package inv.totem

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

/**
 * Handles the totem auto-refill macro logic.
 * Tracks when a totem is used and automatically replaces it from inventory with tick-based delays.
 */
object TotemMacroTracker {
	private val logger = LoggerFactory.getLogger("inv-totem-tracker")
	private const val MAX_CURSOR_WAIT_TICKS = 6
	private const val MAX_VERIFY_RETRIES = 6
	private const val MAX_INTERRUPTION_RECOVERY = 3
	private const val MAX_STASH_RETRIES = 2
	private const val POST_ATTEMPT_WATCH_TICKS = 8
	private const val POST_ATTEMPT_RETRIES = 2

	/**
	 * State machine for the totem replacement sequence.
	 */
	private enum class State {
		IDLE,
		INVENTORY_OPENING,
		SAFETY_BUFFER,
		SCANNING_FOR_TOTEM,
		WAITING_FOR_SWAP_DELAY,
		FIRST_CLICK,
		CLICK_COOLDOWN,
		SECOND_CLICK,
		PREPARE_TARGET_SLOT,
		STASH_BLOCKER,
		POST_TARGET_SYNC,
		VERIFY_OFFHAND,
		CLOSING_INVENTORY,
	}

	private var currentState: State = State.IDLE
	private var tickCounter: Int = 0
	private var totemSlotIndex: Int = -1
	private var sequenceTargetSlotIndex: Int = 45
	private var pendingStashSlotIndex: Int = -1
	private var verifyRetries: Int = 0
	private var interruptionRecoveryCount: Int = 0
	private var lastTargetHadTotem: Boolean? = null
	private var lastItemSlotReplaceMode: Boolean? = null
	private var lastStateForDebug: State = State.IDLE
	private var retryWatchTicks: Int = 0
	private var retryBudget: Int = 0
	private var lastAttemptTargetSlotIndex: Int = 45

	init {
		logger.info("TotemMacroTracker initialized")
	}

	/**
	 * Called every client tick. Manages the state machine for totem replacement.
	 */
	fun onClientTick() {
		if (!ConfigManager.isEnabled()) {
			return
		}

		if (ConfigManager.isDebugModeEnabled() && currentState != lastStateForDebug) {
			logger.info("[debug] State transition: $lastStateForDebug -> $currentState")
			lastStateForDebug = currentState
		}

		val client = Minecraft.getInstance()
		val player = client.player ?: return
		val itemSlotReplaceMode = ConfigManager.isItemSlotReplaceEnabled()
		val dynamicTargetSlot = getTargetSlotIndex()
		val currentTargetHasTotem = doesTargetContainTotem(player, dynamicTargetSlot)

		// If target mode changed at runtime, resync detection baseline to avoid false pop events.
		if (lastItemSlotReplaceMode != itemSlotReplaceMode) {
			lastTargetHadTotem = currentTargetHasTotem
			lastItemSlotReplaceMode = itemSlotReplaceMode
		}

		// Detect totem use: target slot/offhand was totem and now isn't.
		if (lastTargetHadTotem == true && !currentTargetHasTotem && currentState == State.IDLE) {
			if (!isAutoSelectConditionMet(player, dynamicTargetSlot)) {
				debugLog("Auto Select condition blocked sequence start for ${targetSlotLabel(dynamicTargetSlot)}")
			} else {
				logger.info("Totem pop detected! ${targetSlotLabel(dynamicTargetSlot)} no longer contains a totem.")
				startSequenceForTarget(dynamicTargetSlot)
			}
		}

		lastTargetHadTotem = currentTargetHasTotem

		if (currentState == State.IDLE) {
			handlePostAttemptRetry(client, player)
		}

		if (currentState != State.IDLE) {
			suppressHeldInputs(client)
		}

		when (currentState) {
			State.IDLE -> {
				// No action; waiting for pop detection.
			}

			State.INVENTORY_OPENING -> {
				client.setScreen(LockedInventoryScreen(player))
				currentState = State.SAFETY_BUFFER
				tickCounter = 0
				logger.info("Opened inventory screen")
			}

			State.SAFETY_BUFFER -> {
				val requiredTicks = if (ConfigManager.isInstantClickTotemEnabled()) 1 else 2
				if (tickCounter >= requiredTicks) {
					currentState = State.SCANNING_FOR_TOTEM
					tickCounter = 0
					logger.info("Safety buffer complete, scanning for totem")
				}
				tickCounter++
			}

			State.SCANNING_FOR_TOTEM -> {
				if (!player.inventoryMenu.carried.isEmpty) {
					if (tickCounter >= MAX_CURSOR_WAIT_TICKS) {
						recoverFromInterruption(client, "Cursor remained occupied before scan")
						return
					}

					tickCounter++
					debugLog("Waiting for cursor to clear before scanning for totem")
					return
				}

				val inventorySlot = findTotemInInventory(player)
				if (inventorySlot != -1) {
					totemSlotIndex = inventorySlot
					currentState = State.WAITING_FOR_SWAP_DELAY
					tickCounter = 0
					logger.info("Found totem at slot: $totemSlotIndex")
				} else {
					abortSequence(client, "Totem not found in inventory, aborting")
				}
			}

			State.WAITING_FOR_SWAP_DELAY -> {
				// Item-slot mode uses the same global delay path as offhand mode.
				val delayMs = ConfigManager.getSwapDelayMs()
				val delayTicks = if (ConfigManager.isInstantClickTotemEnabled()) 1 else maxOf(1, (delayMs + 25) / 50)
				if (tickCounter >= delayTicks) {
					currentState = State.FIRST_CLICK
					tickCounter = 0
					logger.info("Swap delay elapsed ($delayMs ms), performing first click")
				}
				tickCounter++
			}

			State.FIRST_CLICK -> {
				if (totemSlotIndex == -1) {
					abortSequence(client, "Totem slot index became invalid")
					return
				}

				if (!player.inventoryMenu.carried.isEmpty) {
					if (player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
						currentState = State.SECOND_CLICK
						tickCounter = 0
						debugLog("Cursor already carrying a totem, skipping to target click")
						return
					}

					if (tickCounter >= MAX_CURSOR_WAIT_TICKS) {
						recoverFromInterruption(client, "Cursor stayed occupied before first click")
						return
					}

					tickCounter++
					debugLog("Waiting for cursor to clear before first click")
					return
				}

				performClick(client, totemSlotIndex)
				currentState = State.CLICK_COOLDOWN
				tickCounter = 0
				debugLog("Clicked totem at slot $totemSlotIndex")
			}

			State.CLICK_COOLDOWN -> {
				if (tickCounter >= 1) {
					currentState = State.SECOND_CLICK
					tickCounter = 0
				}
				tickCounter++
			}

			State.SECOND_CLICK -> {
				if (player.inventoryMenu.carried.isEmpty) {
					if (tickCounter >= MAX_CURSOR_WAIT_TICKS) {
						recoverFromInterruption(client, "Expected to carry totem before target click, but cursor stayed empty")
						return
					}

					tickCounter++
					debugLog("Waiting for totem to appear on cursor before target click")
					return
				}

				if (!player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
					recoverFromInterruption(client, "Cursor item changed before target click (possible pickup interference)")
					return
				}

				currentState = State.PREPARE_TARGET_SLOT
				tickCounter = 0
			}

			State.PREPARE_TARGET_SLOT -> {
				if (!player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
					recoverFromInterruption(client, "Lost carried totem before target placement")
					return
				}

				val targetStack = getTargetStack(player, sequenceTargetSlotIndex)
				val targetBlocked = !targetStack.isEmpty && !targetStack.`is`(Items.TOTEM_OF_UNDYING)

				if (targetBlocked) {
					// Swap blocker out first so totem can be placed, then stash blocker safely.
					performClick(client, sequenceTargetSlotIndex)

					if (player.inventoryMenu.carried.isEmpty || player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
						recoverFromInterruption(client, "Target blocker swap did not produce expected carried blocker item")
						return
					}

					pendingStashSlotIndex = findStashSlot(player, sequenceTargetSlotIndex)
					if (pendingStashSlotIndex == -1) {
						recoverFromInterruption(client, "No empty slot available to stash blocker item")
						return
					}

					currentState = State.STASH_BLOCKER
					tickCounter = 0
					debugLog("Target occupied, stashing blocker from ${targetSlotLabel(sequenceTargetSlotIndex)}")
					return
				}

				performClick(client, sequenceTargetSlotIndex)
				currentState = State.POST_TARGET_SYNC
				tickCounter = 0
				verifyRetries = 0
				debugLog("Clicked ${targetSlotLabel(sequenceTargetSlotIndex)}")
			}

			State.STASH_BLOCKER -> {
				if (player.inventoryMenu.carried.isEmpty) {
					currentState = State.POST_TARGET_SYNC
					tickCounter = 0
					verifyRetries = 0
					return
				}

				if (player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
					recoverFromInterruption(client, "Unexpected totem returned to cursor while stashing blocker")
					return
				}

				val stashSlot = if (pendingStashSlotIndex != -1) pendingStashSlotIndex else findStashSlot(player, sequenceTargetSlotIndex)
				if (stashSlot == -1) {
					recoverFromInterruption(client, "Unable to find stash slot for blocker item")
					return
				}

				performClick(client, stashSlot)
				if (!player.inventoryMenu.carried.isEmpty) {
					if (tickCounter >= MAX_STASH_RETRIES) {
						recoverFromInterruption(client, "Failed to stash blocker item after retries")
						return
					}

					pendingStashSlotIndex = findStashSlot(player, sequenceTargetSlotIndex)
					tickCounter++
					debugLog("Retrying blocker stash")
					return
				}

				pendingStashSlotIndex = -1
				currentState = State.POST_TARGET_SYNC
				tickCounter = 0
				verifyRetries = 0
			}

			State.POST_TARGET_SYNC -> {
				// Give server extra ticks for hotbar target updates to reduce ghost cursor desync.
				val requiredTicks = if (sequenceTargetSlotIndex in 0..8) 2 else 1
				if (tickCounter >= requiredTicks) {
					currentState = State.VERIFY_OFFHAND
					tickCounter = 0
				} else {
					tickCounter++
				}
			}

			State.VERIFY_OFFHAND -> {
				val targetHasTotem = doesTargetContainTotem(player, sequenceTargetSlotIndex)
				val carried = player.inventoryMenu.carried

				if (targetHasTotem && carried.isEmpty) {
					currentState = State.CLOSING_INVENTORY
					tickCounter = 0
					return
				}

				if (!carried.isEmpty && !carried.`is`(Items.TOTEM_OF_UNDYING)) {
					recoverFromInterruption(client, "Cursor changed to non-totem during verify (pickup interference)")
					return
				}

				if (!carried.isEmpty && carried.`is`(Items.TOTEM_OF_UNDYING)) {
					performClick(client, sequenceTargetSlotIndex)
					debugLog("Retry click on ${targetSlotLabel(sequenceTargetSlotIndex)} while carrying totem")
				}

				if (verifyRetries >= MAX_VERIFY_RETRIES) {
					recoverFromInterruption(client, "Failed to verify totem in ${targetSlotLabel(sequenceTargetSlotIndex)}")
					return
				}

				verifyRetries++
				tickCounter++
			}

			State.CLOSING_INVENTORY -> {
				if (tickCounter >= 1) {
					schedulePostAttemptWatch(sequenceTargetSlotIndex)
					client.setScreen(null)
					currentState = State.IDLE
					logger.info("Inventory closed, totem replacement complete")
				} else {
					tickCounter++
				}
			}
		}
	}

	private fun startSequenceForTarget(targetSlotIndex: Int) {
		currentState = State.INVENTORY_OPENING
		tickCounter = 0
		totemSlotIndex = -1
		pendingStashSlotIndex = -1
		verifyRetries = 0
		interruptionRecoveryCount = 0
		sequenceTargetSlotIndex = targetSlotIndex
		lastAttemptTargetSlotIndex = targetSlotIndex
	}

	private fun handlePostAttemptRetry(client: Minecraft, player: Player) {
		if (retryWatchTicks <= 0 || retryBudget <= 0) {
			return
		}

		retryWatchTicks--
		if (doesTargetContainTotem(player, lastAttemptTargetSlotIndex)) {
			return
		}

		if (!hasTotemInInventory(player)) {
			return
		}

		if (!isAutoSelectConditionMet(player, lastAttemptTargetSlotIndex)) {
			return
		}

		retryBudget--
		logger.warn("Post-attempt check: target ${targetSlotLabel(lastAttemptTargetSlotIndex)} still missing totem, retrying swap")
		startSequenceForTarget(lastAttemptTargetSlotIndex)
		suppressHeldInputs(client)
	}

	private fun schedulePostAttemptWatch(targetSlotIndex: Int) {
		lastAttemptTargetSlotIndex = targetSlotIndex
		retryWatchTicks = POST_ATTEMPT_WATCH_TICKS
		retryBudget = POST_ATTEMPT_RETRIES
	}

	private fun hasTotemInInventory(player: Player): Boolean {
		for (i in 0..35) {
			if (player.inventory.getItem(i).`is`(Items.TOTEM_OF_UNDYING)) {
				return true
			}
		}
		return false
	}

	private fun recoverFromInterruption(client: Minecraft, reason: String) {
		val player = client.player
		if (interruptionRecoveryCount < MAX_INTERRUPTION_RECOVERY) {
			interruptionRecoveryCount++
			if (player != null) {
				normalizeCursor(client, player)
			}
			logger.warn("$reason. Retrying sequence ($interruptionRecoveryCount/$MAX_INTERRUPTION_RECOVERY)")
			currentState = State.SCANNING_FOR_TOTEM
			tickCounter = 0
			totemSlotIndex = -1
			pendingStashSlotIndex = -1
			verifyRetries = 0
			return
		}

		abortSequence(client, "$reason. Recovery attempts exhausted")
	}

	/**
	 * Scans the player's main inventory (slots 0-35) for a Totem of Undying.
	 * Returns the slot index (0-35) if found, or -1 if not found.
	 */
	private fun findTotemInInventory(player: Player): Int {
		for (i in 0..35) {
			if (player.inventory.getItem(i).`is`(Items.TOTEM_OF_UNDYING)) {
				return i
			}
		}
		return -1
	}

	/**
	 * Performs a click on a specific slot in the player inventory.
	 * This is headless and not tied to physical mouse coordinates.
	 */
	private fun performClick(client: Minecraft, slotIndex: Int) {
		try {
			val player = client.player ?: return
			val gameMode = client.gameMode ?: return
			suppressHeldInputs(client)

			gameMode.handleInventoryMouseClick(
				player.inventoryMenu.containerId,
				toMenuSlotIndex(slotIndex),
				0,
				ClickType.PICKUP,
				player
			)
		} catch (e: Exception) {
			logger.error("Error during slot click", e)
		}
	}

	private fun toMenuSlotIndex(slotIndex: Int): Int {
		return when (slotIndex) {
			in 0..8 -> slotIndex + 36
			else -> slotIndex
		}
	}

	private fun getTargetSlotIndex(): Int {
		return if (ConfigManager.isItemSlotReplaceEnabled()) {
			ConfigManager.getItemSlotReplaceHotbarSlot() - 1
		} else {
			45
		}
	}

	private fun isAutoSelectConditionMet(player: Player, targetSlotIndex: Int): Boolean {
		if (!ConfigManager.isItemSlotReplaceEnabled()) {
			return true
		}

		if (!ConfigManager.isAutoSelectItemSlotEnabled()) {
			return true
		}

		val selectedStack = player.mainHandItem
		val targetStack = player.inventory.getItem(targetSlotIndex)
		return selectedStack === targetStack
	}

	private fun doesTargetContainTotem(player: Player, targetSlotIndex: Int): Boolean {
		return getTargetStack(player, targetSlotIndex).`is`(Items.TOTEM_OF_UNDYING)
	}

	private fun getTargetStack(player: Player, targetSlotIndex: Int) =
		if (targetSlotIndex == 45) player.offhandItem else player.inventory.getItem(targetSlotIndex)

	private fun targetSlotLabel(targetSlotIndex: Int): String {
		return if (targetSlotIndex == 45) "offhand" else "hotbar slot ${targetSlotIndex + 1}"
	}

	private fun suppressHeldInputs(client: Minecraft) {
		KeyMapping.releaseAll()
		client.options.keyMappings.forEach { it.setDown(false) }
	}

	private fun abortSequence(client: Minecraft, message: String) {
		val player = client.player
		if (player != null) {
			normalizeCursor(client, player)
			schedulePostAttemptWatch(sequenceTargetSlotIndex)
		}

		client.setScreen(null)
		currentState = State.IDLE
		tickCounter = 0
		totemSlotIndex = -1
		pendingStashSlotIndex = -1
		verifyRetries = 0
		interruptionRecoveryCount = 0
		logger.warn(message)
	}

	private fun normalizeCursor(client: Minecraft, player: Player) {
		val carried = player.inventoryMenu.carried
		if (carried.isEmpty) {
			return
		}

		val stashSlot = findStashSlot(player, sequenceTargetSlotIndex)
		if (stashSlot != -1) {
			performClick(client, stashSlot)
		}
	}

	private fun findStashSlot(player: Player, excludedSlotIndex: Int): Int {
		for (i in 9..35) {
			if (i != excludedSlotIndex && player.inventory.getItem(i).isEmpty) {
				return i
			}
		}

		for (i in 0..8) {
			if (i != excludedSlotIndex && player.inventory.getItem(i).isEmpty) {
				return i
			}
		}

		return -1
	}

	private fun debugLog(message: String) {
		if (ConfigManager.isDebugModeEnabled()) {
			logger.info("[debug] $message")
		}
	}
}

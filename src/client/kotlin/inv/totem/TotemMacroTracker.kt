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
		POST_TARGET_SYNC,
		VERIFY_OFFHAND,
		CLOSING_INVENTORY,
	}

	private var currentState: State = State.IDLE
	private var tickCounter: Int = 0
	private var totemSlotIndex: Int = -1
	private var sequenceTargetSlotIndex: Int = 45
	private var verifyRetries: Int = 0
	private var interruptionRecoveryCount: Int = 0
	private var lastTargetHadTotem: Boolean? = null
	private var lastItemSlotReplaceMode: Boolean? = null
	private var lastStateForDebug: State = State.IDLE

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
		if (lastTargetHadTotem == true && !currentTargetHasTotem) {
			val targetName = targetSlotLabel(dynamicTargetSlot)
			logger.info("Totem pop detected! $targetName no longer contains a totem.")
			if (currentState == State.IDLE) {
				if (!isAutoSelectConditionMet(player, dynamicTargetSlot)) {
					debugLog("Auto Select condition blocked sequence start for ${targetSlotLabel(dynamicTargetSlot)}")
				} else {
					currentState = State.INVENTORY_OPENING
					tickCounter = 0
					totemSlotIndex = -1
					verifyRetries = 0
					interruptionRecoveryCount = 0
					sequenceTargetSlotIndex = dynamicTargetSlot
				}
			}
		}

		lastTargetHadTotem = currentTargetHasTotem

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
				// Item-slot mode respects the same global delay as offhand mode.
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

				performClick(client, sequenceTargetSlotIndex)
				currentState = State.POST_TARGET_SYNC
				tickCounter = 0
				verifyRetries = 0
				debugLog("Clicked ${targetSlotLabel(sequenceTargetSlotIndex)}")
			}

			State.POST_TARGET_SYNC -> {
				// Give server one extra tick for offhand, two for hotbar to reduce client ghosting.
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
					client.setScreen(null)
					currentState = State.IDLE
					logger.info("Inventory closed, totem replacement complete")
				} else {
					tickCounter++
				}
			}
		}
	}

	private fun recoverFromInterruption(client: Minecraft, reason: String) {
		if (interruptionRecoveryCount < MAX_INTERRUPTION_RECOVERY) {
			interruptionRecoveryCount++
			logger.warn("$reason. Retrying sequence ($interruptionRecoveryCount/$MAX_INTERRUPTION_RECOVERY)")
			currentState = State.SCANNING_FOR_TOTEM
			tickCounter = 0
			totemSlotIndex = -1
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
		val inventory = player.inventory
		for (i in 0..35) {
			val itemStack = inventory.getItem(i)
			if (itemStack.`is`(Items.TOTEM_OF_UNDYING)) {
				return i
			}
		}
		return -1
	}

	/**
	 * Performs a click on a specific slot in the player inventory.
	 */
	private fun performClick(client: Minecraft, slotIndex: Int) {
		try {
			val player = client.player ?: return
			val gameMode = client.gameMode ?: return

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
		return if (targetSlotIndex == 45) {
			player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)
		} else {
			player.inventory.getItem(targetSlotIndex).`is`(Items.TOTEM_OF_UNDYING)
		}
	}

	private fun targetSlotLabel(targetSlotIndex: Int): String {
		return if (targetSlotIndex == 45) {
			"offhand"
		} else {
			"hotbar slot ${targetSlotIndex + 1}"
		}
	}

	private fun suppressHeldInputs(client: Minecraft) {
		KeyMapping.releaseAll()
		client.options.keyMappings.forEach { it.setDown(false) }
	}

	private fun abortSequence(client: Minecraft, message: String) {
		val player = client.player
		if (player != null && !player.inventoryMenu.carried.isEmpty && player.inventoryMenu.carried.`is`(Items.TOTEM_OF_UNDYING)) {
			// Best-effort cursor cleanup to avoid ghost cursor state after a failed sequence.
			val fallbackSlot = findTotemFallbackSlot(player)
			if (fallbackSlot != -1) {
				performClick(client, fallbackSlot)
			}
		}

		client.setScreen(null)
		currentState = State.IDLE
		tickCounter = 0
		totemSlotIndex = -1
		verifyRetries = 0
		interruptionRecoveryCount = 0
		logger.warn(message)
	}

	private fun findTotemFallbackSlot(player: Player): Int {
		for (i in 0..35) {
			if (player.inventory.getItem(i).isEmpty) {
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

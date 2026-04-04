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
 *
 * Bug-fix changelog (v1.0.6):
 *   FIX-1  Kit loading cursor lock — require recent damage to trigger (hurtTime / low HP check)
 *   FIX-2  Crystal combo instant death — pendingRepop flag re-triggers immediately after sequence
 *   FIX-3  Slot replace mode — target slot is now computed from config, verify checks correct slot
 */
object TotemMacroTracker {
	private val logger = LoggerFactory.getLogger("inv-totem-tracker")
	private const val MIN_ACTION_GAP_TICKS = 2L
	private const val VERIFY_DELAY_TICKS = 3L
	private const val MAX_VERIFY_RETRIES = 3
	private const val STATE_TIMEOUT_TICKS = 40L   // FIX-1: abort if stuck > 2 seconds

	/**
	 * State machine for the totem replacement sequence.
	 */
	private enum class State {
		IDLE,                       // Waiting for totem pop
		INVENTORY_OPENING,          // Opening the inventory screen
		SAFETY_BUFFER,              // Waiting 75ms for server sync
		SCANNING_FOR_TOTEM,         // Looking for totem in inventory
		WAITING_FOR_SWAP_DELAY,     // Waiting for configured delay before clicking
		FIRST_CLICK,                // Clicking the totem in inventory
		CLICK_COOLDOWN,             // Brief delay between first and second click
		SECOND_CLICK,               // Clicking the target slot (offhand or hotbar)
		CLOSING_INVENTORY,          // Closing the inventory screen
	}

	private var currentState: State = State.IDLE
	private var tickCounter: Int = 0
	private var totemSlotIndex: Int = -1
	private var lastOffhandHadTotem: Boolean? = null
	private var macroTick: Long = 0L
	private var lastActionTick: Long = -9999L
	private var deferUntilTick: Long = 0L

	// FIX-1: Track state-entry tick for timeout detection
	private var stateEntryTick: Long = 0L

	// FIX-2: If a totem pop fires while the state machine is already active, queue a re-trigger
	private var pendingRepop: Boolean = false

	private data class VerifyTask(
		var targetSlot: Int,
		var checkAtTick: Long,
		var retriesLeft: Int,
	)

	private var verifyTask: VerifyTask? = null
	
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
		
		val client = Minecraft.getInstance()
		val player = client.player ?: return
		
		// END_CLIENT_TICK is the safest phase: vanilla pickup updates have already been applied.
		macroTick++
		processVerifyQueue(player)

		if (macroTick < deferUntilTick) {
			return
		}
		
		// Detect when the offhand transitions away from a totem.
		val currentOffhandHasTotem = player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)
		
		// Detect totem pop: offhand was totem, now it's not
		if (lastOffhandHadTotem == true && !currentOffhandHasTotem) {
			// ────────────────────────────────────────────────────────────────
			// FIX-1: Only trigger if the player actually took damage.
			//   hurtTime > 0  → player was hit this/last tick (red-flash timer)
			//   health <= 4   → post-totem-pop HP (totem sets you to 1 HP + regen)
			//   Either condition confirms a real totem pop, NOT a kit load.
			// ────────────────────────────────────────────────────────────────
			val isDamaged = player.hurtTime > 0 || player.health <= 4.0f

			if (currentState == State.IDLE) {
				if (isDamaged) {
					logger.info("Totem pop detected (hurtTime=${player.hurtTime}, hp=${player.health}). Starting replacement.")
					currentState = State.INVENTORY_OPENING
					tickCounter = 0
					totemSlotIndex = -1
					stateEntryTick = macroTick
				} else {
					logger.info("Offhand totem removed without damage (hurtTime=0, hp=${player.health}). Kit load? Skipping.")
				}
			} else if (isDamaged) {
				// ────────────────────────────────────────────────────────────
				// FIX-2: Crystal combo — a second pop happened while we're
				//   still replacing the first one. Mark for immediate re-trigger.
				// ────────────────────────────────────────────────────────────
				pendingRepop = true
				logger.warn("Re-pop during active sequence at tick $macroTick. Queued for retry after completion.")
			}
		}
		
		lastOffhandHadTotem = currentOffhandHasTotem

		if (currentState != State.IDLE) {
			suppressHeldInputs(client)

			// FIX-1: Timeout — abort if stuck in any state for too long (e.g., screen failed to open)
			if (macroTick - stateEntryTick > STATE_TIMEOUT_TICKS) {
				clearCursorSafely(client, player)
				abortSequence(client, "State machine timeout (${STATE_TIMEOUT_TICKS} ticks in $currentState). Force-aborting.")
				return
			}
		}
		
		// State machine execution
		when (currentState) {
			State.IDLE -> {
				// Do nothing, wait for totem pop
			}
			
			State.INVENTORY_OPENING -> {
				// FIX-1: Clear any stuck cursor item before opening (prevents ghost items from kit loads)
				clearCursorSafely(client, player)

				// Open the inventory screen
				client.setScreen(LockedInventoryScreen(player))
				if (ConfigManager.isInstantClickTotemEnabled()) {
					currentState = State.SCANNING_FOR_TOTEM
					logger.info("Opened inventory screen and skipping safety buffer for instant mode")
				} else {
					currentState = State.SAFETY_BUFFER
					tickCounter = 0
					logger.info("Opened inventory screen")
				}
				stateEntryTick = macroTick
			}
			
			State.SAFETY_BUFFER -> {
				// Wait 75ms (approximately 1-2 ticks at 20 TPS) for server to register screen opening
				// At 20 ticks/sec, each tick is ~50ms, so 2 ticks = ~100ms > 75ms
				if (tickCounter >= 2) {
					currentState = State.SCANNING_FOR_TOTEM
					logger.info("Safety buffer complete, scanning for totem")
				}
				tickCounter++
			}
			
			State.SCANNING_FOR_TOTEM -> {
				if (!ensureReadyBeforeSwap(client, player, expectTotemOnCursor = false)) {
					scheduleNextTick("cursor not clean before scan", player)
					return
				}

				// Scan inventory for totem of undying
				val inventorySlot = findTotemInInventory(player)
				if (inventorySlot != -1) {
					totemSlotIndex = inventorySlot
					logger.info("Found totem at slot: $totemSlotIndex")

					if (ConfigManager.isInstantClickTotemEnabled()) {
						currentState = State.FIRST_CLICK
						tickCounter = 0
						logger.info("Instant Click Totem is enabled, starting headless swap path")
					} else {
						currentState = State.WAITING_FOR_SWAP_DELAY
						tickCounter = 0
					}
				} else {
					// Totem not found, close inventory and abort
					client.setScreen(null)
					currentState = State.IDLE
					logger.warn("Totem not found in inventory, aborting")
				}
			}
			
			State.WAITING_FOR_SWAP_DELAY -> {
				// Wait for the configured swap delay
				val delayMs = ConfigManager.getSwapDelayMs()
				val delayTicks = maxOf(1, (delayMs + 25) / 50)  // Restore original timing: 0ms still waits one tick
				if (tickCounter >= delayTicks) {
					currentState = State.FIRST_CLICK
					tickCounter = 0
					logger.info("Swap delay elapsed ($delayMs ms), performing first click")
				}
				tickCounter++
			}
			
			State.FIRST_CLICK -> {
				// Click the totem slot in inventory
				if (totemSlotIndex != -1) {
					if (!ensureReadyBeforeSwap(client, player, expectTotemOnCursor = false)) {
						scheduleNextTick("cursor became occupied before first click", player)
						currentState = State.SCANNING_FOR_TOTEM
						return
					}

					val syncId = currentSyncId(player)
					if (!performClick(client, totemSlotIndex, syncId, "pickup-totem")) {
						scheduleNextTick("first click throttled or sync mismatch", player)
						return
					}
					currentState = State.CLICK_COOLDOWN
					tickCounter = 0
					logger.debug("Clicked totem at slot $totemSlotIndex")
				} else {
					// Something went wrong, abort
					client.setScreen(null)
					currentState = State.IDLE
					logger.error("Totem slot index is invalid")
				}
			}
			
			State.CLICK_COOLDOWN -> {
				// Brief delay between first and second click (1 tick = ~50ms)
				if (tickCounter >= 1) {
					currentState = State.SECOND_CLICK
					tickCounter = 0
				}
				tickCounter++
			}
			
			State.SECOND_CLICK -> {
				// ────────────────────────────────────────────────────────────
				// FIX-3: Determine target slot from config.
				//   - Default: offhand (slot 45)
				//   - Slot Replace mode: hotbar slot (0-8, mapped to 36-44 by toMenuSlotIndex)
				// ────────────────────────────────────────────────────────────
				val targetSlot: Int
				if (ConfigManager.isItemSlotReplaceEnabled()) {
					val hotbarIndex = ConfigManager.getItemSlotReplaceHotbarSlot() - 1  // 1-based → 0-based

					// Auto-select gating: only replace if the player is holding the target hotbar slot
					if (ConfigManager.isAutoSelectItemSlotEnabled() && player.inventory.getSelectedSlot() != hotbarIndex) {
						abortSequence(client,
							"Auto-select check failed: player holding slot ${player.inventory.getSelectedSlot()}, target is $hotbarIndex. Aborting.")
						return
					}
					targetSlot = hotbarIndex  // toMenuSlotIndex will map 0-8 → 36-44
					logger.info("Slot Replace mode: targeting hotbar slot ${hotbarIndex + 1} (menu index ${toMenuSlotIndex(hotbarIndex)})")
				} else {
					targetSlot = 45  // Offhand
				}

				if (!ensureReadyBeforeSwap(client, player, expectTotemOnCursor = true)) {
					scheduleNextTick("cursor changed before target click", player)
					currentState = State.SCANNING_FOR_TOTEM
					return
				}

				val syncId = currentSyncId(player)
				if (!performClick(client, targetSlot, syncId, "place-target")) {
					scheduleNextTick("second click throttled or sync mismatch", player)
					return
				}

				verifyTask = VerifyTask(
					targetSlot = targetSlot,
					checkAtTick = macroTick + VERIFY_DELAY_TICKS,
					retriesLeft = MAX_VERIFY_RETRIES,
				)
				logger.info("Scheduled post-swap verification at tick ${macroTick + VERIFY_DELAY_TICKS} (syncId=$syncId, target=$targetSlot)")
				currentState = State.CLOSING_INVENTORY
				logger.debug("Clicked target slot $targetSlot")
			}
			
			State.CLOSING_INVENTORY -> {
				// Close the inventory
				client.setScreen(null)
				currentState = State.IDLE
				logger.info("Inventory closed, totem replacement complete")

				// ────────────────────────────────────────────────────────────
				// FIX-2: Crystal combo re-trigger.
				//   If another totem pop was detected while we were mid-sequence,
				//   immediately restart the replacement instead of staying idle.
				// ────────────────────────────────────────────────────────────
				if (pendingRepop) {
					pendingRepop = false
					logger.info("Crystal combo: re-triggering macro for pending re-pop.")
					currentState = State.INVENTORY_OPENING
					tickCounter = 0
					totemSlotIndex = -1
					stateEntryTick = macroTick
				}
			}
		}
	}
	
	/**
	 * Scans the player's main inventory (slots 0-35) for a Totem of Undying.
	 * Returns the slot index (0-35) if found, or -1 if not found.
	 */
	private fun findTotemInInventory(player: Player): Int {
		val inventory = player.inventory
		// Scan main inventory slots (0-35)
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
	 * Uses the interaction manager to simulate a left-click.
	 */
	private fun performClick(client: Minecraft, slotIndex: Int, expectedSyncId: Int, actionName: String): Boolean {
		try {
			val player = client.player ?: return false
			val gameMode = client.gameMode ?: return false
			val currentSyncId = currentSyncId(player)

			if (currentSyncId != expectedSyncId) {
				logger.warn("Aborting $actionName at tick $macroTick due to syncId mismatch (expected=$expectedSyncId, actual=$currentSyncId)")
				return false
			}

			if (macroTick - lastActionTick < MIN_ACTION_GAP_TICKS) {
				logger.debug("Throttling $actionName at tick $macroTick; lastActionTick=$lastActionTick")
				return false
			}
			
			// Hotbar inventory indices (0-8) map to menu slots 36-44 in the player inventory screen.
			gameMode.handleInventoryMouseClick(
				currentSyncId,
				toMenuSlotIndex(slotIndex),
				0,
				ClickType.PICKUP,
				player
			)
			lastActionTick = macroTick
			return true
		} catch (e: Exception) {
			logger.error("Error during slot click", e)
			return false
		}
	}
	
	/**
	 * Converts inventory indices to the player menu slot indices used by click handling.
	 */
	private fun toMenuSlotIndex(slotIndex: Int): Int {
		return when (slotIndex) {
			in 0..8 -> slotIndex + 36
			else -> slotIndex
		}
	}

	private fun suppressHeldInputs(client: Minecraft) {
		KeyMapping.releaseAll()
		client.options.keyMappings.forEach { it.setDown(false) }
	}

	private fun currentSyncId(player: Player): Int {
		return player.inventoryMenu.containerId
	}

	private fun scheduleNextTick(reason: String, player: Player) {
		deferUntilTick = macroTick + 1
		logger.warn("Deferring swap to next tick=$deferUntilTick (currentTick=$macroTick, syncId=${currentSyncId(player)}): $reason")
	}

	private fun clearCursorSafely(client: Minecraft, player: Player): Boolean {
		if (player.inventoryMenu.carried.isEmpty) {
			return true
		}

		val syncId = currentSyncId(player)
		val emptySlot = findEmptyInventorySlot(player)
		if (emptySlot != -1) {
			performClick(client, emptySlot, syncId, "cursor-stash")
		}

		if (!player.inventoryMenu.carried.isEmpty) {
			// Drop outside the screen as last resort to avoid a stuck ghost cursor state.
			performClick(client, -999, syncId, "cursor-drop")
		}

		return player.inventoryMenu.carried.isEmpty
	}

	private fun ensureReadyBeforeSwap(client: Minecraft, player: Player, expectTotemOnCursor: Boolean): Boolean {
		val carried = player.inventoryMenu.carried
		if (expectTotemOnCursor) {
			if (carried.isEmpty || !carried.`is`(Items.TOTEM_OF_UNDYING)) {
				return false
			}
			return true
		}

		if (carried.isEmpty) {
			return true
		}

		logger.warn("Unexpected cursor stack before swap action at tick $macroTick; attempting cleanup")
		return clearCursorSafely(client, player)
	}

	/**
	 * FIX-3: Verify now checks the correct slot (offhand OR hotbar) depending on config.
	 */
	private fun processVerifyQueue(player: Player) {
		val task = verifyTask ?: return
		if (macroTick < task.checkAtTick) {
			return
		}

		val syncId = currentSyncId(player)

		// Check the slot we actually placed the totem into
		val slotHasTotem = if (task.targetSlot == 45) {
			// Offhand mode
			player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)
		} else {
			// Slot Replace mode — targetSlot is 0-8 (inventory hotbar index)
			player.inventory.getItem(task.targetSlot).`is`(Items.TOTEM_OF_UNDYING)
		}

		if (slotHasTotem) {
			logger.info("Verify success at tick $macroTick (syncId=$syncId, slot=${task.targetSlot})")
			verifyTask = null
			return
		}

		if (task.retriesLeft <= 0) {
			logger.error("Verify failed after retries at tick $macroTick (syncId=$syncId, slot=${task.targetSlot})")
			verifyTask = null
			return
		}

		task.retriesLeft -= 1
		task.checkAtTick = macroTick + VERIFY_DELAY_TICKS
		logger.warn("Verify miss at tick $macroTick (syncId=$syncId, slot=${task.targetSlot}), retrying swap. retriesLeft=${task.retriesLeft}")

		if (currentState == State.IDLE) {
			currentState = State.INVENTORY_OPENING
			tickCounter = 0
			totemSlotIndex = -1
			stateEntryTick = macroTick
		}
	}

	private fun findEmptyInventorySlot(player: Player): Int {
		for (i in 9..35) {
			if (player.inventory.getItem(i).isEmpty) {
				return i
			}
		}
		for (i in 0..8) {
			if (player.inventory.getItem(i).isEmpty) {
				return i
			}
		}
		return -1
	}

	private fun abortSequence(client: Minecraft, message: String) {
		pendingRepop = false           // FIX-2: clear queued re-trigger on abort
		verifyTask = null
		client.setScreen(null)
		currentState = State.IDLE
		tickCounter = 0
		totemSlotIndex = -1
		logger.warn(message)
	}
}

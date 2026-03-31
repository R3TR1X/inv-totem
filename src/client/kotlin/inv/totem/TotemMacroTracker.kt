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
		SECOND_CLICK,               // Clicking the offhand slot
		CLOSING_INVENTORY,          // Closing the inventory screen
	}
	
	private var currentState: State = State.IDLE
	private var tickCounter: Int = 0
	private var totemSlotIndex: Int = -1
	private var lastOffhandHadTotem: Boolean? = null
	
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
		
		// Detect when the offhand transitions away from a totem.
		val currentOffhandHasTotem = player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)
		
		// Detect totem pop: offhand was totem, now it's not
		if (lastOffhandHadTotem == true && !currentOffhandHasTotem) {
			logger.info("Totem pop detected! Offhand no longer contains a totem.")
			if (currentState == State.IDLE) {
				currentState = State.INVENTORY_OPENING
				tickCounter = 0
				totemSlotIndex = -1
			}
		}
		
		lastOffhandHadTotem = currentOffhandHasTotem

		if (currentState != State.IDLE) {
			suppressHeldInputs(client)
		}
		
		// State machine execution
		when (currentState) {
			State.IDLE -> {
				// Do nothing, wait for totem pop
			}
			
			State.INVENTORY_OPENING -> {
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
				if (!player.inventoryMenu.carried.isEmpty) {
					abortSequence(client, "Inventory cursor is already carrying an item, aborting auto-swap for safety")
					return
				}

				// Scan inventory for totem of undying
				val inventorySlot = findTotemInInventory(player)
				if (inventorySlot != -1) {
					totemSlotIndex = inventorySlot
					logger.info("Found totem at slot: $totemSlotIndex")

					if (ConfigManager.isInstantClickTotemEnabled()) {
						performClick(client, totemSlotIndex)
						performClick(client, 45)
						client.setScreen(null)
						currentState = State.IDLE
						tickCounter = 0
						logger.info("Instant Click Totem is enabled, performing full swap immediately")
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
					if (!player.inventoryMenu.carried.isEmpty) {
						abortSequence(client, "Inventory cursor became occupied before the first click, aborting auto-swap")
						return
					}

					performClick(client, totemSlotIndex)
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
				// Click the offhand slot (slot 45)
				if (player.inventoryMenu.carried.isEmpty) {
					abortSequence(client, "Expected to be carrying the totem before the offhand click, aborting auto-swap")
					return
				}

				performClick(client, 45)
				currentState = State.CLOSING_INVENTORY
				logger.debug("Clicked offhand slot 45")
			}
			
			State.CLOSING_INVENTORY -> {
				// Close the inventory
				client.setScreen(null)
				currentState = State.IDLE
				logger.info("Inventory closed, totem replacement complete")
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
	private fun performClick(client: Minecraft, slotIndex: Int) {
		try {
			val player = client.player ?: return
			val gameMode = client.gameMode ?: return
			
			// Hotbar inventory indices (0-8) map to menu slots 36-44 in the player inventory screen.
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

	private fun abortSequence(client: Minecraft, message: String) {
		client.setScreen(null)
		currentState = State.IDLE
		tickCounter = 0
		totemSlotIndex = -1
		logger.warn(message)
	}
}


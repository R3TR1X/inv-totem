package inv.totem

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Player

/**
 * Inventory screen used during auto-refill so player inputs cannot interfere with the scripted clicks.
 */
class LockedInventoryScreen(player: Player) : InventoryScreen(player) {
	private var inputHandlersInstalled = false

	override fun init() {
		super.init()

		if (inputHandlersInstalled) {
			return
		}

		inputHandlersInstalled = true

		ScreenKeyboardEvents.allowKeyPress(this).register { _, _ -> false }
		ScreenMouseEvents.allowMouseClick(this).register { _, _ -> false }
		ScreenMouseEvents.allowMouseRelease(this).register { _, _ -> false }
		ScreenMouseEvents.allowMouseDrag(this).register { _, _, _, _ -> false }
		ScreenMouseEvents.allowMouseScroll(this).register { _, _, _, _, _ -> false }
	}

	override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = true

	override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = true

	override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean = true

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean = true

	override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = true

	override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = true

	override fun charTyped(codePoint: Char, modifiers: Int): Boolean = true

	override fun shouldCloseOnEsc(): Boolean = false
}

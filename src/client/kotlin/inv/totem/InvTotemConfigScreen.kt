package inv.totem

import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

private const val MAX_DELAY_MS = 500

private fun delayMsToSliderValue(delayMs: Int): Double {
	return delayMs.coerceIn(0, MAX_DELAY_MS).toDouble() / MAX_DELAY_MS.toDouble()
}

class InvTotemConfigScreen(private val parentScreen: Screen) : Screen(Component.literal("Inv-Totem Config")) {
	private var selectedDelayMs = ConfigManager.getSwapDelayMs()
	private var instantClickTotemEnabled = ConfigManager.isInstantClickTotemEnabled()
	private var debugModeEnabled = ConfigManager.isDebugModeEnabled()

	override fun init() {
		super.init()

		val sliderWidth = 220
		val sliderX = (width - sliderWidth) / 2
		val toggleY = height / 2 - 24
		val debugToggleY = toggleY + 24
		val sliderY = debugToggleY + 38
		val titleText = Component.literal("INV-TOTEM CONFIG")
		val titleWidget = StringWidget(titleText, font).setColor(0xFFFFFF)
		titleWidget.setX((width - titleWidget.width) / 2)
		titleWidget.setY(toggleY - 14)

		addRenderableWidget(titleWidget)

		addRenderableWidget(
			Button.builder(instantClickButtonLabel()) { button ->
				instantClickTotemEnabled = !instantClickTotemEnabled
				button.setMessage(instantClickButtonLabel())
			}.bounds(sliderX, toggleY, sliderWidth, 20).build()
		)

		addRenderableWidget(
			Button.builder(debugModeButtonLabel()) { button ->
				debugModeEnabled = !debugModeEnabled
				button.setMessage(debugModeButtonLabel())
			}.bounds(sliderX, debugToggleY, sliderWidth, 20).build()
		)

		addRenderableWidget(
			DelaySliderButton(sliderX, sliderY, sliderWidth, selectedDelayMs) { delayMs ->
				selectedDelayMs = delayMs
			}
		)

		addRenderableWidget(
			Button.builder(Component.literal("Done")) {
				saveAndClose()
			}.bounds((width - 200) / 2, sliderY + 44, 200, 20).build()
		)
	}

	override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
		renderTransparentBackground(guiGraphics)
		super.render(guiGraphics, mouseX, mouseY, partialTick)
	}

	override fun onClose() {
		saveAndClose()
	}

	private fun saveAndClose() {
		ConfigManager.setSwapDelayMs(selectedDelayMs)
		ConfigManager.setInstantClickTotemEnabled(instantClickTotemEnabled)
		ConfigManager.setDebugModeEnabled(debugModeEnabled)
		minecraft?.setScreen(parentScreen)
	}

	private fun instantClickButtonLabel(): Component {
		val stateText = if (instantClickTotemEnabled) "Enabled" else "Disabled"
		return Component.literal("Instant Click Totem: $stateText")
	}

	private fun debugModeButtonLabel(): Component {
		val stateText = if (debugModeEnabled) "Enabled" else "Disabled"
		return Component.literal("Debug Mode: $stateText")
	}
}

private class DelaySliderButton(
	x: Int,
	y: Int,
	width: Int,
	initialDelayMs: Int,
	private val onValueChanged: (Int) -> Unit,
) : AbstractSliderButton(
	x,
	y,
	width,
	Button.DEFAULT_HEIGHT,
	Component.literal(""),
	delayMsToSliderValue(initialDelayMs),
) {
	private var delayMs = initialDelayMs.coerceIn(0, MAX_DELAY_MS)

	init {
		updateMessage()
	}

	override fun updateMessage() {
		setMessage(Component.literal("Delay: ${currentDelayMs()}ms"))
	}

	override fun applyValue() {
		delayMs = currentDelayMs()
		onValueChanged(delayMs)
		updateMessage()
	}

	private fun currentDelayMs(): Int {
		return (value * MAX_DELAY_MS).roundToInt().coerceIn(0, MAX_DELAY_MS)
	}
}

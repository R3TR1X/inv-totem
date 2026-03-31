package inv.totem

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object InvtotemClient : ClientModInitializer {
	private val logger = LoggerFactory.getLogger("inv-totem-client")

	override fun onInitializeClient() {
		logger.info("Inv-Totem client initialized!")
		
		// Load configuration
		ConfigManager.loadConfig()
		
		// Register event listeners
		ClientTickEvents.END_CLIENT_TICK.register { TotemMacroTracker.onClientTick() }
		registerPauseMenuButton()
	}

	private fun registerPauseMenuButton() {
		ScreenEvents.AFTER_INIT.register { client, screen, _, scaledHeight ->
			if (screen !is PauseScreen) {
				return@register
			}

			Screens.getButtons(screen).add(
				Button.builder(Component.literal("Inv-Totem")) {
					client.setScreen(InvTotemConfigScreen(screen))
				}.bounds(4, scaledHeight - 24, 120, 20).build()
			)
		}
	}
}

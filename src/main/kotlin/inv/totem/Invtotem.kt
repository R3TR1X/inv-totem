package inv.totem

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Invtotem : ModInitializer {
    private val logger = LoggerFactory.getLogger("inv-totem")

	override fun onInitialize() {
		logger.info("Inv-Totem mod initialized!")
	}
}
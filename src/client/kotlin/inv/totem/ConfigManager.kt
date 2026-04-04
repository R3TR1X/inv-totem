package inv.totem

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.slf4j.LoggerFactory

/**
 * Manages configuration for the Inv-Totem mod.
 * Configuration is stored in a JSON file at .minecraft/config/inv-totem-config.json
 */
object ConfigManager {
	private val logger = LoggerFactory.getLogger("inv-totem-config")
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
	private val configDir = Paths.get(System.getProperty("user.home"), ".minecraft", "config")
	private val configFile = configDir.resolve("inv-totem-config.json").toFile()
	
	private var config: TotemConfig = TotemConfig()
	
	data class TotemConfig(
		var swapDelayMs: Int = 100,
		var instantClickTotem: Boolean = false,
		var itemSlotReplace: Boolean = false,
		var itemSlotReplaceHotbarSlot: Int = 1,
		var autoSelectItemSlot: Boolean = false,
		var enabled: Boolean = true,
		var debugMode: Boolean = false,

		// Inventory totem settings
		var inventoryTotemEnabled: Boolean = true,
		var inventoryTotemMode: String = "AUTO",
		var inventoryTotemPriority: String = "NORMAL",
		var inventoryTotemEmergencyOnly: Boolean = false,
	)
	
	fun loadConfig() {
		try {
			if (!configFile.exists()) {
				logger.info("Config file not found, creating default config at: ${configFile.absolutePath}")
				createConfigDir()
				saveConfig()
			} else {
				val json = configFile.readText()
				config = gson.fromJson(json, TotemConfig::class.java) ?: TotemConfig()
				config.swapDelayMs = config.swapDelayMs.coerceIn(0, 500)
				config.itemSlotReplaceHotbarSlot = config.itemSlotReplaceHotbarSlot.coerceIn(1, 9)
				config.inventoryTotemMode = config.inventoryTotemMode.uppercase()
					.takeIf { it in listOf("AUTO", "MANUAL", "DISABLED") } ?: "AUTO"
				config.inventoryTotemPriority = config.inventoryTotemPriority.uppercase()
					.takeIf { it in listOf("HIGH", "NORMAL", "LOW") } ?: "NORMAL"
				logger.info(
					"Loaded config: enabled=${config.enabled}, swapDelayMs=${config.swapDelayMs}, " +
					"instantClickTotem=${config.instantClickTotem}, itemSlotReplace=${config.itemSlotReplace}, " +
					"inventoryTotemEnabled=${config.inventoryTotemEnabled}, inventoryTotemMode=${config.inventoryTotemMode}, " +
					"inventoryTotemPriority=${config.inventoryTotemPriority}, inventoryTotemEmergencyOnly=${config.inventoryTotemEmergencyOnly}"
				)
			}
		} catch (e: Exception) {
			logger.warn("Failed to load config file, using defaults", e)
			config = TotemConfig()
		}
	}
	
	fun saveConfig() {
		try {
			createConfigDir()
			val json = gson.toJson(config)
			configFile.writeText(json)
			logger.info("Config saved successfully")
		} catch (e: Exception) {
			logger.error("Failed to save config file", e)
		}
	}
	
	private fun createConfigDir() {
		Files.createDirectories(configDir)
	}
	
	fun getSwapDelayMs(): Int = config.swapDelayMs
	fun setSwapDelayMs(delayMs: Int) {
		config.swapDelayMs = delayMs.coerceIn(0, 500)
		saveConfig()
	}

	fun isInstantClickTotemEnabled(): Boolean = config.instantClickTotem
	fun setInstantClickTotemEnabled(enabled: Boolean) {
		config.instantClickTotem = enabled
		saveConfig()
	}

	fun isItemSlotReplaceEnabled(): Boolean = config.itemSlotReplace
	fun setItemSlotReplaceEnabled(enabled: Boolean) {
		config.itemSlotReplace = enabled
		saveConfig()
	}

	fun getItemSlotReplaceHotbarSlot(): Int = config.itemSlotReplaceHotbarSlot
	fun setItemSlotReplaceHotbarSlot(slot: Int) {
		config.itemSlotReplaceHotbarSlot = slot.coerceIn(1, 9)
		saveConfig()
	}

	fun isAutoSelectItemSlotEnabled(): Boolean = config.autoSelectItemSlot
	fun setAutoSelectItemSlotEnabled(enabled: Boolean) {
		config.autoSelectItemSlot = enabled
		saveConfig()
	}
	
	fun isEnabled(): Boolean = config.enabled
	fun setEnabled(enabled: Boolean) {
		config.enabled = enabled
		saveConfig()
	}

	fun isDebugModeEnabled(): Boolean = config.debugMode
	fun setDebugModeEnabled(enabled: Boolean) {
		config.debugMode = enabled
		saveConfig()
	}

	fun isInventoryTotemEnabled(): Boolean = config.inventoryTotemEnabled
	fun setInventoryTotemEnabled(enabled: Boolean) {
		config.inventoryTotemEnabled = enabled
		saveConfig()
	}

	fun getInventoryTotemMode(): String = config.inventoryTotemMode
	fun setInventoryTotemMode(mode: String) {
		config.inventoryTotemMode = mode.uppercase()
			.takeIf { it in listOf("AUTO", "MANUAL", "DISABLED") } ?: "AUTO"
		saveConfig()
	}

	fun getInventoryTotemPriority(): String = config.inventoryTotemPriority
	fun setInventoryTotemPriority(priority: String) {
		config.inventoryTotemPriority = priority.uppercase()
			.takeIf { it in listOf("HIGH", "NORMAL", "LOW") } ?: "NORMAL"
		saveConfig()
	}

	fun isInventoryTotemEmergencyOnly(): Boolean = config.inventoryTotemEmergencyOnly
	fun setInventoryTotemEmergencyOnly(enabled: Boolean) {
		config.inventoryTotemEmergencyOnly = enabled
		saveConfig()
	}

	fun resetToDefaults() {
		config = TotemConfig()
		saveConfig()
	}
}

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
		/**
		 * Delay in milliseconds before clicking the totem slot.
		 * Adjust this value to balance between anti-cheat evasion and responsiveness.
		 * Allowed range: 0-500ms
		 */
		var swapDelayMs: Int = 100,

		/**
		 * When enabled, clicks the replacement totem immediately after it is found.
		 */
		var instantClickTotem: Boolean = false,
		
		/**
		 * Enable/disable the auto-totem feature.
		 */
		var enabled: Boolean = true,

		/**
		 * Enable verbose state-machine logs for troubleshooting.
		 */
		var debugMode: Boolean = false
	)
	
	/**
	 * Load configuration from file, or create default if it doesn't exist.
	 */
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
				logger.info(
					"Loaded config: swapDelayMs=${config.swapDelayMs}, instantClickTotem=${config.instantClickTotem}, enabled=${config.enabled}, debugMode=${config.debugMode}"
				)
			}
		} catch (e: Exception) {
			logger.warn("Failed to load config file, using defaults", e)
			config = TotemConfig()
		}
	}
	
	/**
	 * Save current configuration to file.
	 */
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
	
	/**
	 * Get the current swap delay in milliseconds.
	 */
	fun getSwapDelayMs(): Int = config.swapDelayMs
	
	/**
	 * Set the swap delay in milliseconds.
	 */
	fun setSwapDelayMs(delayMs: Int) {
		config.swapDelayMs = delayMs.coerceIn(0, 500)
		saveConfig()
	}

	/**
	 * Check if instant-click mode is enabled.
	 */
	fun isInstantClickTotemEnabled(): Boolean = config.instantClickTotem

	/**
	 * Enable or disable instant-click mode.
	 */
	fun setInstantClickTotemEnabled(enabled: Boolean) {
		config.instantClickTotem = enabled
		saveConfig()
	}
	
	/**
	 * Check if the auto-totem feature is enabled.
	 */
	fun isEnabled(): Boolean = config.enabled
	
	/**
	 * Enable or disable the auto-totem feature.
	 */
	fun setEnabled(enabled: Boolean) {
		config.enabled = enabled
		saveConfig()
	}

	/**
	 * Check if debug mode is enabled.
	 */
	fun isDebugModeEnabled(): Boolean = config.debugMode

	/**
	 * Enable or disable debug mode.
	 */
	fun setDebugModeEnabled(enabled: Boolean) {
		config.debugMode = enabled
		saveConfig()
	}
}

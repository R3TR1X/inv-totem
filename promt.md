/**
 * GUI REDESIGN REQUEST: Modern Config Menu with Enhanced Controls
 * 
 * PROJECT CONTEXT:
 * Minecraft macro mod written in Kotlin using Mojang official mappings.
 * Request to redesign the configuration GUI to match the provided reference image
 * and add granular control over inventory totem functionality.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * DESIGN REFERENCE ANALYSIS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * REFERENCE IMAGE BREAKDOWN:
 * ──────────────────────────
 * The uploaded image shows a "PAPER RIG" configuration menu with the following design:
 * 
 * VISUAL ELEMENTS:
 * ✦ Animated rainbow gradient border (dotted/dashed pattern)
 * ✦ Title bar with gradient text effect ("⚡ PAPER RIG ⚡")
 * ✦ Dark gray/charcoal background (#3C3C3C or similar)
 * ✦ Toggle buttons with status indicators (green "ENABLED", red "DISABLED")
 * ✦ Winner display field showing selection ("Winner: A")
 * ✦ Numeric input fields with labels ("[A] 10", "[B] 8")
 * ✦ Slider control with percentage display ("Win Rate: 100%")
 * ✦ Toggle button with ON/OFF states ("⚠ Notifs: ON")
 * ✦ Navigation buttons ("◂" previous, "▸" next)
 * ✦ Action button ("✓ Done")
 * ✦ Footer text ("Morarkis - v1.2")
 * 
 * DESIGN PRINCIPLES:
 * ✓ Clean, centered layout with consistent spacing
 * ✓ High contrast text (white/yellow on dark background)
 * ✓ Visual feedback for interactive elements
 * ✓ Professional gaming aesthetic
 * ✓ Compact but readable font sizing
 * ✓ Icon usage for visual clarity (⚡, ⚠, ✓, 🎯, etc.)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * IMPLEMENTATION REQUIREMENTS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * CREATE A CONFIG GUI WITH THE FOLLOWING STRUCTURE:
 * 
 * 1. TITLE SECTION:
 *    ┌─────────────────────────────────────────┐
 *    │     ⚡ TOTEM MACRO CONFIG ⚡            │
 *    └─────────────────────────────────────────┘
 *    - Rainbow gradient border animation (RGB cycling)
 *    - Gold/yellow gradient text effect on title
 *    - Lightning bolt emojis or unicode symbols
 * 
 * 2. MAIN CONTROLS SECTION:
 *    ┌─────────────────────────────────────────┐
 *    │  🎯 Offhand Replace: ENABLED            │
 *    │  📦 Inventory Totem: ENABLED            │  ← NEW CONTROL
 *    │  ⚡ Slot Replace: DISABLED              │
 *    │  🤖 Humanization: ENABLED               │
 *    │  ⚠️ Anti-Cheat Mode: ON                 │
 *    └─────────────────────────────────────────┘
 *    
 *    EACH TOGGLE BUTTON MUST:
 *    - Show icon + descriptive label
 *    - Display current state (ENABLED/DISABLED or ON/OFF)
 *    - Use color coding: Green (#00FF00) for ON, Red (#FF0000) for OFF
 *    - Toggle on click with smooth transition
 *    - Play click sound effect
 * 
 * 3. ADVANCED SETTINGS SECTION:
 *    ┌─────────────────────────────────────────┐
 *    │  ⏱️ Min Delay (ms): [50]  [-][+]        │
 *    │  ⏱️ Max Delay (ms): [250] [-][+]        │
 *    │  ❤️ Health Trigger: [6.0] [-][+]        │
 *    │  🎲 Randomization: ████████░░ 80%       │
 *    └─────────────────────────────────────────┘
 *    
 *    NUMERIC INPUTS:
 *    - Editable text fields with increment/decrement buttons
 *    - Input validation (min/max bounds)
 *    - Tooltips explaining each setting
 *    
 *    SLIDERS:
 *    - Visual progress bar showing current value
 *    - Percentage display
 *    - Drag to adjust or click on bar
 * 
 * 4. INVENTORY TOTEM CONTROL (NEW FEATURE):
 *    ┌─────────────────────────────────────────┐
 *    │  📦 Inventory Totem Replace             │
 *    │     Status: ENABLED                     │
 *    │     Mode: [Auto] [Manual] [Disabled]    │
 *    │     Priority: [High] [Normal] [Low]     │
 *    │     Emergency Only: ☐                   │
 *    └─────────────────────────────────────────┘
 *    
 *    MODES EXPLANATION:
 *    - Auto: Automatically replaces from inventory when offhand empty
 *    - Manual: Only replaces when keybind is pressed
 *    - Disabled: Never replaces from inventory (offhand only)
 *    
 *    PRIORITY LEVELS:
 *    - High: Replace immediately on damage (crystal combo protection)
 *    - Normal: Replace with humanization delays
 *    - Low: Replace only when safe (no combat)
 *    
 *    EMERGENCY ONLY:
 *    - If checked, only activates when health < 4 hearts
 *    - Prevents detection during normal gameplay
 * 
 * 5. NAVIGATION AND ACTION BUTTONS:
 *    ┌─────────────────────────────────────────┐
 *    │  ◂ Previous    ✓ Save & Close    Next ▸ │
 *    │                                          │
 *    │  🔄 Reset to Defaults                   │
 *    │  📋 Copy Config    📥 Import Config     │
 *    └─────────────────────────────────────────┘
 * 
 * 6. FOOTER:
 *    ┌─────────────────────────────────────────┐
 *    │         YourName - v1.0.0               │
 *    └─────────────────────────────────────────┘
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * TECHNICAL IMPLEMENTATION GUIDELINES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * USE MINECRAFT'S SCREEN SYSTEM:
 * 
 * ```kotlin
 * class TotemMacroConfigScreen(
 *     private val parent: Screen?
 * ) : Screen(Component.literal("⚡ Totem Macro Config ⚡")) {
 *     
 *     private val config = TotemMacroConfig.instance
 *     
 *     // Widget references
 *     private lateinit var offhandToggle: ToggleButton
 *     private lateinit var inventoryTotemToggle: ToggleButton
 *     private lateinit var slotReplaceToggle: ToggleButton
 *     private lateinit var humanizationToggle: ToggleButton
 *     
 *     private lateinit var minDelayField: EditBox
 *     private lateinit var maxDelayField: EditBox
 *     private lateinit var healthTriggerField: EditBox
 *     
 *     private lateinit var randomizationSlider: Slider
 *     
 *     // Animation state
 *     private var rainbowOffset = 0f
 *     private var tickCount = 0
 *     
 *     override fun init() {
 *         super.init()
 *         
 *         val centerX = width / 2
 *         var currentY = 50
 *         val spacing = 25
 *         
 *         // Title is rendered manually for gradient effect
 *         
 *         // Main toggles
 *         offhandToggle = addRenderableWidget(
 *             ToggleButton(
 *                 centerX - 150, currentY,
 *                 300, 20,
 *                 "🎯 Offhand Replace",
 *                 config.offhandReplaceEnabled
 *             ) { value ->
 *                 config.offhandReplaceEnabled = value
 *             }
 *         )
 *         currentY += spacing
 *         
 *         // NEW: Inventory totem toggle
 *         inventoryTotemToggle = addRenderableWidget(
 *             ToggleButton(
 *                 centerX - 150, currentY,
 *                 300, 20,
 *                 "📦 Inventory Totem",
 *                 config.inventoryTotemEnabled
 *             ) { value ->
 *                 config.inventoryTotemEnabled = value
 *             }
 *         )
 *         currentY += spacing
 *         
 *         slotReplaceToggle = addRenderableWidget(
 *             ToggleButton(
 *                 centerX - 150, currentY,
 *                 300, 20,
 *                 "⚡ Slot Replace",
 *                 config.slotReplaceEnabled
 *             ) { value ->
 *                 config.slotReplaceEnabled = value
 *             }
 *         )
 *         currentY += spacing
 *         
 *         humanizationToggle = addRenderableWidget(
 *             ToggleButton(
 *                 centerX - 150, currentY,
 *                 300, 20,
 *                 "🤖 Humanization",
 *                 config.humanizationEnabled
 *             ) { value ->
 *                 config.humanizationEnabled = value
 *             }
 *         )
 *         currentY += spacing + 10
 *         
 *         // Advanced settings section
 *         addRenderableWidget(Button.builder(
 *             Component.literal("⚙️ Advanced Settings"),
 *             { button -> toggleAdvancedSettings() }
 *         ).bounds(centerX - 100, currentY, 200, 20).build())
 *         
 *         currentY += spacing
 *         
 *         // Numeric inputs (shown if advanced expanded)
 *         if (showAdvanced) {
 *             // Min delay
 *             minDelayField = addRenderableWidget(
 *                 EditBox(font, centerX - 80, currentY, 60, 20,
 *                     Component.literal("Min Delay"))
 *             )
 *             minDelayField.value = config.minDelayMs.toString()
 *             
 *             addRenderableWidget(Button.builder(
 *                 Component.literal("-"),
 *                 { decrementMinDelay() }
 *             ).bounds(centerX - 120, currentY, 20, 20).build())
 *             
 *             addRenderableWidget(Button.builder(
 *                 Component.literal("+"),
 *                 { incrementMinDelay() }
 *             ).bounds(centerX - 15, currentY, 20, 20).build())
 *             
 *             currentY += spacing
 *             
 *             // Similar for max delay and health trigger
 *         }
 *         
 *         currentY += spacing + 10
 *         
 *         // Action buttons
 *         addRenderableWidget(Button.builder(
 *             Component.literal("✓ Save & Close"),
 *             { onSaveAndClose() }
 *         ).bounds(centerX - 60, currentY, 120, 20).build())
 *         
 *         currentY += spacing
 *         
 *         addRenderableWidget(Button.builder(
 *             Component.literal("🔄 Reset Defaults"),
 *             { onResetDefaults() }
 *         ).bounds(centerX - 80, currentY, 160, 20).build())
 *     }
 *     
 *     override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
 *         // Render background
 *         renderBackground(guiGraphics)
 *         
 *         // Render animated rainbow border
 *         renderRainbowBorder(guiGraphics)
 *         
 *         // Render gradient title
 *         renderGradientTitle(guiGraphics)
 *         
 *         // Render all widgets
 *         super.render(guiGraphics, mouseX, mouseY, partialTick)
 *         
 *         // Render tooltips
 *         renderTooltips(guiGraphics, mouseX, mouseY)
 *         
 *         // Render footer
 *         val footerText = "YourName - v1.0.0"
 *         guiGraphics.drawCenteredString(
 *             font, footerText,
 *             width / 2, height - 20,
 *             0x888888
 *         )
 *     }
 *     
 *     private fun renderRainbowBorder(guiGraphics: GuiGraphics) {
 *         val borderInset = 10
 *         val x1 = borderInset
 *         val y1 = borderInset
 *         val x2 = width - borderInset
 *         val y2 = height - borderInset
 *         
 *         // Animate rainbow effect
 *         rainbowOffset += 0.02f
 *         if (rainbowOffset > 1.0f) rainbowOffset = 0f
 *         
 *         // Draw dashed border with rainbow colors
 *         val dashLength = 5
 *         val segments = 50
 *         
 *         for (i in 0 until segments) {
 *             val progress = i.toFloat() / segments
 *             val hue = (progress + rainbowOffset) % 1.0f
 *             val color = Color.HSBtoRGB(hue, 0.8f, 1.0f)
 *             
 *             // Top border
 *             val x = x1 + ((x2 - x1) * progress).toInt()
 *             if (i % 2 == 0) {
 *                 guiGraphics.fill(x, y1, x + dashLength, y1 + 2, color or 0xFF000000.toInt())
 *             }
 *             
 *             // Similar for other borders (left, right, bottom)
 *         }
 *     }
 *     
 *     private fun renderGradientTitle(guiGraphics: GuiGraphics) {
 *         val titleText = "⚡ TOTEM MACRO CONFIG ⚡"
 *         val centerX = width / 2
 *         val titleY = 20
 *         
 *         // Calculate gradient colors (gold to yellow)
 *         val color1 = 0xFFD700 // Gold
 *         val color2 = 0xFFFF00 // Yellow
 *         
 *         // Draw with shadow for depth
 *         guiGraphics.drawCenteredString(
 *             font, titleText,
 *             centerX + 2, titleY + 2,
 *             0x000000 // Shadow
 *         )
 *         
 *         guiGraphics.drawCenteredString(
 *             font, titleText,
 *             centerX, titleY,
 *             color1 // Main color
 *         )
 *     }
 *     
 *     override fun tick() {
 *         super.tick()
 *         tickCount++
 *         
 *         // Update any animated elements
 *         minDelayField?.tick()
 *         maxDelayField?.tick()
 *     }
 *     
 *     private fun onSaveAndClose() {
 *         // Validate all inputs
 *         try {
 *             config.minDelayMs = minDelayField.value.toLong()
 *             config.maxDelayMs = maxDelayField.value.toLong()
 *             config.healthTrigger = healthTriggerField.value.toFloat()
 *             
 *             // Save to file
 *             config.save()
 *             
 *             // Play success sound
 *             minecraft?.soundManager?.play(
 *                 SimpleSoundInstance.forUI(
 *                     SoundEvents.UI_BUTTON_CLICK,
 *                     1.0f
 *                 )
 *             )
 *             
 *             // Close screen
 *             minecraft?.setScreen(parent)
 *             
 *         } catch (e: NumberFormatException) {
 *             // Show error message
 *             renderErrorTooltip("Invalid numeric input!")
 *         }
 *     }
 *     
 *     private fun onResetDefaults() {
 *         config.resetToDefaults()
 *         minecraft?.setScreen(TotemMacroConfigScreen(parent))
 *     }
 * }
 * ```
 * 
 * CUSTOM TOGGLE BUTTON WIDGET:
 * 
 * ```kotlin
 * class ToggleButton(
 *     x: Int, y: Int,
 *     width: Int, height: Int,
 *     private val label: String,
 *     private var enabled: Boolean,
 *     private val onChange: (Boolean) -> Unit
 * ) : AbstractButton(x, y, width, height, Component.literal(label)) {
 *     
 *     override fun onPress() {
 *         enabled = !enabled
 *         onChange(enabled)
 *         
 *         // Play click sound
 *         Minecraft.getInstance().soundManager.play(
 *             SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
 *         )
 *     }
 *     
 *     override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
 *         // Background color (darker if hovered)
 *         val bgColor = if (isHovered) 0x505050 else 0x3C3C3C
 *         guiGraphics.fill(x, y, x + width, y + height, bgColor or 0xFF000000.toInt())
 *         
 *         // Border
 *         val borderColor = if (isHovered) 0xFFFFFF else 0x808080
 *         guiGraphics.renderOutline(x, y, width, height, borderColor or 0xFF000000.toInt())
 *         
 *         // Label text (left aligned)
 *         guiGraphics.drawString(
 *             Minecraft.getInstance().font,
 *             label,
 *             x + 5, y + (height - 8) / 2,
 *             0xFFFFFF
 *         )
 *         
 *         // Status text (right aligned)
 *         val statusText = if (enabled) "ENABLED" else "DISABLED"
 *         val statusColor = if (enabled) 0x00FF00 else 0xFF0000
 *         
 *         val textWidth = Minecraft.getInstance().font.width(statusText)
 *         guiGraphics.drawString(
 *             Minecraft.getInstance().font,
 *             statusText,
 *             x + width - textWidth - 5,
 *             y + (height - 8) / 2,
 *             statusColor
 *         )
 *     }
 *     
 *     override fun updateWidgetNarration(builder: NarrationElementOutput) {
 *         builder.add(NarratedElementType.TITLE, message)
 *         builder.add(NarratedElementType.STATE, 
 *             if (enabled) "Enabled" else "Disabled")
 *     }
 * }
 * ```
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * INVENTORY TOTEM FEATURE REQUIREMENTS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ADD TO CONFIG DATA CLASS:
 * 
 * ```kotlin
 * data class TotemMacroConfig(
 *     // Existing settings
 *     var offhandReplaceEnabled: Boolean = true,
 *     var slotReplaceEnabled: Boolean = false,
 *     var humanizationEnabled: Boolean = true,
 *     
 *     // NEW: Inventory totem settings
 *     var inventoryTotemEnabled: Boolean = true,
 *     var inventoryTotemMode: InventoryTotemMode = InventoryTotemMode.AUTO,
 *     var inventoryTotemPriority: Priority = Priority.NORMAL,
 *     var inventoryTotemEmergencyOnly: Boolean = false,
 *     
 *     // Timing settings
 *     var minDelayMs: Long = 50,
 *     var maxDelayMs: Long = 250,
 *     var healthTrigger: Float = 6.0f,
 *     var randomizationPercent: Int = 80
 * ) {
 *     enum class InventoryTotemMode {
 *         AUTO,    // Automatic replacement
 *         MANUAL,  // Keybind only
 *         DISABLED // Never replace from inventory
 *     }
 *     
 *     enum class Priority {
 *         HIGH,    // Immediate (crystal combo protection)
 *         NORMAL,  // With humanization
 *         LOW      // Only when safe
 *     }
 *     
 *     fun save() {
 *         val configFile = File("config/totem_macro.json")
 *         configFile.writeText(Gson().toJson(this))
 *     }
 *     
 *     companion object {
 *         val instance: TotemMacroConfig by lazy {
 *             val configFile = File("config/totem_macro.json")
 *             if (configFile.exists()) {
 *                 Gson().fromJson(configFile.readText(), TotemMacroConfig::class.java)
 *             } else {
 *                 TotemMacroConfig()
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * LOGIC INTEGRATION:
 * 
 * ```kotlin
 * fun shouldReplaceFromInventory(): Boolean {
 *     val config = TotemMacroConfig.instance
 *     
 *     // Check if feature is enabled
 *     if (!config.inventoryTotemEnabled) return false
 *     
 *     // Check mode
 *     when (config.inventoryTotemMode) {
 *         InventoryTotemMode.DISABLED -> return false
 *         InventoryTotemMode.MANUAL -> {
 *             // Only replace if manual keybind is pressed
 *             return isManualReplaceKeyPressed()
 *         }
 *         InventoryTotemMode.AUTO -> {
 *             // Auto mode - check emergency setting
 *             if (config.inventoryTotemEmergencyOnly) {
 *                 val player = Minecraft.getInstance().player ?: return false
 *                 return player.health <= 8.0f // Only when low health
 *             }
 *             return true
 *         }
 *     }
 * }
 * 
 * fun getReplacementDelay(): Long {
 *     val config = TotemMacroConfig.instance
 *     
 *     return when (config.inventoryTotemPriority) {
 *         Priority.HIGH -> {
 *             // Immediate - no delay
 *             0L
 *         }
 *         Priority.NORMAL -> {
 *             // Normal humanization
 *             Random.nextLong(config.minDelayMs, config.maxDelayMs)
 *         }
 *         Priority.LOW -> {
 *             // Extra delay for safety
 *             Random.nextLong(config.maxDelayMs, config.maxDelayMs * 2)
 *         }
 *     }
 * }
 * ```
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * VISUAL POLISH REQUIREMENTS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * MUST IMPLEMENT:
 * 
 * ✓ Smooth animations for toggle state changes (fade/slide effect)
 * ✓ Rainbow border that cycles through hues continuously
 * ✓ Gradient text rendering for title and important labels
 * ✓ Hover effects on all interactive elements (brightness increase)
 * ✓ Sound effects for button clicks and toggles
 * ✓ Tooltips explaining each setting when hovered
 * ✓ Input validation with visual feedback (red border on invalid)
 * ✓ Smooth scrolling if content exceeds screen height
 * ✓ Keyboard navigation support (Tab to cycle, Enter to toggle)
 * ✓ Visual indication of unsaved changes
 * ✓ Confirmation dialog for "Reset Defaults"
 * 
 * ACCESSIBILITY:
 * 
 * ✓ Screen reader support via narration system
 * ✓ High contrast mode option
 * ✓ Colorblind-friendly palette (not just red/green)
 * ✓ Keyboard-only navigation fully functional
 * ✓ Adjustable text size
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SUCCESS CRITERIA
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ✅ GUI matches reference image aesthetic (modern, clean, professional)
 * ✅ Rainbow border animation runs smoothly at 60 FPS
 * ✅ All settings persist between game sessions
 * ✅ Inventory totem toggle fully functional with all modes
 * ✅ User has complete control over totem replacement behavior
 * ✅ No performance impact on game (GUI renders efficiently)
 * ✅ All tooltips are informative and helpful
 * ✅ Input validation prevents invalid configurations
 * ✅ Keybind for opening config menu works reliably
 * ✅ GUI is responsive on all screen resolutions
 * 
 * Please create a polished, production-ready configuration GUI that matches
 * the visual quality of the reference image while providing comprehensive
 * control over the inventory totem replacement feature. Prioritize user
 * experience and visual appeal alongside functionality.
 */
package inv.totem

import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.min
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

private const val MAX_DELAY_MS = 500

// ─── Color Palette ──────────────────────────────────────────────────────────────
private val PANEL_BG       = 0xF0141422.toInt()
private val PANEL_BG_INNER = 0xF01C1C30.toInt()
private val WIDGET_BG      = 0xFF242440.toInt()
private val WIDGET_HOVER   = 0xFF34345A.toInt()
private val BORDER_DIM     = 0xFF3A3A5A.toInt()
private val BORDER_BRIGHT  = 0xFF5A5A80.toInt()
private val TEXT_PRIMARY   = 0xFFE0E0E0.toInt()
private val TEXT_DIM       = 0xFF808090.toInt()
private val ON_GREEN       = 0xFF2E7D32.toInt()
private val ON_GREEN_TEXT  = 0xFF66FF6A.toInt()
private val OFF_RED        = 0xFFC62828.toInt()
private val OFF_RED_TEXT   = 0xFFFF6B6B.toInt()
private val ACCENT_PURPLE  = 0xFF7C4DFF.toInt()
private val SECTION_BLUE   = 0xFF64B5F6.toInt()
private val TAB_ACTIVE_BG  = 0xFF3A2A6E.toInt()
private val TAB_INACTIVE   = 0xFF1E1E34.toInt()
private val SLIDER_TRACK   = 0xFF1A1A30.toInt()

// ─── HSB → packed-ARGB helper ───────────────────────────────────────────────────
private fun hsbToArgb(hue: Float, sat: Float, bri: Float): Int {
    val h = ((hue % 1f + 1f) % 1f * 6f).toInt()
    val f = hue * 6f - h
    val p = bri * (1 - sat)
    val q = bri * (1 - f * sat)
    val t = bri * (1 - (1 - f) * sat)
    val (r, g, b) = when (h % 6) {
        0 -> Triple(bri, t, p)
        1 -> Triple(q, bri, p)
        2 -> Triple(p, bri, t)
        3 -> Triple(p, q, bri)
        4 -> Triple(t, p, bri)
        else -> Triple(bri, p, q)
    }
    return (0xFF shl 24) or
           ((r * 255).toInt().coerceIn(0, 255) shl 16) or
           ((g * 255).toInt().coerceIn(0, 255) shl 8) or
           (b * 255).toInt().coerceIn(0, 255)
}

// ─── Layout Constants ───────────────────────────────────────────────────────────
private object Layout {
    const val ELEMENT_HEIGHT = 20
    const val ELEMENT_SPACING = 24
    const val SECTION_GAP = 12         // extra gap before a section header
    const val SECTION_LABEL_HEIGHT = 14 // space the label text occupies
    const val TAB_HEIGHT = 20
    const val TAB_GAP = 4
    const val CONTENT_PAD_TOP = 6     // gap between tab bar bottom → first content
}

// ═════════════════════════════════════════════════════════════════════════════════
//  Main config screen
// ═════════════════════════════════════════════════════════════════════════════════
class InvTotemConfigScreen(private val parentScreen: Screen) :
    Screen(Component.literal("Totem Macro Config")) {

    // ── Persisted-state mirrors ─────────────────────────────────────────────────
    private var enabled                   = ConfigManager.isEnabled()
    private var instantClick              = ConfigManager.isInstantClickTotemEnabled()
    private var debugMode                 = ConfigManager.isDebugModeEnabled()
    private var selectedDelayMs           = ConfigManager.getSwapDelayMs()

    private var invTotemEnabled           = ConfigManager.isInventoryTotemEnabled()
    private var invTotemMode              = ConfigManager.getInventoryTotemMode()
    private var invTotemPriority          = ConfigManager.getInventoryTotemPriority()
    private var invTotemEmergencyOnly     = ConfigManager.isInventoryTotemEmergencyOnly()

    private var slotReplaceEnabled        = ConfigManager.isItemSlotReplaceEnabled()
    private var slotReplaceHotbarSlot     = ConfigManager.getItemSlotReplaceHotbarSlot()
    private var autoSelectSlot            = ConfigManager.isAutoSelectItemSlotEnabled()

    // ── Animation / page state ──────────────────────────────────────────────────
    private var animTick = 0L
    private enum class Page { GENERAL, INV_TOTEM, SLOT_MODE }
    private var page = Page.GENERAL

    // ── Layout (computed once in init) ───────────────────────────────────────────
    private var panelW = 0; private var panelH = 0
    private var panelLeft = 0; private var panelTop = 0
    private var wLeft = 0; private var wWidth = 0

    // ── Section header y-positions (filled during build, drawn during render) ───
    private val sectionHeaders = mutableListOf<Pair<Int, String>>() // (y, label)

    // ════════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════════════
    override fun init() {
        super.init()
        panelW = min(310, width - 24)
        panelH = height - 30
        panelLeft = (width - panelW) / 2
        panelTop = 15
        wWidth = panelW - 36
        wLeft = panelLeft + 18
        rebuildWidgets()
    }

    override fun tick() { super.tick(); animTick++ }

    // ════════════════════════════════════════════════════════════════════════════
    //  Widget construction per page
    // ════════════════════════════════════════════════════════════════════════════
    override fun rebuildWidgets() {
        clearWidgets()
        sectionHeaders.clear()

        val tabY = panelTop + 30
        val contentY = tabY + Layout.TAB_HEIGHT + Layout.CONTENT_PAD_TOP

        // ── Tab buttons (use vanilla Button so click dispatch is guaranteed) ────
        val tabW = (wWidth - Layout.TAB_GAP * 2) / 3
        addTab(wLeft, tabY, tabW, "General", Page.GENERAL)
        addTab(wLeft + tabW + Layout.TAB_GAP, tabY, tabW, "Inv. Totem", Page.INV_TOTEM)
        addTab(wLeft + (tabW + Layout.TAB_GAP) * 2, tabY, tabW, "Slot Mode", Page.SLOT_MODE)

        var y = contentY
        val sp = Layout.ELEMENT_SPACING

        when (page) {
            Page.GENERAL -> {
                y = addSectionHeader(y, "\u2699 General")
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\u26A1", "Auto-Totem", enabled, "ENABLED", "DISABLED",
                    "Master toggle for the entire totem macro.") { enabled = it })
                y += sp
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\u26A1", "Instant Click", instantClick, "ON", "OFF",
                    "Skip safety buffer and click immediately.") { instantClick = it })
                y += sp
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\uD83D\uDC1B", "Debug Mode", debugMode, "ON", "OFF",
                    "Log verbose state-machine output.") { debugMode = it })
                y += sp + Layout.SECTION_GAP
                y = addSectionHeader(y, "\u23F1 Timing")
                addRenderableWidget(
                    StyledSlider(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT, selectedDelayMs) {
                        selectedDelayMs = it
                    }
                )
            }
            Page.INV_TOTEM -> {
                y = addSectionHeader(y, "\uD83D\uDCE6 Inventory Totem")
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\uD83D\uDCE6", "Inv. Totem Replace", invTotemEnabled, "ENABLED", "DISABLED",
                    "Pull totems from inventory to replace.") { invTotemEnabled = it })
                y += sp
                addCycleButton(wLeft, y, wWidth, "Mode", invTotemMode,
                    listOf("AUTO", "MANUAL", "DISABLED"),
                    "AUTO = auto on pop, MANUAL = keybind, DISABLED = never.") { invTotemMode = it }
                y += sp
                addCycleButton(wLeft, y, wWidth, "Priority", invTotemPriority,
                    listOf("HIGH", "NORMAL", "LOW"),
                    "HIGH = instant, NORMAL = humanized, LOW = extra safe.") { invTotemPriority = it }
                y += sp
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\u2764", "Emergency Only", invTotemEmergencyOnly, "ON", "OFF",
                    "Only activate below 4 hearts.") { invTotemEmergencyOnly = it })
            }
            Page.SLOT_MODE -> {
                y = addSectionHeader(y, "\u2699 Slot Mode")
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\uD83D\uDD27", "Slot Replace", slotReplaceEnabled, "ENABLED", "DISABLED",
                    "Replace into a hotbar slot instead of offhand.") { slotReplaceEnabled = it })
                y += sp
                addCycleButton(wLeft, y, wWidth, "Target Slot", slotReplaceHotbarSlot.toString(),
                    (1..9).map { it.toString() },
                    "Hotbar slot to place the totem.") { slotReplaceHotbarSlot = it.toInt() }
                y += sp
                addRenderableWidget(ToggleWidget(wLeft, y, wWidth, Layout.ELEMENT_HEIGHT,
                    "\uD83C\uDFAF", "Auto Select", autoSelectSlot, "ON", "OFF",
                    "Only replace if selected slot matches target.") { autoSelectSlot = it })
            }
        }

        // ── Bottom action buttons ───────────────────────────────────────────────
        val bottomY = panelTop + panelH - 48
        val btnW = (wWidth - 6) / 2
        addRenderableWidget(
            Button.builder(Component.literal("\u2714 Save & Close")) { saveAndClose() }
                .bounds(wLeft, bottomY, btnW, 20)
                .tooltip(Tooltip.create(Component.literal("Save all settings and return.")))
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("\uD83D\uDD04 Reset")) { resetDefaults() }
                .bounds(wLeft + btnW + 6, bottomY, btnW, 20)
                .tooltip(Tooltip.create(Component.literal("Restore every setting to default.")))
                .build()
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    private fun addTab(x: Int, y: Int, w: Int, label: String, target: Page) {
        addRenderableWidget(TabButton(x, y, w, Layout.TAB_HEIGHT, label, page == target) {
            if (page != target) {
                page = target
                rebuildWidgets()
            }
        })
    }

    /** Records a section header position and advances y past the label area. */
    private fun addSectionHeader(y: Int, label: String): Int {
        sectionHeaders.add(y to label)
        return y + Layout.SECTION_LABEL_HEIGHT
    }

    private fun addCycleButton(x: Int, y: Int, w: Int, label: String,
                                current: String, values: List<String>,
                                tip: String, onChange: (String) -> Unit) {
        var tracked = current
        val btn = Button.builder(Component.literal("$label: $tracked")) { button ->
            val idx = (values.indexOf(tracked) + 1) % values.size
            tracked = values[idx]
            onChange(tracked)
            button.message = Component.literal("$label: $tracked")
        }.bounds(x, y, w, Layout.ELEMENT_HEIGHT)
         .tooltip(Tooltip.create(Component.literal(tip)))
         .build()
        addRenderableWidget(btn)
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Rendering
    // ════════════════════════════════════════════════════════════════════════════
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)

        val g = guiGraphics
        val pr = panelLeft; val pt = panelTop
        val pw = panelW;    val ph = panelH

        // ── Panel background ────────────────────────────────────────────────────
        g.fill(pr, pt, pr + pw, pt + ph, PANEL_BG)
        g.fill(pr + 2, pt + 2, pr + pw - 2, pt + ph - 2, PANEL_BG_INNER)

        // ── Animated rainbow border ─────────────────────────────────────────────
        renderRainbowBorder(g, pr, pt, pw, ph)

        // ── Gradient title ──────────────────────────────────────────────────────
        renderGradientTitle(g)

        // ── Section headers (drawn before widgets so text sits behind controls) ─
        for ((sy, label) in sectionHeaders) {
            drawSectionLabel(g, sy, label)
        }

        // ── Widgets (via super) ─────────────────────────────────────────────────
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // ── Footer ──────────────────────────────────────────────────────────────
        val footer = "sfblair - v1.0.8"
        val fx = (width - font.width(footer)) / 2
        g.drawString(font, footer, fx, pt + ph - 14, TEXT_DIM, false)
    }

    // ── Rainbow border (4px segments, smooth hue cycling) ───────────────────────
    private fun renderRainbowBorder(g: GuiGraphics, px: Int, py: Int, pw: Int, ph: Int) {
        val t = animTick * 0.04f
        val seg = 4; val thick = 2
        val x2 = px + pw; val y2 = py + ph

        // Top
        for (x in px until x2 step seg) {
            val w = min(seg, x2 - x)
            val hue = ((x - px).toFloat() / pw + t) % 1f
            g.fill(x, py, x + w, py + thick, hsbToArgb(hue, 0.9f, 1f))
        }
        // Bottom
        for (x in px until x2 step seg) {
            val w = min(seg, x2 - x)
            val hue = ((x - px).toFloat() / pw + t + 0.5f) % 1f
            g.fill(x, y2 - thick, x + w, y2, hsbToArgb(hue, 0.9f, 1f))
        }
        // Left
        for (y in py until y2 step seg) {
            val h = min(seg, y2 - y)
            val hue = ((y - py).toFloat() / ph + t + 0.25f) % 1f
            g.fill(px, y, px + thick, y + h, hsbToArgb(hue, 0.9f, 1f))
        }
        // Right
        for (y in py until y2 step seg) {
            val h = min(seg, y2 - y)
            val hue = ((y - py).toFloat() / ph + t + 0.75f) % 1f
            g.fill(x2 - thick, y, x2, y + h, hsbToArgb(hue, 0.9f, 1f))
        }
    }

    // ── Per-character gold/yellow gradient title ────────────────────────────────
    private fun renderGradientTitle(g: GuiGraphics) {
        val title = "\u26A1 TOTEM MACRO CONFIG \u26A1"
        val totalW = font.width(title)
        var cx = (width - totalW) / 2
        val ty = panelTop + 10
        val t = animTick * 0.06f

        for ((i, ch) in title.withIndex()) {
            val p = i.toFloat() / title.length
            val r = 255
            val gv = (200 + 55 * sin((p * Math.PI + t).toFloat())).toInt().coerceIn(170, 255)
            val b = (40 + 40 * sin((p * Math.PI * 2 + t).toFloat())).toInt().coerceIn(0, 90)
            val color = (0xFF shl 24) or (r shl 16) or (gv shl 8) or b
            val s = ch.toString()
            g.drawString(font, s, cx + 1, ty + 1, 0xFF000000.toInt(), false)
            g.drawString(font, s, cx, ty, color, false)
            cx += font.width(s)
        }
    }

    // ── Section label ───────────────────────────────────────────────────────────
    private fun drawSectionLabel(g: GuiGraphics, y: Int, label: String) {
        g.drawString(font, label, wLeft, y + 2, SECTION_BLUE, true)
        val lw = font.width(label)
        g.fill(wLeft, y + Layout.SECTION_LABEL_HEIGHT - 2, wLeft + lw,
               y + Layout.SECTION_LABEL_HEIGHT - 1, 0x60_64B5F6)
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Actions
    // ════════════════════════════════════════════════════════════════════════════
    override fun onClose() { saveAndClose() }

    private fun saveAndClose() {
        ConfigManager.setEnabled(enabled)
        ConfigManager.setInstantClickTotemEnabled(instantClick)
        ConfigManager.setDebugModeEnabled(debugMode)
        ConfigManager.setSwapDelayMs(selectedDelayMs)
        ConfigManager.setInventoryTotemEnabled(invTotemEnabled)
        ConfigManager.setInventoryTotemMode(invTotemMode)
        ConfigManager.setInventoryTotemPriority(invTotemPriority)
        ConfigManager.setInventoryTotemEmergencyOnly(invTotemEmergencyOnly)
        ConfigManager.setItemSlotReplaceEnabled(slotReplaceEnabled)
        ConfigManager.setItemSlotReplaceHotbarSlot(slotReplaceHotbarSlot)
        ConfigManager.setAutoSelectItemSlotEnabled(autoSelectSlot)
        minecraft?.setScreen(parentScreen)
    }

    private fun resetDefaults() {
        ConfigManager.resetToDefaults()
        enabled = true; instantClick = false; debugMode = false; selectedDelayMs = 100
        invTotemEnabled = true; invTotemMode = "AUTO"; invTotemPriority = "NORMAL"; invTotemEmergencyOnly = false
        slotReplaceEnabled = false; slotReplaceHotbarSlot = 1; autoSelectSlot = false
        rebuildWidgets()
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Custom Widgets
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Stylish toggle widget: icon + label left, colored status pill right.
     * Uses mouseClicked override for guaranteed click dispatch.
     */
    private class ToggleWidget(
        x: Int, y: Int, w: Int, h: Int,
        private val icon: String,
        private val label: String,
        private var toggled: Boolean,
        private val onText: String,
        private val offText: String,
        tip: String,
        private val onChange: (Boolean) -> Unit,
    ) : AbstractWidget(x, y, w, h, Component.literal(label)) {

        init { setTooltip(Tooltip.create(Component.literal(tip))) }

        override fun onClick(event: MouseButtonEvent, pressed: Boolean) {
            if (pressed) {
                toggled = !toggled
                onChange(toggled)
            }
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val mc = Minecraft.getInstance()
            val g = guiGraphics
            val bg = if (isHovered) WIDGET_HOVER else WIDGET_BG
            val brd = if (isHovered) BORDER_BRIGHT else BORDER_DIM

            // Background
            g.fill(x, y, x + width, y + height, bg)
            // Border
            g.fill(x, y, x + width, y + 1, brd)
            g.fill(x, y + height - 1, x + width, y + height, brd)
            g.fill(x, y, x + 1, y + height, brd)
            g.fill(x + width - 1, y, x + width, y + height, brd)

            // Icon + label
            val textY = y + (height - 8) / 2
            g.drawString(mc.font, "$icon $label", x + 6, textY, TEXT_PRIMARY, true)

            // Status pill
            val status = if (toggled) onText else offText
            val pillBg = if (toggled) ON_GREEN else OFF_RED
            val pillFg = if (toggled) ON_GREEN_TEXT else OFF_RED_TEXT
            val tw = mc.font.width(status)
            val pw = tw + 10; val ph = 12
            val pillX = x + width - pw - 6
            val pillY = y + (height - ph) / 2
            g.fill(pillX, pillY, pillX + pw, pillY + ph, pillBg)
            g.drawString(mc.font, status, pillX + 5, pillY + 2, pillFg, false)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE,
                Component.literal("$label: ${if (toggled) onText else offText}"))
        }
    }

    /**
     * Tab button with active/inactive visual state.
     */
    private class TabButton(
        x: Int, y: Int, w: Int, h: Int,
        private val label: String,
        private val selected: Boolean,
        private val onPress: () -> Unit,
    ) : AbstractWidget(x, y, w, h, Component.literal(label)) {

        override fun onClick(event: MouseButtonEvent, pressed: Boolean) {
            if (pressed) onPress()
        }

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val mc = Minecraft.getInstance()
            val g = guiGraphics
            val bg = when {
                selected -> TAB_ACTIVE_BG
                isHovered -> WIDGET_HOVER
                else -> TAB_INACTIVE
            }
            g.fill(x, y, x + width, y + height, bg)

            // Active indicator bar at bottom
            if (selected) {
                g.fill(x, y + height - 2, x + width, y + height, ACCENT_PURPLE)
            }

            // Border
            val brd = if (selected) ACCENT_PURPLE else BORDER_DIM
            g.fill(x, y, x + width, y + 1, brd)
            g.fill(x, y, x + 1, y + height, brd)
            g.fill(x + width - 1, y, x + width, y + height, brd)

            val textColor = if (selected) 0xFFFFFFFF.toInt() else TEXT_DIM
            val tw = mc.font.width(label)
            g.drawString(mc.font, label, x + (width - tw) / 2, y + (height - 8) / 2, textColor, true)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, Component.literal(label))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════
//  Styled delay slider (themed to match toggle widgets)
// ═════════════════════════════════════════════════════════════════════════════════
private class StyledSlider(
    x: Int, y: Int, w: Int, h: Int,
    initialDelayMs: Int,
    private val onValueChanged: (Int) -> Unit,
) : AbstractSliderButton(
    x, y, w, h,
    Component.literal(""),
    initialDelayMs.coerceIn(0, MAX_DELAY_MS).toDouble() / MAX_DELAY_MS.toDouble(),
) {
    private var delayMs = initialDelayMs.coerceIn(0, MAX_DELAY_MS)

    init { updateMessage() }

    override fun updateMessage() {
        message = Component.literal("\u23F1 Swap Delay: ${currentMs()}ms")
    }

    override fun applyValue() {
        delayMs = currentMs()
        onValueChanged(delayMs)
        updateMessage()
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val g = guiGraphics
        val bg = if (isHovered) WIDGET_HOVER else WIDGET_BG
        val brd = if (isHovered) BORDER_BRIGHT else BORDER_DIM

        // Background — matches toggle widget style
        g.fill(x, y, x + width, y + height, bg)

        // Border — matches toggle widget style
        g.fill(x, y, x + width, y + 1, brd)
        g.fill(x, y + height - 1, x + width, y + height, brd)
        g.fill(x, y, x + 1, y + height, brd)
        g.fill(x + width - 1, y, x + width, y + height, brd)

        // Label text (left side)
        val label = "\u23F1 Swap Delay"
        val textY = y + (height - 8) / 2
        g.drawString(mc.font, label, x + 6, textY, TEXT_PRIMARY, true)

        // Value text (right side) — purple accent to match theme
        val valueText = "${currentMs()}ms"
        val valueWidth = mc.font.width(valueText)
        g.drawString(mc.font, valueText, x + width - valueWidth - 8, textY, ACCENT_PURPLE, true)

        // Track (thin bar between label and value display)
        val labelEnd = x + 6 + mc.font.width(label) + 8
        val valueStart = x + width - valueWidth - 16
        val trackY = y + height / 2 - 1
        val trackH = 3

        // Track background
        g.fill(labelEnd, trackY, valueStart, trackY + trackH, SLIDER_TRACK)

        // Track fill (purple)
        val progress = value.toFloat()
        val fillW = ((valueStart - labelEnd) * progress).toInt()
        if (fillW > 0) {
            g.fill(labelEnd, trackY, labelEnd + fillW, trackY + trackH, ACCENT_PURPLE)
        }

        // Handle dot
        val handleX = labelEnd + fillW
        val handleR = 4
        val handleCY = y + height / 2
        g.fill(handleX - handleR, handleCY - handleR,
               handleX + handleR, handleCY + handleR, 0xFFFFFFFF.toInt())
        g.fill(handleX - handleR + 1, handleCY - handleR + 1,
               handleX + handleR - 1, handleCY + handleR - 1, ACCENT_PURPLE)
    }

    private fun currentMs(): Int =
        (value * MAX_DELAY_MS).roundToInt().coerceIn(0, MAX_DELAY_MS)
}

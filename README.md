# Inv-Totem: Delayed Auto-Totem Refill Mod

A production-ready Minecraft Fabric client-side mod (1.21.10, Kotlin) that automatically refills a Totem of Undying after it pops, using tick-based delays.

## Mod Pages
- Modrinth: https://modrinth.com/mod/inv-totem
- CurseForge: https://legacy.curseforge.com/minecraft/mc-mods/inv-totem
- Issues: https://github.com/R3TR1X/inv-totem/issues

## Architecture Overview

### Core Components

#### 1. `ConfigManager.kt`
Manages JSON-based configuration stored at `~/.minecraft/config/inv-totem-config.json`.

**Configuration Options:**
- `swapDelayMs` (int, default: 100ms, range: 0-500ms): Delay before clicking the totem slot. Adjust to balance anti-cheat evasion vs. responsiveness.
- `instantClickTotem` (bool, default: false): Uses a fixed fast path with safety guards for fast refill.
- `itemSlotReplace` (bool, default: false): Refill to a selected hotbar slot instead of offhand.
- `itemSlotReplaceHotbarSlot` (int, default: 1, range: 1-9): Hotbar slot used when `itemSlotReplace` is enabled.
- `autoSelectItemSlot` (bool, default: false): When enabled, slot mode only triggers if the selected hotbar slot matches `itemSlotReplaceHotbarSlot`.
- `enabled` (bool, default: true): Toggle the feature on/off.
- `debugMode` (bool, default: false): Enables verbose state-machine logs for troubleshooting.

**Example Config:**
```json
{
  "swapDelayMs": 100,
  "instantClickTotem": false,
  "itemSlotReplace": false,
  "itemSlotReplaceHotbarSlot": 1,
  "autoSelectItemSlot": false,
  "enabled": true,
  "debugMode": false
}
```

#### 2. `TotemMacroTracker.kt`
The core state machine that manages the automated totem replacement sequence. Uses a tick-based scheduler hooked into `ClientTickEvents.END_CLIENT_TICK` to avoid blocking the main thread.

**State Machine Flow:**
1. **IDLE** → Waiting for target-slot totem pop detection
2. **INVENTORY_OPENING** → Opens the inventory screen
3. **SAFETY_BUFFER** → Waits ~75ms (2 ticks) for server sync
4. **SCANNING_FOR_TOTEM** → Scans main inventory (slots 0-35) for Totem of Undying
5. **WAITING_FOR_SWAP_DELAY** → Waits configurable delay (100ms default)
6. **FIRST_CLICK** → Clicks the totem slot to pick it up
7. **CLICK_COOLDOWN** → Brief 1-tick pause between clicks
8. **SECOND_CLICK** → Clicks configured target slot (offhand or selected hotbar slot)
9. **POST_TARGET_SYNC** → Waits 1-2 ticks for server sync to reduce cursor ghosting
10. **VERIFY_OFFHAND** → Verifies target slot state and retries if interrupted
11. **CLOSING_INVENTORY** → Closes inventory screen and returns to idle

**Key Features:**
- No `Thread.sleep()`; fully tick-based using Minecraft client tick events
- Totem pop detection via current target item state tracking
- Legitimate screen interaction using an inventory screen
- Server-friendly slot clicking via inventory packet path (`handleInventoryMouseClick`)
- Configurable timing for different server behavior
- Interruption recovery for pickup races and cursor desync
- Input suppression during swap to prevent manual click collision

#### 3. `InvtotemClient.kt`
Client-side entry point. Initializes and registers event listeners:
- Loads configuration on startup
- Registers `TotemMacroTracker.onClientTick()` to `ClientTickEvents.END_CLIENT_TICK`

#### 4. `Invtotem.kt`
Server-side entry point (required for Fabric mod structure, though this is a client-only mod).

## Technical Details

### Supported Version
- Current version: `v1.0.4`

### Configuration File Location
```
~/.minecraft/config/inv-totem-config.json
```
Auto-created on first launch with default values.

### Tick-Based Scheduling
- Client tick = ~50ms at 20 TPS (normal Minecraft)
- Safety buffer: 2 ticks (~100ms) after inventory open
- Configurable swap delay: Variable (default 100ms ≈ 2 ticks)
- Click cooldown: 1 tick (~50ms) between inventory and offhand click

### Inventory Slot Mapping
- Main inventory: Slots 0-35
- Armor: Slots 36-39
- Offhand: Slot 45

### Minecraft API Usage
- **Detection**: Tracks `PlayerEntity.offHand` item changes
- **Interaction**: Uses `InteractionManager.clickSlot()` with `SlotActionType.PICKUP`
- **Timing**: Hooked into `ClientTickEvents.END_CLIENT_TICK` from Fabric API
- **Mappings**: Official Mojang 1.21.10 mappings

## Evasion Strategy

1. **Physical UI Interaction**: Opens real `InventoryScreen` instead of manipulating inventory directly
2. **Configurable Delays**: Custom `swapDelayMs` allows tuning for specific servers' detection patterns
3. **Server Sync Window**: 75ms safety buffer after inventory open ensures server recognizes screen state
4. **Legitimate Slot Clicks**: Uses standard `InteractionManager` API (identical to player clicks)
5. **Stateful Timing**: Avoids burst clicks - tick-based delays make pattern recognition difficult

## Usage

### 1. Install the Mod
Place the compiled `.jar` file in `~/.minecraft/mods/`

### 2. Launch Minecraft
Load the mod through the Fabric launcher.

### 3. Configure (Optional)
Edit `~/.minecraft/config/inv-totem-config.json`:
```json
{
  "swapDelayMs": 75,
  "instantClickTotem": false,
  "itemSlotReplace": false,
  "itemSlotReplaceHotbarSlot": 1,
  "autoSelectItemSlot": false,
  "enabled": true,
  "debugMode": false
}
```
- Lower values (25-50ms): Faster auto-swap, higher detection risk
- Higher values (150-250ms): Slower auto-swap, safer on strict servers
- `instantClickTotem: true`: Uses a 1-tick fast path before first click
- `itemSlotReplace: true`: Targets the selected hotbar slot instead of offhand
- `itemSlotReplaceHotbarSlot: 1-9`: Chooses which hotbar slot to keep filled with totems
- `autoSelectItemSlot: true`: Only runs slot mode when your selected hotbar slot matches the configured slot
- `debugMode: true`: Adds detailed `[debug]` logs to help diagnose edge cases

### 4. Config Menu Tabs

The in-game config screen now uses two tabs:
- Main Settings: delay, instant mode, debug mode
- Slot Mode: item-slot replace toggle, target slot selector, auto-select condition

Use the `Slot Mode` button under `Done` to switch tabs.

### 4. Use
Simply play with a Totem of Undying in your inventory. When the totem pops, the mod automatically:
1. Opens inventory
2. Finds and clicks the new totem
3. Swaps it to your configured target (offhand or selected hotbar slot)
4. Closes inventory

All within ~300-500ms total (configurable).

## Building

```bash
./gradlew build
```

Output JAR: `build/libs/inv-totem-*-client.jar`

## Dependencies (Gradle)
- Minecraft 1.21.10 (Mojang mappings)
- Fabric Loader 0.18.5+
- Fabric API
- Fabric Language Kotlin

## Code Quality and Safety

- Zero blocking operations (no `Thread.sleep()`)
- Null-safe operations in Kotlin
- Structured logging for debugging
- Clean state-machine implementation
- Config validation and clamping
- Graceful error recovery (aborts and returns to idle on failures)

## Logging

The mod logs to SLF4J. View logs in:
- Console during development
- `~/.minecraft/logs/latest.log` in production

Key log messages:
- `Totem pop detected!` — Replacement sequence initiated
- `Found totem at slot: X` — Totem located in inventory
- `Inventory closed, totem replacement complete` — Successful swap
- `Totem not found in inventory, aborting` — Warning if totem unavailable

## Troubleshooting

**Config not loading?**
- Ensure `~/.minecraft/config/inv-totem-config.json` is valid JSON
- Check logs for parse errors
- Delete the config file to regenerate with defaults

**Totem not swapping?**
- Verify you have a Totem of Undying in your main inventory (not offhand)
- Check `enabled: true` in config
- Review logs for "Totem not found" warning

**Anti-cheat triggering?**
- Increase `swapDelayMs` (try 150-200ms)
- Ensure inventory opens/closes visibly (not teleporting)
- Verify slot clicks are registered on server side

## License
MIT

## Author
r3tr1x


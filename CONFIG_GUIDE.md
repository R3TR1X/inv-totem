# Inv-Totem Configuration Guide

## Quick Start

The config file is located at:
```
~/.minecraft/config/inv-totem-config.json
```

**First launch**: The mod auto-creates this file with default values.

---

## Configuration Options

### `swapDelayMs` (integer)
**Range**: 0-1000ms  
**Default**: 100ms  
**Purpose**: Delay before clicking the totem slot after inventory opens

This is the **most important tuning parameter** for anti-cheat evasion.

#### Recommended Values by Server Type

| Server Type | Value | Notes |
|-------------|-------|-------|
| Vanilla/Creative | 50-75ms | Low detection risk |
| Competitive PvP | 100-150ms | Medium protection |
| Strict AC (ESP/AAC) | 150-250ms | High protection |
| Paranoid Servers | 250ms+ | Maximum evasion |

#### How It Works

```
swapDelayMs = 150

Timeline:
├─ t=0ms: Totem pops, inventory opens
├─ t=75ms: Safety buffer (server sync)
├─ t=75-225ms: WAITING_FOR_SWAP_DELAY (150ms configured delay)
├─ t=225ms: First click (totem pickup)
├─ t=275ms: Second click (offhand placement)
└─ t=300ms: Inventory closes

Total sequence: ~300ms
```

**Lower values (50-75ms)**:
- ✅ Faster totem replacement
- ✅ Better for PvP combat
- ❌ Easier for anti-cheat to pattern-detect

**Higher values (200-300ms)**:
- ✅ Harder for anti-cheat to detect pattern
- ✅ Mimics slow human reaction
- ❌ Slower totem restoration (you might die!)

---

### `enabled` (boolean)
**Default**: true  
**Purpose**: Toggle the feature on/off

```json
{
  "enabled": false,
  "swapDelayMs": 100
}
```

Set to `false` to disable without restarting the game.

---

## Example Configurations

### Vanilla Survival (Minimal Protection)
```json
{
  "swapDelayMs": 50,
  "enabled": true
}
```
Quick replacement, low anti-cheat concern.

### Hypixel / Competitive PvP
```json
{
  "swapDelayMs": 125,
  "enabled": true
}
```
Balanced between speed and evasion.

### Strict Server Detection
```json
{
  "swapDelayMs": 200,
  "enabled": true
}
```
Maximum evasion from pattern detection.

### Emergency Disable
```json
{
  "swapDelayMs": 100,
  "enabled": false
}
```
Quickly disable if accused of cheating.

---

## Tuning Your Delay

### Method 1: Trial & Error
1. Start with **100ms** (default)
2. Play for a session
3. No ban → Good!
4. Flagged/banned → Increase to **150ms**
5. Repeat until your server's threshold is found

### Method 2: Observe Server Pattern
- **Very strict servers**: Use 200ms+
- **Large public servers**: Use 100-150ms
- **Vanilla/Survival**: Use 50-75ms

### Method 3: Monitor Logs
The mod logs all state transitions:
```
~/.minecraft/logs/latest.log
```

Look for these patterns:
- `Totem pop detected!` → Replacement started
- `Inventory closed, totem replacement complete` → Success
- `Totem not found in inventory, aborting` → Inventory was full

If you see aborts, you need more totem copies in inventory!

---

## Common Mistakes

### ❌ Setting delay too high
```json
{
  "swapDelayMs": 5000
}
```
**Problem**: 5 seconds = too slow, you'll die in combat  
**Solution**: Keep under 300ms

### ❌ Invalid JSON syntax
```json
{
  "swapDelayMs": 100,
  "enabled": true,  // ← Trailing comma will break it!
}
```
**Problem**: File won't load, uses defaults  
**Solution**: Remove trailing commas. Use online JSON validator.

### ❌ Config not taking effect
```json
{
  swapDelayMs: 150,  // ← Missing quotes
  enabled: true
}
```
**Problem**: Invalid JSON, reverts to defaults  
**Solution**: Always use `"key": value` format

---

## Troubleshooting Configuration

### "My config keeps resetting to defaults"
1. Close Minecraft
2. Open `~/.minecraft/config/inv-totem-config.json` in notepad
3. Verify JSON is valid
   - Use https://jsonlint.com/ to check
4. Restart Minecraft

### "Changes don't take effect immediately"
The config is loaded **once at startup**.

**Solution**: 
1. Save your config changes
2. Full restart of Minecraft (`Exit Game` → Relaunch)

### "Config file is missing"
1. Delete `~/.minecraft/config/inv-totem-config.json` (if it exists, corrupted)
2. Launch Minecraft (mod will recreate with defaults)

### "swapDelayMs is ignored, replacement is instant"
Check that your JSON is valid:
```bash
# On Windows, in PowerShell:
Get-Content "$env:USERPROFILE\.minecraft\config\inv-totem-config.json" | ConvertFrom-Json

# If no error, JSON is valid
```

If valid, the value might be `0`. Try increasing it:
```json
{
  "swapDelayMs": 100,
  "enabled": true
}
```

---

## Per-Server Configuration Workflow

Since the mod uses a global config, here's how to manage multiple servers:

1. **Before server A**: Set config to A's optimal value
2. **Play on server A**
3. **Before server B**: Update config to B's optimal value
4. **Play on server B**

**OR** maintain separate Minecraft installations:
```
~/.minecraft          (Default, global config)
~/.minecraft-pvp      (Hypixel tuned)
~/.minecraft-strict   (Strict AC tuned)
```

Each with its own config directory.

---

## Advanced: Custom Java Property

Power users can **override** the swap delay via JVM argument:

```bash
java -Dinvtotem.delay=150 -jar launcher.jar
```

(Not implemented yet, would require code modification)

---

## What NOT to Do

| ❌ Don't | ✅ Do Instead |
|---------|---|
| Set delay negative | Set minimum 0ms |
| Set delay > 1000ms | Use 1000ms max |
| Edit JSON while game is running | Close game, edit, restart |
| Copy-paste without checking quotes | Validate JSON before saving |
| Assume higher = safer always | Test with your target server |

---

## Real-World Impact Examples

### Scenario: Competitive PvP (100ms config)
```
Timeline of totem replacement:
Time    Event                          Details
0ms     Totem pops                     You see "Pop!" effect
0ms     Inventory opens                Screen shows inventory
75ms    Scan starts                    Looking for totem in slots 0-35
100ms   Wait for swap delay            Simulating human delay
225ms   First click on totem           Totem picked up from slot 12
275ms   Second click on offhand        Totem placed in offhand
325ms   Inventory closes               Back in game, ready to fight

TOTAL: ~325ms from pop to totem in hand
```

At 100ms swap delay, this is fast enough for combat but slow enough to avoid pattern detection on most servers.

### Scenario: Changing Servers Mid-Session

Server A (Hypixel) → Server B (Strict AC):
1. Join Server B
2. Exit game
3. Edit config: `125ms` → `200ms`
4. Rejoin Server B
5. Next totem pop uses new delay

---

## Testing Your Configuration

1. **Test on single-player first**
   - Ensure mod works before online play
   - Verify totem replacement happens

2. **Test on relaxed server**
   - Try new delay for a few hours
   - Monitor chat for AC warnings

3. **If flagged, increase delay**
   - `100ms` → `150ms` → `200ms` → `250ms`
   - Test after each increment

4. **Final verification**
   - Play normally for a full session
   - Check for ban message next day

---

## Summary

| Step | Action |
|------|--------|
| 1 | Locate config: `~/.minecraft/config/inv-totem-config.json` |
| 2 | Edit `swapDelayMs` based on server type |
| 3 | Save (JSON must be valid) |
| 4 | Restart Minecraft |
| 5 | Test on your server |
| 6 | Adjust if needed and repeat |

**Golden rule**: Start high (150-200ms), then decrease if it's too slow.

---

*Need help? Check the main [README.md](README.md) for troubleshooting.*

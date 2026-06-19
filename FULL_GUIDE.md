# ArtMapColorAssistant Full Guide

This guide explains installation, painting modes, calibration, batches, troubleshooting, and every command implemented by ArtMapColorAssistant.

For the short installation and quick start, return to the **[README](README.md)**.

## Contents

1. [What the mod does](#what-the-mod-does)
2. [Requirements and installation](#requirements-and-installation)
3. [Folders and image preparation](#folders-and-image-preparation)
4. [Painting modes](#painting-modes)
5. [Using the GUI](#using-the-gui)
6. [Paint speed](#paint-speed)
7. [Calibration](#calibration)
8. [Smart painting](#smart-painting)
9. [Batch and post-paint automation](#batch-and-post-paint-automation)
10. [Complete command list](#complete-command-list)
11. [Android and PojavLauncher](#android-and-pojavlauncher)
12. [Troubleshooting](#troubleshooting)

## What the Mod Does

ArtMapColorAssistant is a client-only Fabric mod made for servers using the Bukkit ArtMap plugin.
It works with ArtMap's easel, canvas, and paint-item system.

The mod:

- Loads a PNG from your Minecraft folder.
- Resizes nothing automatically: the PNG must match the configured canvas size, which is 32×32 by default.
- Matches each image pixel to a configured ArtMap paint item available in your inventory.
- Follows pixels from left to right, one row at a time, from the top-left to the bottom-right.
- Can swap paint items for manual painting.
- Can aim and click automatically after calibration.
- Can prepare a faster Smart plan using a base coat and connected paint paths.
- Can process numbered PNG files as a batch.

The mod does not edit server data and does not bypass ArtMap. Auto and Smart modes send normal client actions at a controlled speed.

## Requirements and Installation

You need:

- Minecraft Java Edition 1.21.1
- Fabric Loader
- Fabric API
- Java 21
- An ArtMapColorAssistant release JAR

Install it:

1. Download the JAR from the [latest published GitHub release](https://github.com/volcanorc/paint-mod/releases/latest).
2. Install Fabric Loader for Minecraft 1.21.1.
3. Put Fabric API in the Minecraft `mods` folder.
4. Put the downloaded `artmap-color-assistant-*.jar` in the same `mods` folder.
5. Start the Fabric profile.
6. Run `#painting paths` in Minecraft chat to see the folders used by the current launcher.

## Folders and Image Preparation

The normal desktop locations are:

```text
.minecraft/config/artmap_color_assistant.json
.minecraft/artmap_color_assistant/imports/
.minecraft/artmap_color_assistant/calibrations/
```

Use `#painting paths` instead of guessing. Launchers and Android installations can use different game folders.

Prepare an image:

1. Export it as PNG.
2. Make it exactly 32×32 unless you intentionally changed `canvasWidth` and `canvasHeight` in the config.
3. Use a simple filename such as `dragon.png`.
4. Copy it to the imports folder.
5. Put the required ArtMap paint items in hotbar slots 0–8 or main-inventory slots 9–35.

For a numbered batch, name files `1.png`, `2.png`, `3.png`, and so on.

Transparent pixels use the configured transparent-pixel behavior. Smart base coating refuses images containing transparent `SKIP` pixels because filling the whole canvas would overwrite them.

## Painting Modes

Choose a mode in `#painting gui` or with `#painting set manual|auto|smart`.

### Manual

Manual mode handles image analysis and item selection, but you paint each pixel yourself.

1. Load the PNG.
2. The mod selects the item for the current pixel.
3. Aim at the matching canvas pixel and click it.
4. The mod advances to the next pixel and prepares its item.

Manual mode moves left-to-right across a row, then starts the next row. It does not move your camera or click automatically.

### Auto

Auto mode selects items, aims, clicks, and advances automatically.
It requires usable calibration and pauses if it reaches a pixel that cannot be aimed at safely.

Classic Auto can drag through adjacent same-color pixels in one horizontal row. It never crosses into another row during one drag.

### Smart

Smart mode prepares the whole route before it starts:

1. Find the most common nonblank target color.
2. Verify the dominant paint item and an exact empty bucket in the offhand.
3. Perform one guarded bucket base coat.
4. Remove pixels already completed by that base coat from the remaining plan.
5. Paint connected same-color areas as exact-calibration drag trails.
6. Use normal clicks for isolated pixels.

Smart mode performs extra preflight checks. If Smart cannot start from the normal single-image command, the existing start flow can fall back to Classic Auto. Smart batches pause instead of silently changing mode.

## Using the GUI

Open the dashboard:

```text
#painting gui
```

The dashboard provides:

- Manual, Auto, and Smart mode selection.
- `−` and `+` controls for normal auto-paint tick speed.
- Auto Drag toggle.
- Smart Basecoat toggle.
- Painting Bucket toggle.
- Post-paint automation toggle.
- Read-only **Storage Used** status showing the selected `Player Vault N`.
- Rename-point status and **Record New** action.
- **Paint Now**, which opens the imported-image list.

The image screen shows PNG files from the imports folder. Scroll the list and press **Paint** beside a file to load it and start the selected mode.

The top of the image screen also has batch fields:

```text
First | Last | Name suffix | Run Batch Painting
```

Example values `1 | 4 | Dragon` are equivalent to:

```text
#painting batch start 1 4 Dragon
```

Batch painting is available only in Auto or Smart mode.

## Paint Speed

Minecraft normally runs at 20 ticks per second.

| Delay | Approximate time per normal pixel |
| --- | --- |
| 5 ticks | 0.25 seconds |
| 10 ticks | 0.5 seconds |
| 20 ticks | 1 second |
| 40 ticks | 2 seconds |

The default and minimum delay is 5 ticks. A lower number is faster; a higher number is slower.

```text
#painting auto speed 20
```

Smart connected trails use their own fixed three-tick waypoint timing. Changing normal Auto speed does not change that Smart-only waypoint timing.

## Calibration

Stay seated at the same easel position while recording and using a calibration. Moving the player, changing seat position, or changing the view setup can make saved aim directions inaccurate.

### Four-Corner Calibration

Four-corner calibration lets Classic Auto calculate the positions between the four canvas corners.

1. Aim at the center of the top-left canvas pixel and run `#painting cal top-left`.
2. Aim at the center of the top-right pixel and run `#painting cal top-right`.
3. Aim at the center of the bottom-left pixel and run `#painting cal bottom-left`.
4. Aim at the center of the bottom-right pixel and run `#painting cal bottom-right`.
5. Run `#painting cal status`.
6. Test with `#painting cal test 0 0` and `#painting cal test 31 31`.

If either test misses the correct pixel, clear or record the corners again before starting Auto.

### Exact Per-Pixel Calibration

Exact calibration records one camera direction for every canvas pixel. It is the safest calibration for Smart paths.

1. Sit at the easel and do not move.
2. Run `#painting calibrate start <name>`, for example `#painting calibrate start easel1`.
3. Right-click the center of each canvas pixel in order.
4. Record from top-left to top-right, then continue on the next row.
5. Run `#painting calibrate status` to check progress.
6. Save it with `#painting calibrate save easel1`.
7. Load it later with `#painting usecalibration easel1`.
8. Test recorded points with `#painting cal test <x> <y>`.

A partial exact calibration is allowed. Auto stops at the first required pixel that has no exact point instead of guessing.

### Portable Calibration

Portable mode allows an exact calibration copied from another device or launcher to ignore the saved eye-position warning.
It does not make an incorrect seat or camera setup safe.

1. Copy the calibration JSON to the calibrations folder.
2. Load it with `#painting usecalibration <name>`.
3. Run `#painting calibration portable on` if the movement warning blocks it.
4. Test `0 0`, `31 31`, and several middle points before painting.

Turn portable mode off when it is not needed.

## Smart Painting

Smart mode normally expects:

- A fully trusted image/canvas state.
- No transparent `SKIP` pixels.
- Exact calibration for bucket anchors and connected drag pixels.
- The dominant paint item in the usable inventory.
- An exact `minecraft:bucket` in the offhand.
- Smart Basecoat and Painting Bucket enabled.

The initial bucket sequence selects the dominant item, checks both hands, swaps the color and bucket, aims at guarded center anchors, sends one fill click, restores the hands, and verifies the result. Pause/resume protection prevents the same fill click from being sent twice.

Useful checks:

```text
#painting smart preview
#painting smart status
#painting bucket status
```

Smart bucket delay commands are advanced controls. Increase delays if the server or connection responds slowly.

## Batch and Post-Paint Automation

### Guided Batch

Batch mode loads numbered PNGs:

```text
#painting batch start <first> <last> <nameSuffix>
```

Example:

```text
#painting batch start 1 3 Dragon
```

This loads `1.png`, then `2.png`, then `3.png`. The suggested saved names are `1 Dragon`, `2 Dragon`, and `3 Dragon`.

With post-paint automation off:

1. Let the current image finish.
2. Save and store the finished ArtMap canvas manually.
3. Place the next blank canvas and enter the easel.
4. Run `#painting batch continue`.

### Guarded Post-Paint Automation

Post-paint automation is optional. It can attempt the save, rename, storage, next-canvas, and next-image steps. It pauses when an expected item, screen, or recorded point cannot be verified.

Default hotbar expectations:

- Slot 3: ArtMap save/redstone item
- Slot 1: space for the finished canvas
- Slot 2: next blank canvas

Record the save-GUI point:

1. Run `#painting rename click`.
2. Open the ArtMap save GUI.
3. Click the exact save/done position.
4. Enable the workflow with `#painting postpaint on`.

The recorded point is saved in `.minecraft/config/artmap_color_assistant.json`.

Finished canvases are stored in Player Vault 2 by default using `/pv 2`. Choose another vault from 1 through 40 with `#painting pv 3` or the compact form `#painting pv3`. The setting is saved in `.minecraft/config/artmap_color_assistant.json` and stays selected after Minecraft or the computer restarts. The dashboard shows the current vault in its **Storage Used** row.

The storage sequence stays guarded: finish canvas, rename it, open the selected `/pv N`, wait for the Player Vault screen, then quick-move the finished canvas. If the screen does not open or the item remains in the hotbar, the workflow stops and asks you to store it manually.

`#painting pv2` selects Player Vault 2. `#painting pv2 click` and `#painting pv2 clear` are separate legacy click-recorder controls; normal Player Vault transfer is automatic and does not need that recorded point.

## Complete Command List

Both `#painting` and `#paint` work as prefixes. For example, `#paint stop` is the same as `#painting stop`.

All messages whose trimmed text begins with `#` are intercepted by the mod and never sent to server chat. Unknown hash commands show a local warning and clickable ArtMap command help.

### General and Session Commands

- `#painting help` — Show clickable command help in Minecraft chat.
- `#painting gui` — Open the client-side dashboard and image picker.
- `#painting paths` — Print the game, config, imports, and calibration folders.
- `#painting <filename.png>` — Load an imported image, for example `#painting dragon.png`.
- `#painting dryrun <filename.png>` — Analyze matching and inventory colors without starting a session.
- `#painting set manual` — Select Manual mode.
- `#painting set auto` — Select Classic Auto mode.
- `#painting set smart` — Select Smart mode.
- `#painting full` — Start the currently selected mode from the current session position.
- `#painting status` — Show the current painting, session, calibration, and batch state.
- `#painting stop` — Stop the batch, painters, and current session.
- `#painting pause` — Pause the current session and Smart painter safely.
- `#painting resume` — Resume a paused session/painter when its state can be verified.
- `#painting back` — Move the session back one pixel for correction.
- `#painting skip` — Skip the current pixel.
- `#painting goto <index>` — Jump to a flat pixel index.
- `#painting goto <x> <y>` — Jump to canvas coordinates.
- `#painting pos <x> <y>` — Alias for coordinate-based `goto`.
- `#painting confirm on` — Require the configured advance key instead of mouse-click advancement.
- `#painting confirm off` — Allow normal accepted mouse clicks to advance Manual mode.
- `#painting reload` — Reload the JSON configuration and refresh runtime settings.

### Auto Commands

- `#painting auto start` — Start the currently selected Manual, Auto, or Smart behavior.
- `#painting auto full` — Same start action as `auto start`.
- `#painting auto stop` — Stop Auto and Smart painting.
- `#painting auto pause` — Pause the running automatic painter.
- `#painting auto resume` — Resume the automatic painter.
- `#painting auto status` — Show Classic Auto, Smart, and active calibration status.
- `#painting auto speed <ticks>` — Set the normal Auto delay; minimum 5 ticks by default.
- `#painting auto drag on` — Enable same-color Classic Auto row dragging.
- `#painting auto drag off` — Disable Classic Auto row dragging.
- `#painting auto drag status` — Show the current drag state.

### Smart Commands

- `#painting smart on` — Select and enable Smart mode.
- `#painting smart off` — Disable Smart mode and leave Classic Auto available.
- `#painting smart status` — Show Smart phase, queue, trust, hand, and blocking information.
- `#painting smart preview` — Prepare and summarize the Smart route without painting.
- `#painting smart basecoat on` — Enable the dominant-color base coat.
- `#painting smart basecoat off` — Disable the Smart base coat.
- `#painting smart threshold <number>` — Set the stored minimum Smart bucket-region threshold.
- `#painting smart dragthreshold <number>` — Set the stored minimum Smart drag threshold.

### Bucket Commands

Bucket settings can be changed while Painting Type is Smart.

- `#painting bucket on` — Enable the guarded initial bucket base coat.
- `#painting bucket off` — Disable it and invalidate an active Smart bucket plan.
- `#painting bucket status` — Show enabled state, delays, and offhand-bucket check.
- `#painting bucket preview` — Show the prepared Smart preview.
- `#painting bucket selectdelay <ticks>` — Set the dominant-color selection delay.
- `#painting bucket swapdelay <ticks>` — Set the hand-swap verification delay.
- `#painting bucket aimdelay <ticks>` — Set the fill-anchor settling delay.
- `#painting bucket afterdelay <ticks>` — Set the wait after the fill click.
- `#painting bucket restoredelay <ticks>` — Set the hand-restoration verification delay.

Default bucket delays are 10, 20, 16, 24, and 10 ticks in that order.

### Batch Commands

- `#painting batch start <first> <last> <nameSuffix>` — Start numbered images such as `1.png` through `20.png`.
- `#painting batch continue` — Continue after manually preparing the next blank canvas.
- `#painting batch status` — Show the current file, range, wait state, and post-paint state.
- `#painting batch stop` — Stop and clear the active batch queue.

### Post-Paint and Recorded-Point Commands

- `#painting postpaint on` — Enable guarded post-paint automation.
- `#painting postpaint off` — Disable it.
- `#painting postpaint status` — Show slots, vault command, transfer mode, and recorded-point state.
- `#painting rename click` — Arm recording for the ArtMap save/done GUI point.
- `#painting rename clear` — Delete the recorded rename point.
- `#painting pv <1-40>` — Permanently choose where finished canvases are stored, for example `#painting pv 20`.
- `#painting pv1` through `#painting pv40` — Compact form of the same storage command, for example `#painting pv20`.
- `#painting pv` — Incomplete by itself; the mod shows the accepted forms and valid 1-40 range without changing the saved vault.
- `#painting pv2` — Select Player Vault 2. With no extra word, this is a storage selection command.
- `#painting pv2 click` — Arm the legacy `/pv 2` click recorder.
- `#painting pv2 clear` — Delete the legacy Player Vault 2 click point.

Invalid vault values such as `0`, `41`, negative numbers, or words never replace the current saved selection.

### Four-Corner Calibration Commands

- `#painting cal top-left` — Record the current camera direction for the top-left pixel.
- `#painting cal top-right` — Record the top-right direction.
- `#painting cal bottom-left` — Record the bottom-left direction.
- `#painting cal bottom-right` — Record the bottom-right direction.
- `#painting cal status` — Show loaded and recorded calibration state.
- `#painting cal clear` — Clear calibration from memory.
- `#painting cal test <x> <y>` — Aim at a calculated or exact pixel without clicking.

### Exact Calibration Commands

- `#painting calibrate start <name>` — Start a fresh unsaved exact calibration.
- `#painting calibrate continue <name>` — Load saved progress and continue recording.
- `#painting calibrate resume <name>` — Reload saved progress and continue recording.
- `#painting calibrate save <name>` — Save the current in-memory points.
- `#painting calibrate stop` — Stop recording without automatically saving.
- `#painting calibrate undo` — Remove the most recently recorded point.
- `#painting calibrate reset <name>` — Delete the saved calibration and clear its recording.
- `#painting calibrate status` — Show exact-calibration progress.
- `#painting calibrate clear` — Clear calibration from memory.
- `#painting usecalibration <name>` — Load and select a saved exact calibration.

### Portable Calibration Commands

- `#painting calibration portable on` — Allow a transferred exact calibration despite saved eye-position differences.
- `#painting calibration portable off` — Require the original saved eye-position check.
- `#painting calibration portable status` — Show the current portable-mode setting.

### Palette and Image Diagnostics

- `#painting palette status` — Show configured, usable, inventory, tool, and matching color counts.
- `#painting palette reds` — List configured red/pink/maroon colors currently available in inventory.
- `#painting palette why <hex>` — Explain the nearest configured colors to a value such as `#AA2222`.
- `#painting dryrun <filename.png>` — Show matched item counts and warnings without beginning a session.

### Android and Pojav Commands

- `#painting android status` — Show Minecraft, Java, OS, Fabric API, paths, and calibration mode.
- `#painting android testinput` — Show scaled cursor/touch coordinates and GUI-recorder state.

## Android and PojavLauncher

This is a normal Fabric Java mod, not an Android APK. Use a Java launcher capable of Minecraft Java 1.21.1, Fabric, Fabric API, and Java 21.

1. Install the mod and Fabric API in that launcher's `mods` folder.
2. Launch once and run `#painting paths`.
3. Copy PNG files into the printed imports folder.
4. Copy calibration JSON files into the printed calibrations folder if needed.
5. Load a calibration with `#painting usecalibration <name>`.
6. Test several points before painting.
7. Use portable calibration only when necessary.
8. Re-record rename click points on the device because GUI scale and touch layout differ.

## Troubleshooting

### The image is not listed

- Run `#painting paths` and use the printed imports folder.
- Confirm it is a real `.png` file, not a folder or renamed JPEG.
- Press **Refresh** in the image picker.

### The PNG has the wrong size

The default canvas is exactly 32×32. Resize/export the image to 32×32, or intentionally change both canvas dimensions in the config before loading it.

### A paint item is missing

Put the required item in hotbar slots 0–8 or main-inventory slots 9–35. The mod does not use armor slots, open containers, or creative-menu items as its normal paint inventory.

### Auto aims at the wrong place

- Stop immediately.
- Confirm you are seated in the same position used for calibration.
- Run `#painting cal test 0 0`, `#painting cal test 31 31`, and middle-point tests.
- Record the calibration again if the tests miss.

### Auto stops at a calibration limit

The next pixel has no usable calibration point. Continue the exact calibration, load a complete one, or use a verified four-corner calibration for Classic Auto.

### Smart will not start

Run `#painting smart preview` and `#painting bucket status`. Check for transparent SKIP pixels, missing exact points, a missing dominant paint item, or an offhand item that is not an exact empty bucket.

### Painting pauses when a menu opens

Inventory and container screens can make automated clicks unsafe. Close the screen, confirm items and hand state, then resume only when the status is correct.

### Batch pauses between images

With post-paint automation disabled, this is expected. Save/store the finished canvas, prepare the next blank canvas, and run `#painting batch continue`.

If post-paint automation failed, read its error, complete or repair the missing step, then continue.

### Colors look wrong

- Run `#painting palette status`.
- Use `#painting palette why <hex>` for a problem color.
- Use `#painting palette reds` for red-heavy artwork.
- Run `#painting dryrun <filename.png>` before painting.
- Server ArtMap palettes can differ; update the configured colors or server override values when necessary.

### An invalid `#` message was blocked

This is intentional. Every leading-hash message is kept client-side so command typos cannot appear in server chat. Use the clickable help that appears or run `#painting help`.

## Safety and Limits

- Follow the server's automation rules.
- The mod depends on client ticks, network response, correct items, and correct calibration.
- Background or alt-tabbed clicking is best-effort because Minecraft and the operating system must continue processing ticks.
- Use `#painting stop` or the emergency stop key immediately if the aim, item, or canvas state looks wrong.
- The default emergency stop key is `O` and can be changed in Minecraft Controls.

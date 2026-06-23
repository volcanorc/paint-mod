# ArtMapColorAssistant

ArtMapColorAssistant is a client-side Fabric mod for painting imported pixel art on Bukkit ArtMap easels.
It reads a 32×32 PNG, matches its colors to the ArtMap paint items in your inventory, and helps paint the canvas one image at a time or as a numbered batch.

The mod performs normal Minecraft client actions. It does not modify the server or bypass ArtMap.

## Requirements

- Minecraft Java Edition 1.21.1
- Fabric Loader 0.15.11 or newer
- Fabric API 0.101.2+1.21.1 or newer for Minecraft 1.21.1
- Java 21 or newer
- A server using the Bukkit ArtMap plugin with its easel and canvas system

## Installation

1. Download the ArtMapColorAssistant JAR from the [latest published release](https://github.com/volcanorc/paint-mod/releases/latest).
2. Install Fabric Loader for Minecraft 1.21.1.
3. Put Fabric API and the ArtMapColorAssistant JAR in your Minecraft `mods` folder.
4. Start Minecraft with the Fabric profile.
5. Join a world or server and run `#painting paths` in chat.
6. The command prints the exact image-import and calibration folders used by your launcher.

The mod supports the full tested 1.21.1 range above. The latest compatible 1.21.1 Fabric Loader and Fabric API are recommended, but they are no longer unnecessarily required.

## Prepare an Image

1. Resize or export the picture as an exact 32×32 PNG.
2. Give it a simple name such as `dragon.png`.
3. Put it in the PNG imports folder shown by `#painting paths`.
4. For batches, use numbered names such as `1.png`, `2.png`, and `3.png`.

The default imports folder on desktop is:

```text
.minecraft/artmap_color_assistant/imports/
```

## Painting Modes

### Manual

Manual mode analyzes the image and selects the correct ArtMap paint item for each pixel.
You click the canvas yourself. After an accepted click, the mod advances from left to right across each row and automatically prepares the next color.

### Auto

Auto mode selects the paint item, aims at the calibrated pixel, and clicks automatically.
It follows the image from the top-left pixel to the bottom-right pixel and can drag across same-color row sections.

### Smart

Smart mode prepares an efficient plan before painting.
It can apply one bucket base coat using the most common nonblank color, then paint the remaining connected colors as calibrated drag paths.
Smart mode requires a suitable exact calibration, the needed paint items, and an empty bucket in the offhand.

## Quick Start

1. Join the ArtMap server and sit at the easel with a blank canvas ready.
2. Keep the paint items required by the image in your hotbar or main inventory.
3. Run `#painting gui`.
4. Select Manual, Auto, or Smart.
5. Select the imported PNG with **Paint Now**.
6. Auto and Smart modes require calibration before they can aim safely.
7. Press the emergency stop key, `O` by default, if anything looks wrong.

You can change the emergency stop key under:

```text
Options > Controls > Key Binds > ArtMap Color Assistant > Stop Painting
```

## Paint Speed

Minecraft normally runs at 20 ticks per second.
The default and minimum painting delay is 5 ticks, or about 0.25 seconds per normal auto-painted pixel.

- Lower tick value = faster painting
- Higher tick value = slower painting
- `5` ticks ≈ 0.25 seconds
- `20` ticks ≈ 1 second
- `40` ticks ≈ 2 seconds

Set it with:

```text
#painting auto speed 5
```

Use a speed allowed by your server rules and connection quality.

## Main Commands

| Command | Purpose |
| --- | --- |
| `#painting gui` | Open the user-friendly painting controls and image picker. |
| `#painting paths` | Show the imports, config, and calibration folders. |
| `#painting set manual` | Select assisted manual painting. |
| `#painting set auto` | Select classic automatic painting. |
| `#painting set smart` | Select Smart painting. |
| `#painting <file.png>` | Load one PNG, for example `#painting dragon.png`. |
| `#painting auto start` | Start the selected painting mode after loading an image. |
| `#painting auto speed <ticks>` | Change the normal auto-paint delay. |
| `#painting status` | Show the current session and painting state. |
| `#painting stop` | Stop the current painting session and automation. |
| `#painting batch start 1 10 Dragon` | Paint numbered files `1.png` through `10.png`. |
| `#painting pv 3` | Permanently store finished canvases in Player Vault 3 (valid range: 1-40). |
| `#painting help` | Show clickable command help inside Minecraft. |

`#paint` is a shorter alias for `#painting`.
All messages beginning with `#` are kept client-side. Invalid hash commands are blocked instead of being sent to server chat.

While typing `#painting` or `#paint`, the foreground suggestion panel behaves like normal Minecraft command completion. Press **Tab** to cycle forward, **Shift+Tab** to cycle backward, use **Up/Down** to move through choices, or click a row. Placeholders such as `<ticks>` explain what to type but are never inserted as literal text.

Finished canvases use Player Vault 2 by default (`/pv 2`). Change it with either `#painting pv 3` or the compact form `#painting pv3`; the choice is saved in the mod config and remains after restarting Minecraft.

## Calibration

Manual mode does not need camera calibration.
Auto mode can use four-corner or exact per-pixel calibration.
Smart connected paths require exact calibrated points so the mod does not guess where to aim.

Stay seated in the same easel position while recording and using calibration.
See the full guide for the exact steps.

## Batch Painting

Batch mode uses numbered PNG files such as `1.png`, `2.png`, and `3.png`.
It is available in Auto and Smart modes.
Without post-paint automation, the mod pauses between canvases so you can save the finished art, store it, place the next blank canvas, and continue safely.

## Safety

- Check the server rules before using automatic painting.
- Stay seated and avoid moving the camera during calibrated Auto or Smart painting.
- Keep required items in the hotbar or main inventory.
- The mod pauses when an item, calibration point, hand state, or expected screen cannot be verified.
- Use `#painting stop` or the emergency stop key whenever needed.

## Full Documentation

For every command, calibration instructions, Smart setup, batches, post-paint automation, Android/Pojav, palette tools, and troubleshooting, read the **[Full Guide](FULL_GUIDE.md)**.

## Build from Source

Developers need Java 21 and Gradle 8.14.3:

```text
git clone https://github.com/volcanorc/paint-mod.git
cd paint-mod
git switch SmartFeature
gradle test
gradle build
```

The current source build is written to `build/libs/artmap-color-assistant-1.0.2.jar`.

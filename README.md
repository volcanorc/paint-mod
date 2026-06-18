# ArtMapColorAssistant

ArtMapColorAssistant is a client-only Fabric mod for Minecraft 1.21.1. It helps with ArtMap painting by reading a configured-size PNG, switching the held ArtMap color item, and optionally auto-aiming/clicking after four-corner canvas calibration.

## Install

1. Install Fabric Loader for Minecraft 1.21.1.
2. Install Fabric API.
3. Build this project and put the jar from `build/libs/` into `.minecraft/mods/`.
4. Start the Fabric client once to generate `.minecraft/config/artmap_color_assistant.json` and `.minecraft/artmap_color_assistant/imports/`.

## Use

1. Export your image as a PNG matching `canvasWidth` and `canvasHeight` in the config. The default is 32x32.
2. Put it in `.minecraft/artmap_color_assistant/imports/`.
3. Join an ArtMap server and enter ArtMap painting mode manually.
4. Run `#painting image.png`.
5. For manual mode, click the ArtMap canvas manually from top-left to right, row by row.
6. For full auto mode, calibrate all four canvas corners first, test aim, then run `#painting auto start`.

Commands beginning with `#painting` or `#paint` are handled client-side and canceled before server chat.

The default emergency stop key is `O`. You can change it in Minecraft under `Options > Controls > Key Binds > ArtMap Color Assistant > Stop Painting`.

## Commands

- `#painting <filename.png>` starts a session.
- `#painting help` shows clickable colored help.
- `#painting gui` opens a client-only control screen.
- `#painting paths` shows the exact game/config/import/calibration folders used by the current launcher.
- `#painting android status` shows Android/Pojav-friendly runtime and folder diagnostics.
- `#painting android testinput` checks cursor/touch coordinate capture and GUI recorder state.
- `#painting dryrun <filename.png>` analyzes without starting.
- `#painting palette status` shows configured, usable, inventory, tool, and match-mode color counts.
- `#painting palette reds` lists red/pink/maroon configured ArtMap colors currently found in your inventory.
- `#painting palette why <hex>` explains the nearest configured colors for an RGB value, such as `#painting palette why #AA2222`.
- `#painting batch start <first> <last> <nameSuffix>` starts a numbered queue, such as `#painting batch start 1 20 Dragon`.
- `#painting batch continue` starts the next numbered image after you manually save/store/place the next canvas.
- `#painting batch status` shows queue progress.
- `#painting batch stop` clears the queue.
- `#painting postpaint on|off|status` controls guarded post-paint save/vault/next-canvas automation.
- `#painting rename click` records the ArtMap save GUI click point.
- `#painting rename clear` clears the recorded save GUI click point.
- `#painting pv2 click` records the `/pv 2` vault shift-click point.
- `#painting pv2 clear` clears the recorded vault click point.
- `#painting stop`, `pause`, `resume`, `back`, `skip`, `status`, `reload`.
- `#painting goto <index>` or `#painting goto <x> <y>`.
- `#painting pos <x> <y>` is an alias for x/y goto.
- `#painting confirm on|off` toggles confirm mode. When enabled, the advance keybind is required instead of mouse-click advancement.
- `#painting cal top-left`, `top-right`, `bottom-left`, `bottom-right` records the current camera direction as a canvas calibration point.
- `#painting cal status` shows calibration state.
- `#painting cal clear` clears calibration.
- `#painting cal test <x> <y>` rotates to a calibrated pixel without clicking.
- `#painting calibrate start <name>` starts a fresh unsaved exact calibration.
- `#painting calibrate continue <name>` or `resume <name>` loads the saved file and continues from the first missing pixel.
- `#painting calibrate save <name>` writes the current in-memory calibration to disk.
- `#painting calibrate stop` stops recording without saving.
- `#painting calibrate reset <name>` deletes the saved calibration file and clears that in-memory recording.
- `#painting calibrate status`, `clear`.
- `#painting calibration portable on|off|status` toggles transferred exact-calibration mode.
- `#painting usecalibration <name>` loads a saved exact calibration file, such as `#painting usecalibration 1`.
- `#painting full`, `#painting auto full`, and `#painting auto start` start opt-in automated painting from the current session index.
- `#painting auto stop`, `pause`, `resume`, `status`.
- `#painting auto speed <ticks>` sets the delay between auto-painted pixels.
- `#painting auto drag on|off|status` toggles same-color row dragging.
- `#painting smart preview` prepares and summarizes the dominant base coat and connected drag plan without painting.
- `#painting bucket status` shows the guarded single initial bucket sequence and its delays.
- `#painting bucket selectdelay|swapdelay|aimdelay|afterdelay|restoredelay <ticks>` adjusts each bucket stage.

`#paint stop`, `#painting stop`, `#painting auto stop`, the GUI stop button, and the Stop Painting keybind are the intended stop controls. Opening chat or pressing Esc does not intentionally stop auto painting. Auto paint refreshes the crosshair target before sending the normal client click interaction, but background clicking while alt-tabbed is still best-effort and depends on Minecraft and the operating system continuing client ticks.

When `autoLockCameraDuringAuto` is enabled, auto paint re-aims at the active calibrated target every client tick while it is running. Moving the mouse during auto paint, post-click delay, or drag mode should snap back to the current target. Pause, stop, emergency stop, or a safety failure releases the lock.

## Full Auto Calibration

Full auto requires four-corner calibration. Enter ArtMap painting mode and keep the player seated/still while calibrating and painting.

1. Aim at the center of the top-left pixel and run `#painting cal top-left`.
2. Aim at the center of the top-right pixel and run `#painting cal top-right`.
3. Aim at the center of the bottom-left pixel and run `#painting cal bottom-left`.
4. Aim at the center of the bottom-right pixel and run `#painting cal bottom-right`.
5. Run `#painting cal status`.
6. Run `#painting cal test 0 0` and `#painting cal test 31 31` on a default 32x32 canvas to verify the camera points where expected.
7. Start or resume the image session, set speed if needed, then run `#painting auto start`.

The mod computes each pixel in image order: top-left to top-right, then the next row, ending at bottom-right.

## Exact Per-Pixel Calibration

Exact calibration records the camera direction for every pixel you right-click. It saves to `.minecraft/artmap_color_assistant/calibrations/<name>.json` and can be reused after restarting Minecraft.

1. Enter ArtMap painting mode and stay in the same seat/view.
2. Run `#painting calibrate start 1`.
3. Right-click the center of each canvas pixel in order: top-left to top-right, then the next row, until bottom-right.
4. The mod records clicks in memory and tells you the next `x/y`. It does not save automatically.
5. Run `#painting calibrate status` to check progress.
6. Save manually with `#painting calibrate save 1`.
7. If you need to stop recording without saving, run `#painting calibrate stop`.
8. To continue from the saved file later, run `#painting calibrate continue 1`.
9. To discard unsaved clicks and return to the last saved file, run `#painting calibrate resume 1`.
10. To delete a saved calibration, run `#painting calibrate reset 1`.
11. Later, load it with `#painting usecalibration 1`.
12. Verify recorded pixels with `#painting cal test <x> <y>`.
13. Start painting with `#painting auto start`.

Exact per-pixel calibration is used before four-corner interpolation. A partial exact calibration is allowed: auto paint follows the saved direction for each recorded pixel and stops at the first pixel without a recorded point, reporting how many calibrated clicks were completed.

The GUI shows the current selected calibration and includes a calibration picker. The picker lists saved calibration files, shows their progress, lets you use/continue/reset them, and can create a new calibration name. The selected calibration name is stored in config as `selectedCalibrationName`, so it remains the default after restarting Minecraft.

For transferred exact calibrations, such as moving a 1024-point calibration from desktop Java to Android/Pojav, use `#painting calibration portable on` only after loading the calibration and verifying aim with `#painting cal test 0 0` and `#painting cal test 31 31`. Portable mode skips the saved eye-position movement warning for exact calibration yaw/pitch samples, but wrong seat/view position can still aim incorrectly.

Config field: `portableExactCalibrationMode`, default `false`.

## Auto Paint Speed

Minecraft normally runs at 20 ticks per second, so 20 ticks is about 1 second.

The default auto-paint speed is 20 ticks, which means 1 pixel per second. The default minimum is 5 ticks, which means 4 clicks per second. `#painting auto speed 5` means 0.25 seconds per click, `#painting auto speed 20` means 1 click per second, and `#painting auto speed 40` means 1 click every 2 seconds.

A 32x32 canvas has 1024 pixels, so a full painting takes about 17 minutes and 4 seconds plus item switching and server/client lag.

Do not use faster automation unless server rules allow it.

## Smart Initial Base Coat And Connected Drag

Smart mode prepares one bounded plan when an image starts. It finds the most frequent nonblank color, equips that item, verifies an exact empty bucket in the offhand, swaps hands, and performs one guarded left click near the calibrated canvas center. Nine fixed shuffled near-center anchors are used three at a time and loop after three images. The fill-click guard survives pause/resume so a completed bucket click cannot be sent twice. The mod then verifies that the bucket and color item are restored to their original hands before continuing.

The base coat is the only bucket action. Once it succeeds, every target pixel using the dominant color is considered complete and is excluded from later painting. Images containing transparent `SKIP` pixels do not start in smart mode because a whole-canvas fill could not preserve those pixels.

Remaining same-color pixels that touch by an edge are painted as calibrated drag trails. Trails may move horizontally, vertically, and around turns; diagonal-only contact is not treated as connected. Branched trails may safely revisit a junction with the same color. Manual clicks are reserved for isolated one-pixel components.

Smart connected trails dwell for exactly 3 ticks (about 150 ms at 20 TPS) at each calibrated waypoint. This Smart-only timing does not change Classic Auto row dragging, the 2-tick starting hold, the 1-tick ending hold, or the 5-tick post-trail delay.

Smart bucket defaults in config:

- `bucketColorSelectDelayTicks`: `10`
- `bucketHandSwapDelayTicks`: `20`
- `bucketFillAimSettleTicks`: `16`
- `bucketPostFillDelayTicks`: `24`
- `bucketHandRestoreDelayTicks`: `10`

If an item, hand state, or exact calibration cannot be verified, smart mode pauses before changing the canvas rather than falling back to classic auto.

## Classic Auto Same-Color Row Drag

Auto drag is enabled by default. When two or more adjacent pixels in the same row use the same ArtMap item and every pixel in that run has exact calibration, the mod holds right-click, moves through those calibrated pixel centers, and releases at the end of the row run.

Drag mode never crosses into the next row, even if the next row has the same color. It also stops the run on a color change, transparent skip, missing calibration point, missing item, or row end.

Use these commands:

- `#painting auto drag on`
- `#painting auto drag off`
- `#painting auto drag status`

Drag settings in config:

- `autoDragSameColorRuns`: default `true`
- `autoDragMinRunLength`: default `2`
- `autoDragPixelTicks`: default `5`
- `autoDragRequireExactCalibration`: default `true`
- `autoDragStartHoldTicks`: default `2`
- `autoDragEndHoldTicks`: default `1`

## Guided Batch Painting

Batch mode paints numbered files from `.minecraft/artmap_color_assistant/imports/` and pauses between canvases so you can handle server-specific save, vault, and easel steps manually.

Example:

1. Put `1.png`, `2.png`, and `3.png` in the imports folder.
2. Enter ArtMap painting mode with a blank canvas ready.
3. Run `#painting batch start 1 3 Dragon`.
4. When `1.png` finishes, save the canvas as `1 Dragon`, store it, place the next blank canvas, and enter painting mode again.
5. Run `#painting batch continue`.
6. Repeat until the batch reports complete.

Batch config:

- `batchAutoStartAfterContinue`: default `true`
- `batchDefaultSpeedTicks`: default `5`
- `batchEnableDrag`: default `true`

With post-paint automation disabled, batch mode does not type `/pv`, rename canvases, click server GUI slots, or place canvases. Those steps stay manual because server GUI layouts and rules vary.

### Optional Guarded Post-Paint Automation

Post-paint automation is disabled by default. When enabled, batch mode tries to save, rename, vault-store, place the next blank canvas, re-enter painting mode, and start the next PNG. It pauses instead of clicking blindly if an expected item, GUI, or recorded point is missing.

Setup:

1. Put the ArtMap save/redstone item in hotbar slot 3.
2. Keep hotbar slot 1 empty for the finished saved canvas.
3. Put the blank canvas in hotbar slot 2.
4. Run `#painting rename click`, open the ArtMap save GUI, and click the save/done item location.
5. Run `#painting pv2 click`, open `/pv 2`, and click the exact finished-item location you want the mod to shift-click during automation.
6. Run `#painting postpaint on`.
7. Start batch mode normally, such as `#painting batch start 1 20 Dragon`.

Post-paint config:

- `postPaintAutomationEnabled`: default `false`
- `postPaintSaveHotbarSlot`: default `2`
- `postPaintFinishedHotbarSlot`: default `0`
- `postPaintBlankCanvasHotbarSlot`: default `1`
- `postPaintAimCalibrationIndex`: default `500`
- `postPaintVaultCommand`: default `/pv 2`
- `postPaintSaveSelectDelayTicks`: default `2`
- `postPaintSaveAimSettleTicks`: default `3`
- `postPaintRenameOpenDelayTicks`: default `20`
- `postPaintRightClickRetries`: default `2`
- `postPaintFunJumpsEnabled`: default `true`
- `postPaintFunJumpCount`: default `5`
- `postPaintFunJumpPressTicks`: default `2`
- `postPaintFunJumpGapTicks`: default `4`
- `postPaintRenameClickPoint`: recorded by `#painting rename click`
- `postPaintPv2ClickPoint`: recorded by `#painting pv2 click`

The recorded rename and PV2 click points are saved in `.minecraft/config/artmap_color_assistant.json`, so they remain after restarting Minecraft. The mod does not warn about missing recorded points at startup or normal auto start; it only warns when post-paint automation reaches the rename or vault step that needs that point.

On Android/Pojav, `rename click` and `pv2 click` prefer actual GUI `mouseClicked` coordinates from the screen. Desktop cursor polling remains as a fallback. `#painting postpaint status` shows whether each point was recorded from `screen` or `cursor`.

For saving, the mod selects hotbar slot 3, waits briefly, aims at calibration index 500, settles the aim, refreshes the crosshair target, then sends the normal Minecraft right-click interaction. If the ArtMap save GUI does not open, it retries before pausing with a clear message.

After placing the next blank canvas, post-paint automation does 5 fun jumps by default, then right-clicks again to enter the easel for the next image. Disable this with `postPaintFunJumpsEnabled: false` or set `postPaintFunJumpCount: 0`.

The post-paint jumps are optional cosmetic movement only. If any post-paint step fails, finish the save/store/setup manually, then run `#painting batch continue`.

## Safety And Limits

This mod does not bypass ArtMap and does not edit server data. Semi-manual mode never auto-clicks, auto-aims, or moves the camera. Auto paint is opt-in with `#painting auto start`; it rotates the client camera to calibrated pixel targets and performs normal client clicks at the configured delay. Misclicks require `back`, `skip`, or `goto` correction.

The mod only scans the usable player inventory: hotbar slots 0-8 and main inventory slots 9-35. It ignores armor, offhand, creative tabs, and open containers.

Some servers may disallow automation or inventory packet behavior. Check server rules before using auto paint or inventory-to-hotbar swapping. If swapping fails, the session pauses and tells you exactly which item to put in the hotbar.

RGB values in the default ArtMap color table are approximations. Edit the config if your server uses different colors.

## Android / PojavLauncher

This remains a normal Fabric Java mod jar, not an Android APK. Use an Android Java launcher that can run Minecraft Java Edition 1.21.1 with Fabric Loader, Fabric API, and Java 21.

Setup:

1. Install Minecraft Java `1.21.1`, Fabric Loader, and Fabric API in the Android Java launcher.
2. Put `artmap-color-assistant-1.0.0.jar` in that launcher's `mods` folder.
3. Launch once, then run `#painting paths`.
4. Copy PNG files into the printed `PNG imports` folder.
5. Copy exact calibration JSON files into the printed `Calibrations` folder.
6. Run `#painting usecalibration <name>`.
7. Verify aim with `#painting cal test 0 0` and `#painting cal test 31 31`.
8. If a transferred exact calibration is blocked by the eye-position warning, run `#painting calibration portable on`, then retest aim before painting.
9. Re-record `#painting rename click` and `#painting pv2 click` on Android because GUI scale and touch layout differ.

Useful diagnostics:

- `#painting android status`
- `#painting android testinput`
- `#painting paths`

## Color Matching And Red Themes

The mod only uses colors listed in `artMapColors` inside `.minecraft/config/artmap_color_assistant.json`. It does not automatically use every reddish Minecraft item texture. For example, crimson stem, crimson hyphae, and crimson nylium are not default ArtMap color entries, so the mod will not select them unless you explicitly add them to the config and your server supports them as ArtMap paint items.

Bright red artwork may mostly choose `red_dye` because it is genuinely nearest to those pixels in the configured palette. The default red-ish ArtMap entries include items such as `red_dye`, `apple`, `nether_wart`, `spider_eye`, `beetroot`, `chorus_fruit`, `pink_dye`, and `magenta_dye`, but the matcher will only use them when their configured RGB value is closer to the image pixel and the item is available in your hotbar/main inventory.

Useful diagnostics:

- `#painting palette reds` shows which red-ish ArtMap colors are currently available.
- `#painting palette why #AA2222` shows the nearest configured colors to a specific RGB value and whether each item is in your inventory.
- `#painting dryrun image.png` shows matched item counts and warns when a red-heavy image collapses to only a few red-ish colors.

The config field `colorMatchMode` defaults to `RGB`, which preserves the original raw RGB squared-distance behavior. You can set it to `PERCEPTUAL` to use Lab-style distance, which can separate dark red, maroon, pink, and orange-red shades better for some images.

## Server-Measured Red Palette

This local build includes a server-measured red override layer enabled by default with `serverColorOverridesEnabled: true`. These values came from measuring painted ArtMap output and are not guaranteed to match every server.

Default measured overrides:

- `minecraft:crimson_nylium` -> `#9F2829`
- `minecraft:beetroot` -> `#7D495A`
- `minecraft:brick` -> `#812B2B`
- `minecraft:red_dye` -> `#D70000`
- `minecraft:apple` -> `#773126`
- `minecraft:spider_eye` -> `#874041`
- `minecraft:crimson_hyphae` -> `#4D1418`
- `minecraft:crimson_stem` -> `#7C3450`
- `minecraft:nether_wart` -> `#5E0100`

If an override item already exists in `artMapColors`, only its RGB is replaced for matching. If an override item is missing, it is added as a normal non-tool color entry. The raw `artMapColors` list stays separate, so setting `serverColorOverridesEnabled` to `false` disables this layer.

If your server does not accept crimson items as ArtMap paint colors, remove those entries from `serverColorOverrides` or set `serverColorOverridesEnabled` to `false`.

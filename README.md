# ArtMapColorAssistant

ArtMapColorAssistant is a client-only Fabric mod for Minecraft 1.21.1. It helps with manual ArtMap painting by reading a configured-size PNG and switching the held ArtMap color item after each real player click.

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
5. Click the ArtMap canvas manually from top-left to right, row by row.

Commands beginning with `#painting` are handled client-side and canceled before server chat.

## Commands

- `#painting <filename.png>` starts a session.
- `#painting dryrun <filename.png>` analyzes without starting.
- `#painting stop`, `pause`, `resume`, `back`, `skip`, `status`, `reload`.
- `#painting goto <index>` or `#painting goto <x> <y>`.
- `#painting pos <x> <y>` is an alias for x/y goto.
- `#painting confirm on|off` toggles confirm mode. When enabled, the advance keybind is required instead of mouse-click advancement.

## Safety And Limits

This mod does not bypass ArtMap. It does not edit server data, auto-click, auto-aim, or move the camera. The player must manually click pixels in the correct order. Misclicks require `back`, `skip`, or `goto` correction.

The mod only scans the usable player inventory: hotbar slots 0-8 and main inventory slots 9-35. It ignores armor, offhand, creative tabs, and open containers.

Some servers may disallow automation or inventory packet behavior. Check server rules before using inventory-to-hotbar swapping. If swapping fails, the session pauses and tells you exactly which item to put in the hotbar.

RGB values in the default ArtMap color table are approximations. Edit the config if your server uses different colors.

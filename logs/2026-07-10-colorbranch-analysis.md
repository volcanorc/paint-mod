# Colorbranch Analysis Log

- Date: 2026-07-10
- Analysis time: 2026-07-10 11:01:19 +08:00
- Branch: `colorbranch`
- Starting commit analyzed: `0c4db55`
- Repository: `volcanorc/paint-mod`

## What was checked

- Confirmed the working tree was clean on `colorbranch`.
- Confirmed `colorbranch` was at commit `0c4db55` and matched `origin/colorbranch`.
- Compared the current branch history against `SmartFeature`.
- Reviewed the launch crash report that mentioned:
  - `Mixin transformation of net.minecraft.client.main.Main failed`
  - `MixinApplyError: ScreenMixin`
  - `InvalidMixinException: @Mixin target type mismatch: net.minecraft.class_364 is an interface`
- Checked the current `ScreenMixin` fix.
- Checked the Fabric 1.21.1 dependency metadata.
- Checked the `#bot` hash-command pass-through behavior.
- Checked the measured ArtMap palette changes.
- Checked the Smart deep-black Ink Sac + Coal bucket basecoat behavior.
- Checked that Coal is excluded from normal image color matching.
- Ran project verification commands:
  - `git diff --check`
  - `.\gradlew.bat test --no-daemon`
  - `.\gradlew.bat build --no-daemon`
- Inspected the rebuilt v1.0.2 JAR contents for important classes and metadata.

## Main finding

The reported launch crash was caused by the old `ScreenMixin` targeting `net.minecraft.client.gui.Element` as a normal class mixin even though `Element` is an interface in Minecraft 1.21.1.

The current `colorbranch` already fixes that by making `ScreenMixin` an interface mixin. The rebuilt JAR contains the updated `ScreenMixin.class`, so this specific crash should not happen when Minecraft loads the correct current JAR.

This crash was not caused by Fabric API, Coal, Ink Sac, palette colors, Player Vault storage, or Smart painting logic.

## Most likely remaining error cause

The most likely remaining cause is a stale or duplicate ArtMapColorAssistant JAR in the Minecraft `mods` folder.

Important notes:

- If both an older `SmartFeature` JAR and the newer `colorbranch` JAR are in `mods`, Fabric may load the broken old mixin.
- If the launcher cached or kept the previous JAR, Minecraft can still crash with the same old `ScreenMixin` error.
- The safe setup is to delete every old ArtMapColorAssistant / paint-mod JAR from `mods`, then copy in only the newest rebuilt `artmap-color-assistant-1.0.2.jar`.

## Compatibility notes

- Target Minecraft version remains exactly `1.21.1`.
- Java requirement remains Java 21 or newer.
- Runtime minimums are:
  - Fabric Loader `0.15.11` or newer
  - Fabric API `0.101.2+1.21.1` or newer
- The user-shown Fabric API `0.116.12+1.21.1` is compatible with the current metadata.
- The mod still builds against the newer Fabric stack while allowing the tested older 1.21.1 runtime range.

## Colorbranch feature checks

### Measured palette

- The measured ArtMap palette is applied through the bundled/effective palette path.
- Existing users with older generated config palettes are migrated using the palette version field.
- Unmeasured older colors remain preserved.

### Coal and Ink Sac behavior

- Ink Sac remains the normal near-black paint color.
- Coal is kept as a tool for Smart deep-black bucket darkening only.
- Coal is locked out of normal Manual, Auto, and Smart pixel color matching.
- Charcoal remains a separate normal reddish-black paint color.

### Smart deep-black basecoat

- Deep-black dominant images force an Ink Sac bucket basecoat.
- Coal bucket darkening can run after Ink Sac only in Smart mode.
- Coal darkening uses guarded bucket handling and calibrated anchor groups.
- Coal is not used for normal per-pixel matching.

### Hash command behavior

- `#painting` and `#paint` are still intercepted and handled locally.
- Unknown leading-`#` messages are still blocked locally.
- Exact lowercase `#bot` followed by whitespace or end-of-message is allowed through for the other mod.
- Collision cases like `#botany`, `#bot#`, `#Bot`, and `#botcmd` remain blocked.

## Risk ranking

1. Highest risk: old or duplicate ArtMapColorAssistant JARs in the Minecraft `mods` folder.
   - This exactly explains seeing the old `ScreenMixin` interface-target crash after the code fix exists.
2. Medium risk: same-looking JAR names causing copy confusion.
   - The current artifact is still v1.0.2, so it is easy to accidentally keep or copy the wrong branch build.
3. Medium risk: wrong Minecraft/Fabric/Java install.
   - Minecraft must be `1.21.1`, Java must be 21+, and Fabric API must be for `1.21.1`.
4. Low risk: current `ScreenMixin` implementation.
   - Static inspection, tests, and build passed after the interface-mixin fix.
5. Low risk: `#bot` pass-through.
   - It intentionally lets exact `#bot` commands pass so another mod can own them.
6. Low launch risk: palette, Coal, and Smart planner changes.
   - These paths run after the game loads and are covered by tests/build.

## Next troubleshooting steps if launch still fails

1. Open the Minecraft `mods` folder.
2. Delete every old ArtMapColorAssistant / paint-mod JAR.
3. Copy in only the newest rebuilt `build/libs/artmap-color-assistant-1.0.2.jar`.
4. Confirm there is only one ArtMapColorAssistant JAR in the folder.
5. Launch Minecraft 1.21.1 again.
6. If it still fails, collect:
   - `.minecraft/logs/latest.log`
   - the crash report file, if generated
   - a screenshot or list of every JAR in the `mods` folder

## Verification result at time of analysis

- `git diff --check`: passed
- `.\gradlew.bat test --no-daemon`: passed
- `.\gradlew.bat build --no-daemon`: passed
- Working tree before this log was clean
- Current branch before this log was `colorbranch`


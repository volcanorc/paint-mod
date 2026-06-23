package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasuredArtMapPaletteTest {
    private static final Identifier INK_SAC = Identifier.of("minecraft:ink_sac");
    private static final Identifier CHARCOAL = Identifier.of("minecraft:charcoal");
    private static final Identifier COAL = Identifier.of("minecraft:coal");
    private static final Identifier RED_DYE = Identifier.of("minecraft:red_dye");
    private static final Identifier COBBLED_DEEPSLATE = Identifier.of("minecraft:cobbled_deepslate");
    private static final Identifier CRIMSON_HYPHAE = Identifier.of("minecraft:crimson_hyphae");
    private static final Identifier PACKED_ICE = Identifier.of("minecraft:packed_ice");
    private static final Identifier WARPED_WART_BLOCK = Identifier.of("minecraft:warped_wart_block");

    @Test
    void measuredPaletteContainsEveryUniqueColorFromColorsFile() {
        List<ServerColorOverride> measured = ConfigManager.measuredArtMapPalette();

        assertEquals(48, measured.size());
        assertEquals(0x141414, measuredRgb(INK_SAC));
        assertEquals(0x1E110C, measuredRgb(CHARCOAL));
        assertEquals(0xD10000, measuredRgb(RED_DYE));
        assertEquals(0x545352, measuredRgb(COBBLED_DEEPSLATE));
        assertEquals(0x8786D1, measuredRgb(PACKED_ICE));
        assertEquals(0x0F9067, measuredRgb(WARPED_WART_BLOCK));
        assertFalse(measured.stream().anyMatch(color -> color.item().equals(COAL)));
    }

    @Test
    void defaultsUseMeasuredColorsAndAddMeasuredItems() {
        ConfigManager.Config defaults = ConfigManager.Config.defaults();

        assertEquals(0x141414, rgb(defaults.artMapColors(), INK_SAC));
        assertEquals(0x1E110C, rgb(defaults.artMapColors(), CHARCOAL));
        assertEquals(0xD10000, rgb(defaults.artMapColors(), RED_DYE));
        assertEquals(0x545352, rgb(defaults.artMapColors(), COBBLED_DEEPSLATE));
        assertEquals(0x8786D1, rgb(defaults.artMapColors(), PACKED_ICE));
        assertEquals(0x0F9067, rgb(defaults.artMapColors(), WARPED_WART_BLOCK));
        assertEquals(0x4D1418, rgb(defaults.effectiveArtMapColors(), CRIMSON_HYPHAE));
    }

    @Test
    void normalMatchingUsesInkSacForNearBlackAndKeepsCharcoalExact() {
        ConfigManager.Config defaults = ConfigManager.Config.defaults();
        ColorMatcher matcher = new ColorMatcher();
        InventoryHelper.InventorySnapshot inventory = new InventoryHelper.InventorySnapshot(Map.of(
                INK_SAC, 64,
                CHARCOAL, 64,
                COAL, 64
        ));
        List<ArtMapColor> palette = matcher.buildMatchingPalette(defaults, inventory);

        assertTrue(palette.stream().noneMatch(ArtMapColor::tool));
        assertEquals(INK_SAC, matcher.nearest(0x000000, palette, defaults.colorMatchMode()).item());
        assertEquals(CHARCOAL, matcher.nearest(0x1E110C, palette, defaults.colorMatchMode()).item());
    }

    @Test
    void coalIsNeverUsedForNormalMatchingEvenWhenToolMatchingIsEnabled() {
        ConfigManager.Config toolMatching = withToolMatching(ConfigManager.Config.defaults(), true);
        ColorMatcher matcher = new ColorMatcher();
        InventoryHelper.InventorySnapshot inventory = new InventoryHelper.InventorySnapshot(Map.of(
                INK_SAC, 64,
                CHARCOAL, 64,
                COAL, 64
        ));

        List<ArtMapColor> palette = matcher.buildMatchingPalette(toolMatching, inventory);

        assertFalse(palette.stream().anyMatch(color -> color.item().equals(COAL)));
        assertEquals(INK_SAC, matcher.nearest(0x000000, palette, toolMatching.colorMatchMode()).item());
    }

    @Test
    void migrationUpdatesOldBaseColorsAndAddsMissingMeasuredItems() {
        List<ArtMapColor> old = List.of(
                color("BLACK", INK_SAC, 0x1D1D21, false),
                color("BLACK_TERRACOTTA", CHARCOAL, 0x251610, false),
                color("DARKEN_TOOL", COAL, 0x000000, true)
        );

        List<ArtMapColor> migrated = ConfigManager.applyMeasuredArtMapPalette(old);

        assertEquals(0x141414, rgb(migrated, INK_SAC));
        assertEquals(0x1E110C, rgb(migrated, CHARCOAL));
        assertEquals(0x000000, rgb(migrated, COAL));
        assertTrue(migrated.stream().filter(color -> color.item().equals(COAL)).findFirst().orElseThrow().tool());
        assertEquals(0x545352, rgb(migrated, COBBLED_DEEPSLATE));
    }

    @Test
    void migrationMergesMeasuredOverridesWithExistingOverrides() {
        List<ServerColorOverride> migrated = ConfigManager.mergeMeasuredServerColorOverrides(List.of(
                new ServerColorOverride(RED_DYE, 0xD70000),
                new ServerColorOverride(Identifier.of("minecraft:custom_item"), 0x123456)
        ));

        assertEquals(0xD10000, overrideRgb(migrated, RED_DYE));
        assertEquals(0x123456, overrideRgb(migrated, Identifier.of("minecraft:custom_item")));
        assertEquals(0x545352, overrideRgb(migrated, COBBLED_DEEPSLATE));
        assertEquals(49, migrated.size());
    }

    @Test
    void savedConfigIncludesCurrentPaletteVersion() {
        assertEquals(ConfigManager.CURRENT_ARTMAP_PALETTE_VERSION,
                ConfigManager.toJson(ConfigManager.Config.defaults()).get("artMapPaletteVersion").getAsInt());
    }

    private static ArtMapColor color(String name, Identifier item, int rgb, boolean tool) {
        return new ArtMapColor(name, name, item, null, rgb, tool);
    }

    private static int measuredRgb(Identifier item) {
        return overrideRgb(ConfigManager.measuredArtMapPalette(), item);
    }

    private static int overrideRgb(List<ServerColorOverride> colors, Identifier item) {
        return colors.stream()
                .filter(color -> color.item().equals(item))
                .findFirst()
                .orElseThrow()
                .rgb();
    }

    private static int rgb(List<ArtMapColor> colors, Identifier item) {
        return colors.stream()
                .filter(color -> color.item().equals(item))
                .findFirst()
                .orElseThrow()
                .rgb();
    }

    private static ConfigManager.Config withToolMatching(ConfigManager.Config config, boolean includeTools) {
        return new ConfigManager.Config(config.canvasWidth(), config.canvasHeight(), config.reservedHotbarSlot(),
                config.autoSwapFromInventory(), config.advanceOnLeftClick(), config.advanceOnRightClick(),
                config.onlyAdvanceWhenCrosshairTargetExists(), config.alphaThreshold(),
                config.transparentPixelMode(), config.debug(), config.useOnlyInventoryAvailableColors(),
                config.colorMatchMode(), includeTools, config.confirmMode(), config.paintingMode(),
                config.autoPaintDefaultDelayTicks(), config.autoPaintMinDelayTicks(), config.autoPaintClickButton(),
                config.autoAimSettleTicks(), config.autoAimToleranceDegrees(), config.autoRequireCalibration(),
                config.autoLockCameraDuringAuto(), config.portableExactCalibrationMode(),
                config.useBundledDirectionalCalibration(), config.defaultBundledCalibrationPrefix(),
                config.autoDetectCalibrationDirectionOnAutoStart(), config.cardinalDirectionToleranceDegrees(),
                config.autoEnablePortableForBundledCalibration(), config.autoDragSameColorRuns(),
                config.autoDragMinRunLength(), config.autoDragPixelTicks(), config.autoDragRequireExactCalibration(),
                config.autoDragStartHoldTicks(), config.autoDragEndHoldTicks(), config.smartEnabled(),
                config.smartMode(), config.smartBaseCoatEnabled(), config.smartBucketThreshold(),
                config.smartDragThreshold(), config.smartCoalBlackBasecoatEnabled(), config.smartCoalBlackPasses(),
                config.smartCoalBlackDominanceThreshold(), config.bucketEnabled(), config.bucketClickRepeats(),
                config.bucketClickGapTicks(), config.bucketSwapDelayTicks(), config.bucketAimSettleTicks(),
                config.bucketAfterDelayTicks(), config.selectedCalibrationName(), config.serverColorOverridesEnabled(),
                config.serverColorOverrides(), config.batchAutoStartAfterContinue(), config.batchDefaultSpeedTicks(),
                config.batchEnableDrag(), config.postPaintAutomationEnabled(), config.postPaintSaveHotbarSlot(),
                config.postPaintFinishedHotbarSlot(), config.postPaintBlankCanvasHotbarSlot(),
                config.postPaintAimCalibrationIndex(), config.postPaintVaultCommand(),
                config.postPaintSaveSelectDelayTicks(), config.postPaintSaveAimSettleTicks(),
                config.postPaintRenameOpenDelayTicks(), config.postPaintRightClickRetries(),
                config.postPaintFunJumpsEnabled(), config.postPaintFunJumpCount(), config.postPaintFunJumpPressTicks(),
                config.postPaintFunJumpGapTicks(), config.postPaintRenameClickPoint(),
                config.postPaintPv2ClickPoint(), config.artMapColors(), config.effectiveArtMapColors());
    }
}

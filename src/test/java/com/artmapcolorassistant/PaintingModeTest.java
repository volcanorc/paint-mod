package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintingModeTest {
    private static final ConfigManager.Config DEFAULTS = ConfigManager.Config.defaults();
    private static final ArtMapColor RED = color("RED", "minecraft:red_dye", 0xD70000);
    private static final ArtMapColor BLUE = color("BLUE", "minecraft:lapis_lazuli", 0x3C44AA);

    @Test
    void defaultModeIsSmartWithPostPaintOn() {
        ConfigManager.Config config = ConfigManager.Config.defaults();

        assertEquals(PaintingMode.SMART, config.paintingMode());
        assertTrue(config.smartEnabled());
        assertTrue(config.bucketEnabled());
        assertTrue(config.autoDragSameColorRuns());
        assertTrue(config.autoSwapFromInventory());
        assertTrue(config.postPaintAutomationEnabled());
        assertEquals("ee", config.selectedCalibrationName());
        assertTrue(config.useBundledDirectionalCalibration());
        assertEquals("ee", config.defaultBundledCalibrationPrefix());
        assertTrue(config.autoDetectCalibrationDirectionOnAutoStart());
        assertEquals(45.0D, config.cardinalDirectionToleranceDegrees());
        assertTrue(config.autoEnablePortableForBundledCalibration());
        assertEquals(10, config.bucketColorSelectDelayTicks());
        assertEquals(20, config.bucketHandSwapDelayTicks());
        assertEquals(16, config.bucketFillAimSettleTicks());
        assertEquals(24, config.bucketPostFillDelayTicks());
        assertEquals(10, config.bucketHandRestoreDelayTicks());
        assertEquals(5, config.autoDragPixelTicks());
        assertEquals(2, config.smartDragThreshold());
        assertEquals(5, config.autoPaintDefaultDelayTicks());
    }

    @Test
    void manualPresetDisablesAutomationButKeepsAssistedSwap() {
        ConfigManager.Config config = DEFAULTS.withPostPaintAutomationEnabled(false)
                .withPaintingModePreset(PaintingMode.MANUAL);

        assertEquals(PaintingMode.MANUAL, config.paintingMode());
        assertTrue(config.autoSwapFromInventory());
        assertFalse(config.smartEnabled());
        assertFalse(config.bucketEnabled());
        assertFalse(config.autoDragSameColorRuns());
        assertFalse(config.batchEnableDrag());
        assertFalse(config.postPaintAutomationEnabled());
        assertEquals("ee", config.selectedCalibrationName());
        assertEquals(5, config.autoPaintDefaultDelayTicks());
    }

    @Test
    void autoPresetUsesOldAutoDefaults() {
        ConfigManager.Config config = DEFAULTS.withPaintingModePreset(PaintingMode.AUTO);

        assertEquals(PaintingMode.AUTO, config.paintingMode());
        assertTrue(config.autoSwapFromInventory());
        assertTrue(config.autoDragSameColorRuns());
        assertTrue(config.batchEnableDrag());
        assertFalse(config.smartEnabled());
        assertFalse(config.bucketEnabled());
        assertTrue(config.postPaintAutomationEnabled());
        assertEquals("ee", config.selectedCalibrationName());
        assertEquals(5, config.autoPaintDefaultDelayTicks());
    }

    @Test
    void smartPresetEnablesSmartAndBucketDefaults() {
        ConfigManager.Config config = DEFAULTS.withPaintingModePreset(PaintingMode.MANUAL)
                .withPaintingModePreset(PaintingMode.SMART);

        assertEquals(PaintingMode.SMART, config.paintingMode());
        assertTrue(config.smartEnabled());
        assertTrue(config.bucketEnabled());
        assertTrue(config.autoDragSameColorRuns());
        assertTrue(config.batchEnableDrag());
        assertTrue(config.autoSwapFromInventory());
        assertTrue(config.postPaintAutomationEnabled());
        assertEquals("ee", config.selectedCalibrationName());
        assertEquals(10, config.bucketColorSelectDelayTicks());
        assertEquals(20, config.bucketHandSwapDelayTicks());
        assertEquals(16, config.bucketFillAimSettleTicks());
        assertEquals(24, config.bucketPostFillDelayTicks());
        assertEquals(10, config.bucketHandRestoreDelayTicks());
    }

    @Test
    void modeGatesMatchCommandRules() {
        assertFalse(PaintingMode.MANUAL.allowsBatch());
        assertFalse(PaintingMode.MANUAL.allowsBucketConfig());
        assertFalse(PaintingMode.MANUAL.allowsAutoDragConfig());

        assertTrue(PaintingMode.AUTO.allowsBatch());
        assertFalse(PaintingMode.AUTO.allowsBucketConfig());
        assertTrue(PaintingMode.AUTO.allowsAutoDragConfig());

        assertTrue(PaintingMode.SMART.allowsBatch());
        assertTrue(PaintingMode.SMART.allowsBucketConfig());
        assertTrue(PaintingMode.SMART.allowsAutoDragConfig());
    }

    @Test
    void smartModeStillAllowsBaseCoatPlanner() {
        ConfigManager.Config config = DEFAULTS.withPaintingModePreset(PaintingMode.SMART);
        PaintSession session = sessionWith(index -> index % 5 == 0 ? RED : BLUE);
        SmartPreview preview = new SmartPaintPlanner().preview(session, config);

        assertEquals(BLUE, preview.selectedBaseCoatColor());
        assertTrue(preview.smartEstimatedTicks() < preview.oldEstimatedTicks());
    }

    @Test
    void modePresetSelectsEeForLaterExactLoad() {
        assertEquals("ee", DEFAULTS.withPaintingModePreset(PaintingMode.MANUAL).selectedCalibrationName());
        assertEquals("ee", DEFAULTS.withPaintingModePreset(PaintingMode.AUTO).selectedCalibrationName());
        assertEquals("ee", DEFAULTS.withPaintingModePreset(PaintingMode.SMART).selectedCalibrationName());
    }

    @Test
    void loadedExactNameIsSeparateFromSelectedConfigName() {
        CanvasCalibration calibration = new CanvasCalibration();

        assertEquals(null, calibration.loadedExactName());

        calibration.setLoadedMetadata("other", DEFAULTS.canvasWidth(), DEFAULTS.canvasHeight());

        assertEquals("other", calibration.loadedExactName());
        assertFalse("ee".equals(calibration.loadedExactName()));
    }

    private static PaintSession sessionWith(ColorAt colorAt) {
        int size = DEFAULTS.canvasWidth() * DEFAULTS.canvasHeight();
        List<PaintStep> steps = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ArtMapColor color = colorAt.color(i);
            steps.add(new PaintStep(i, CanvasMath.toX(i, DEFAULTS.canvasWidth()), CanvasMath.toY(i, DEFAULTS.canvasWidth()),
                    0xFF000000 | color.rgb(), false, color, color.item()));
        }
        return new PaintSession("synthetic.png", steps, List.of(RED, BLUE));
    }

    private static ArtMapColor color(String name, String item, int rgb) {
        return new ArtMapColor(name, name, Identifier.of(item), null, rgb, false);
    }

    private interface ColorAt {
        ArtMapColor color(int index);
    }
}

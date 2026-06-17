package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPaintPlannerTest {
    private static final ConfigManager.Config CONFIG = ConfigManager.Config.defaults();
    private static final ArtMapColor WHITE = color("WHITE", "minecraft:bone_meal", 0xF9FFFE);
    private static final ArtMapColor RED = color("RED", "minecraft:red_dye", 0xD70000);
    private static final ArtMapColor BLUE = color("BLUE", "minecraft:lapis_lazuli", 0x3C44AA);

    @Test
    void floodFillNeverCrossesDiagonals() {
        PaintSession session = sessionWith(index -> WHITE);
        SmartCanvas canvas = SmartCanvas.fresh(session, CONFIG);
        List<Integer> redIndexes = new ArrayList<>();
        for (int i = 0; i < CONFIG.canvasWidth() * CONFIG.canvasHeight(); i++) {
            if (i != 0 && i != 33) {
                redIndexes.add(i);
            }
        }
        canvas.apply(action(PaintActionType.MANUAL_CLICK, RED, redIndexes));

        List<Integer> region = FloodFill4.fill(canvas, 0);

        assertEquals(List.of(0), region);
    }

    @Test
    void safeBucketOnlyAcceptsRegionsWithOneTargetColor() {
        ConfigManager.Config noBaseCoat = CONFIG.withSmartSettings(true, SmartPaintMode.AGGRESSIVE, false, 2, 5);
        PaintSession session = sessionWith(index -> index == 1 ? BLUE : RED);
        SmartCanvas canvas = SmartCanvas.fresh(session, noBaseCoat);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        PaintAction action = planner.nextAction(canvas, noBaseCoat).orElseThrow();

        assertFalse(action.bucket());
    }

    @Test
    void bucketSafetyRejectsTransparentPixelsInsideFloodRegion() {
        ConfigManager.Config noBaseCoat = CONFIG.withSmartSettings(true, SmartPaintMode.AGGRESSIVE, false, 2, 5);
        PaintSession session = sessionWithSkips(index -> RED, index -> index == 1);
        SmartCanvas canvas = SmartCanvas.fresh(session, noBaseCoat);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        PaintAction action = planner.nextAction(canvas, noBaseCoat).orElseThrow();

        assertFalse(action.bucket());
    }

    @Test
    void dominantBaseCoatUpdatesBelievedCanvas() {
        PaintSession session = sessionWith(index -> index % 4 == 0 ? RED : BLUE);
        SmartCanvas canvas = SmartCanvas.fresh(session, CONFIG);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        PaintAction action = planner.nextAction(canvas, CONFIG).orElseThrow();
        canvas.apply(action);

        assertEquals(PaintActionType.BUCKET_BASE_COAT, action.type());
        assertTrue(canvas.baseCoatDone());
        assertEquals(BLUE, action.color());
        assertEquals(BLUE, canvas.currentColor(0));
        assertEquals(BLUE, canvas.currentColor(900));
    }

    @Test
    void scatteredColorCanBeSelectedAsBaseCoatWhenItSavesClicks() {
        PaintSession session = sessionWith(index -> index % 5 == 0 ? RED : BLUE);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        SmartPreview preview = planner.preview(session, CONFIG);
        PaintAction action = planner.nextAction(SmartCanvas.fresh(session, CONFIG), CONFIG).orElseThrow();

        assertEquals(PaintActionType.BUCKET_BASE_COAT, action.type());
        assertEquals(BLUE, action.color());
        assertEquals(BLUE, preview.selectedBaseCoatColor());
        assertTrue(preview.smartEstimatedTicks() < preview.oldEstimatedTicks());
    }

    @Test
    void blankMajorityDoesNotForceBadBaseCoat() {
        PaintSession session = sessionWith(index -> index < 20 ? RED : WHITE);
        SmartPaintPlanner planner = new SmartPaintPlanner();
        SmartCanvas canvas = SmartCanvas.fresh(session, CONFIG);

        SmartPreview preview = planner.preview(session, CONFIG);
        PaintAction action = planner.nextAction(canvas, CONFIG).orElseThrow();
        canvas.apply(action);

        assertFalse(action.type() == PaintActionType.BUCKET_BASE_COAT);
        assertTrue(canvas.baseCoatDecisionDone());
        assertEquals(null, preview.selectedBaseCoatColor());
    }

    @Test
    void tiedBaseCoatCandidatesChooseStableFirstColor() {
        PaintSession session = sessionWith(index -> index % 2 == 0 ? BLUE : RED);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        PaintAction first = planner.nextAction(SmartCanvas.fresh(session, CONFIG), CONFIG).orElseThrow();
        PaintAction second = planner.nextAction(SmartCanvas.fresh(session, CONFIG), CONFIG).orElseThrow();

        assertEquals(PaintActionType.BUCKET_BASE_COAT, first.type());
        assertEquals(BLUE, first.color());
        assertEquals(first.color(), second.color());
    }

    @Test
    void skippedImageRejectsBaseCoatEvenWhenColorDominates() {
        PaintSession session = sessionWithSkips(index -> RED, index -> index == 10);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        PaintAction action = planner.nextAction(SmartCanvas.fresh(session, CONFIG), CONFIG).orElseThrow();
        SmartPreview preview = planner.preview(session, CONFIG);

        assertFalse(action.type() == PaintActionType.BUCKET_BASE_COAT);
        assertEquals(null, preview.selectedBaseCoatColor());
    }

    @Test
    void checkerboardCanUseBaseCoatWhenItBeatsNoBaseCoat() {
        PaintSession session = sessionWith(index -> index % 2 == 0 ? RED : BLUE);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        SmartPreview preview = planner.preview(session, CONFIG);

        assertEquals(RED, preview.selectedBaseCoatColor());
        assertTrue(preview.smartEstimatedTicks() < preview.oldEstimatedTicks());
    }

    @Test
    void checkerboardPreviewShowsNoBucketBenefit() {
        ConfigManager.Config noBaseCoat = CONFIG.withSmartSettings(true, SmartPaintMode.AGGRESSIVE, false, 6, 5);
        PaintSession session = sessionWith(index -> (CanvasMath.toX(index, CONFIG.canvasWidth()) + CanvasMath.toY(index, CONFIG.canvasWidth())) % 2 == 0 ? RED : BLUE);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        SmartPreview preview = planner.preview(session, noBaseCoat);

        assertEquals(0, preview.bucketActions());
        assertTrue(preview.smartEstimatedTicks() >= preview.oldEstimatedTicks());
    }

    private static PaintSession sessionWith(ColorAt colorAt) {
        return sessionWithSkips(colorAt, index -> false);
    }

    private static PaintSession sessionWithSkips(ColorAt colorAt, SkipAt skipAt) {
        int size = CONFIG.canvasWidth() * CONFIG.canvasHeight();
        List<PaintStep> steps = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ArtMapColor color = colorAt.color(i);
            boolean skip = skipAt.skip(i);
            steps.add(new PaintStep(i, CanvasMath.toX(i, CONFIG.canvasWidth()), CanvasMath.toY(i, CONFIG.canvasWidth()),
                    skip ? 0 : 0xFF000000 | color.rgb(), skip, skip ? null : color, skip ? null : color.item()));
        }
        return new PaintSession("synthetic.png", steps, List.of(WHITE, RED, BLUE));
    }

    private static PaintAction action(PaintActionType type, ArtMapColor color, List<Integer> indexes) {
        int seed = indexes.getFirst();
        return new PaintAction(type, color, color.item(), indexes, seed, seed, indexes.getLast(), 1, "test");
    }

    private static ArtMapColor color(String name, String item, int rgb) {
        return new ArtMapColor(name, name, Identifier.of(item), null, rgb, false);
    }

    private interface ColorAt {
        ArtMapColor color(int index);
    }

    private interface SkipAt {
        boolean skip(int index);
    }
}

package com.artmapcolorassistant;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPaintPlannerTest {
    private static final ConfigManager.Config CONFIG = ConfigManager.Config.defaults();
    private static final ArtMapColor WHITE = color("WHITE", "minecraft:bone_meal", 0xF9FFFE);
    private static final ArtMapColor RED = color("RED", "minecraft:red_dye", 0xD70000);
    private static final ArtMapColor BLUE = color("BLUE", "minecraft:lapis_lazuli", 0x3C44AA);
    private static final ArtMapColor INK_SAC = color("BLACK", "minecraft:ink_sac", 0x141414);
    private static final ArtMapColor CHARCOAL = color("CHARCOAL", "minecraft:charcoal", 0x1E110C);

    @Test
    void dominantBaseCoatIsAlwaysFirstAndOnlyBucket() {
        PaintSession session = sessionWith(index -> index < 700 ? RED : BLUE);
        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(session, CONFIG);

        assertTrue(plan.available());
        assertEquals(PaintActionType.BUCKET_BASE_COAT, plan.baseCoat().type());
        assertEquals(RED, plan.baseCoat().color());
        assertEquals(1, plan.preview().bucketActions());
        assertTrue(plan.actions().stream().noneMatch(PaintAction::bucket));
    }

    @Test
    void deepBlackMajorityUsesInkSacBaseCoatAndCoalBucketPasses() {
        PaintSession session = sessionWithRaw(index -> index < 700 ? INK_SAC : BLUE,
                index -> index < 700 ? 0xFF000000 : 0xFF0000FF);

        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(session, CONFIG);

        assertTrue(plan.available());
        assertEquals(INK_SAC.item(), plan.baseCoat().item());
        assertEquals(3, plan.preview().bucketActions());
        assertTrue(plan.preview().coalBlackPlanned());
        assertEquals(2, plan.preview().coalBlackPasses());
        assertEquals(700, plan.preview().deepBlackPixels());
        assertEquals(PaintActionType.COAL_BUCKET_DARKEN, plan.actions().get(0).type());
        assertEquals(DeepBlackAnalyzer.COAL, plan.actions().get(0).item());
        assertEquals(PaintActionType.COAL_BUCKET_DARKEN, plan.actions().get(1).type());
        assertEquals(DeepBlackAnalyzer.COAL, plan.actions().get(1).item());
    }

    @Test
    void charcoalMajorityDoesNotTriggerCoalBlackBaseCoat() {
        PaintSession session = sessionWithRaw(index -> index < 700 ? CHARCOAL : BLUE,
                index -> index < 700 ? 0xFF1E110C : 0xFF0000FF);

        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(session, CONFIG);

        assertTrue(plan.available());
        assertEquals(CHARCOAL.item(), plan.baseCoat().item());
        assertFalse(plan.preview().coalBlackPlanned());
        assertEquals(0, plan.preview().coalBlackPasses());
        assertTrue(plan.actions().stream().noneMatch(action -> action.type() == PaintActionType.COAL_BUCKET_DARKEN));
    }

    @Test
    void coalBlackPassesCanBeDisabledAndClamped() {
        ConfigManager.Config disabled = CONFIG.withSmartCoalBlackSettings(false, 2, 0.55D);
        PaintSession session = sessionWithRaw(index -> INK_SAC, index -> 0xFF000000);

        PreparedSmartPlan disabledPlan = new SmartPaintPlanner().prepare(session, disabled);

        assertFalse(disabledPlan.preview().coalBlackPlanned());
        assertEquals(1, disabled.withSmartCoalBlackSettings(true, -5, 0.55D).smartCoalBlackPasses());
        assertEquals(2, disabled.withSmartCoalBlackSettings(true, 99, 0.55D).smartCoalBlackPasses());
    }

    @Test
    void dominantPixelsAreRemovedFromRemainingPlan() {
        PaintSession session = sessionWith(index -> index < 900 ? RED : BLUE);
        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(session, CONFIG);

        assertTrue(plan.actions().stream().flatMap(action -> action.indexes().stream())
                .allMatch(index -> session.steps().get(index).matchedColor().equals(BLUE)));
    }

    @Test
    void twoHorizontalPixelsAlwaysDrag() {
        PreparedSmartPlan plan = planWithBlue(Set.of(0, 1));
        assertSingleDragCovering(plan, Set.of(0, 1));
    }

    @Test
    void twoVerticalPixelsAlwaysDrag() {
        PreparedSmartPlan plan = planWithBlue(Set.of(0, CONFIG.canvasWidth()));
        assertSingleDragCovering(plan, Set.of(0, CONFIG.canvasWidth()));
    }

    @Test
    void turningTrailRemainsEdgeAdjacent() {
        Set<Integer> pixels = Set.of(0, 1, 1 + CONFIG.canvasWidth());
        PreparedSmartPlan plan = planWithBlue(pixels);

        assertSingleDragCovering(plan, pixels);
        assertAdjacent(plan.actions().getFirst().indexes());
    }

    @Test
    void diagonalOnlyPixelsRemainIsolatedManualActions() {
        PreparedSmartPlan plan = planWithBlue(Set.of(0, CONFIG.canvasWidth() + 1));

        assertEquals(2, plan.actions().size());
        assertTrue(plan.actions().stream().allMatch(action -> action.type() == PaintActionType.MANUAL_CLICK));
    }

    @Test
    void branchedComponentUsesOneSafeTrailWithJunctionRevisits() {
        int center = CONFIG.canvasWidth() + 1;
        Set<Integer> pixels = Set.of(center, center - 1, center + 1,
                center - CONFIG.canvasWidth(), center + CONFIG.canvasWidth());
        PreparedSmartPlan plan = planWithBlue(pixels);

        assertSingleDragCovering(plan, pixels);
        List<Integer> trail = plan.actions().getFirst().indexes();
        assertAdjacent(trail);
        assertTrue(trail.size() >= pixels.size());
    }

    @Test
    void cyclicComponentProducesAdjacentTrail() {
        Set<Integer> pixels = Set.of(0, 1, CONFIG.canvasWidth(), CONFIG.canvasWidth() + 1);
        PreparedSmartPlan plan = planWithBlue(pixels);

        assertSingleDragCovering(plan, pixels);
        assertAdjacent(plan.actions().getFirst().indexes());
    }

    @Test
    void longConnectedTrailIsSplitWithAdjacentOverlap() {
        Set<Integer> pixels = new HashSet<>();
        for (int y = 0; y < CONFIG.canvasHeight(); y++) {
            pixels.add(CanvasMath.toIndex(0, y, CONFIG.canvasWidth()));
            pixels.add(CanvasMath.toIndex(1, y, CONFIG.canvasWidth()));
            pixels.add(CanvasMath.toIndex(2, y, CONFIG.canvasWidth()));
        }
        PreparedSmartPlan plan = planWithBlue(pixels);

        assertTrue(plan.actions().size() > 1);
        assertTrue(plan.actions().stream().allMatch(action -> action.type() == PaintActionType.DRAG_RUN));
        for (PaintAction action : plan.actions()) {
            assertTrue(action.indexes().size() <= 64);
            assertAdjacent(action.indexes());
        }
        assertEquals(pixels, covered(plan.actions()));
    }

    @Test
    void transparentSkipBlocksMandatoryBaseCoat() {
        PaintSession session = sessionWithSkips(index -> RED, index -> index == 10);
        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(session, CONFIG);

        assertFalse(plan.available());
        assertTrue(plan.unavailableReason().contains("transparent SKIP"));
        assertEquals(0, plan.preview().bucketActions());
    }

    @Test
    void tiedDominantColorsChooseStableFirstSeenColor() {
        PaintSession session = sessionWith(index -> index % 2 == 0 ? BLUE : RED);
        SmartPaintPlanner planner = new SmartPaintPlanner();

        assertEquals(BLUE, planner.prepare(session, CONFIG).baseCoat().color());
        assertEquals(BLUE, planner.prepare(session, CONFIG).baseCoat().color());
    }

    @Test
    void bucketPlanContainsNineUniqueNearCenterAnchors() {
        PreparedSmartPlan plan = new SmartPaintPlanner().prepare(
                sessionWith(index -> index < 700 ? RED : BLUE), CONFIG);

        assertEquals(9, plan.bucketAimAnchors().size());
        assertEquals(9, new HashSet<>(plan.bucketAimAnchors()).size());
        int centerX = (CONFIG.canvasWidth() - 1) / 2;
        int centerY = (CONFIG.canvasHeight() - 1) / 2;
        for (int index : plan.bucketAimAnchors()) {
            int x = CanvasMath.toX(index, CONFIG.canvasWidth());
            int y = CanvasMath.toY(index, CONFIG.canvasWidth());
            assertTrue(x >= 0 && x < CONFIG.canvasWidth());
            assertTrue(y >= 0 && y < CONFIG.canvasHeight());
            assertTrue(Math.abs(x - centerX) <= 1);
            assertTrue(Math.abs(y - centerY) <= 1);
        }
    }

    @Test
    void plannerPreparesWorstCaseCanvasUnder250Milliseconds() {
        PaintSession session = sessionWith(index -> {
            int x = CanvasMath.toX(index, CONFIG.canvasWidth());
            int y = CanvasMath.toY(index, CONFIG.canvasWidth());
            return (x + y) % 2 == 0 ? RED : BLUE;
        });

        PreparedSmartPlan plan = assertTimeout(Duration.ofMillis(250),
                () -> new SmartPaintPlanner().prepare(session, CONFIG));
        assertNotNull(plan);
        assertTrue(plan.available());
    }

    @Test
    void floodFillNeverCrossesDiagonals() {
        PaintSession session = sessionWith(index -> WHITE);
        SmartCanvas canvas = SmartCanvas.fresh(session, CONFIG);
        List<Integer> redIndexes = new ArrayList<>();
        for (int i = 0; i < CONFIG.canvasWidth() * CONFIG.canvasHeight(); i++) {
            if (i != 0 && i != CONFIG.canvasWidth() + 1) {
                redIndexes.add(i);
            }
        }
        canvas.apply(action(PaintActionType.MANUAL_CLICK, RED, redIndexes));
        assertEquals(List.of(0), FloodFill4.fill(canvas, 0));
    }

    private static PreparedSmartPlan planWithBlue(Set<Integer> bluePixels) {
        return new SmartPaintPlanner().prepare(sessionWith(index -> bluePixels.contains(index) ? BLUE : RED), CONFIG);
    }

    private static void assertSingleDragCovering(PreparedSmartPlan plan, Set<Integer> expected) {
        assertTrue(plan.available());
        assertEquals(1, plan.actions().size());
        PaintAction action = plan.actions().getFirst();
        assertEquals(PaintActionType.DRAG_RUN, action.type());
        assertEquals(expected, new HashSet<>(action.indexes()));
    }

    private static void assertAdjacent(List<Integer> indexes) {
        for (int i = 1; i < indexes.size(); i++) {
            int previous = indexes.get(i - 1);
            int current = indexes.get(i);
            int dx = Math.abs(CanvasMath.toX(previous, CONFIG.canvasWidth()) - CanvasMath.toX(current, CONFIG.canvasWidth()));
            int dy = Math.abs(CanvasMath.toY(previous, CONFIG.canvasWidth()) - CanvasMath.toY(current, CONFIG.canvasWidth()));
            assertEquals(1, dx + dy, "non-adjacent trail step " + previous + " -> " + current);
        }
    }

    private static Set<Integer> covered(List<PaintAction> actions) {
        HashSet<Integer> result = new HashSet<>();
        actions.forEach(action -> result.addAll(action.indexes()));
        return result;
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

    private static PaintSession sessionWithRaw(ColorAt colorAt, RawAt rawAt) {
        int size = CONFIG.canvasWidth() * CONFIG.canvasHeight();
        List<PaintStep> steps = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ArtMapColor color = colorAt.color(i);
            steps.add(new PaintStep(i, CanvasMath.toX(i, CONFIG.canvasWidth()), CanvasMath.toY(i, CONFIG.canvasWidth()),
                    rawAt.argb(i), false, color, color.item()));
        }
        return new PaintSession("synthetic.png", steps, List.of(WHITE, RED, BLUE, INK_SAC, CHARCOAL));
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

    private interface RawAt {
        int argb(int index);
    }

    private interface SkipAt {
        boolean skip(int index);
    }
}

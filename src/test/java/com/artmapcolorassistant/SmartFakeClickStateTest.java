package com.artmapcolorassistant;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartFakeClickStateTest {
    private static final ArtMapColor BASE = color("BASE", "minecraft:bone_meal", false);
    private static final ArtMapColor RED = color("RED", "minecraft:red_dye", false);
    private static final ArtMapColor BLUE = color("BLUE", "minecraft:lapis_lazuli", false);
    private static final ArtMapColor GREEN = color("GREEN", "minecraft:green_dye", false);
    private static final ArtMapColor COAL = color("COAL", "minecraft:coal", true);

    @Test
    void bucketHeavyStreakStartsFakeGesturesOnSecondEligibleImage() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(1));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);

        assertFalse(state.beginImage(plan, config).runFakeClick());
        assertTrue(state.beginImage(plan, config).runFakeClick());
        assertTrue(state.beginImage(plan, config).runFakeClick());
    }

    @Test
    void detailedImageResetsBucketHeavyStreak() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(2));
        ConfigManager.Config config = ConfigManager.Config.defaults();

        state.beginImage(planWithDetailPixels(0), config);
        state.beginImage(planWithDetailPixels(0), config);
        assertEquals(0, state.beginImage(planWithDetailPixels(200), config).streak());
        assertFalse(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
        assertTrue(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
    }

    @Test
    void tinyDetailImagesDoNotCountAsEmptyByDefault() {
        SmartFakeClickState.Analysis analysis =
                SmartFakeClickState.analyze(planWithDetailPixels(55), ConfigManager.Config.defaults());

        assertFalse(analysis.bucketHeavy());
        assertEquals(55, analysis.detailPixels());
        assertTrue(analysis.baseCoatDominance() > 0.90D);
    }

    @Test
    void fakeClickDefaultsAreTrueEmptySecondPaintingDefaults() {
        ConfigManager.Config config = ConfigManager.Config.defaults();

        assertTrue(config.smartFakeClickEnabled());
        assertEquals(1, config.smartFakeClickStreakThreshold());
        assertEquals(1.0D, config.smartFakeClickDominanceThreshold(), 0.000001D);
        assertEquals(0, config.smartFakeClickMaxDetailPixels());
    }

    @Test
    void legacyExperimentalFakeClickDefaultsMigrateToSecondTrueEmptyDefaults() {
        JsonObject root = ConfigManager.toJson(ConfigManager.Config.defaults());
        root.addProperty("smartFakeClickStreakThreshold", SmartFakeClickState.LEGACY_DEFAULT_STREAK_THRESHOLD);
        root.addProperty("smartFakeClickDominanceThreshold", SmartFakeClickState.LEGACY_DEFAULT_DOMINANCE_THRESHOLD);
        root.addProperty("smartFakeClickMaxDetailPixels", SmartFakeClickState.LEGACY_DEFAULT_MAX_DETAIL_PIXELS);

        assertTrue(ConfigManager.hasLegacySmartFakeClickDefaults(root,
                SmartFakeClickState.LEGACY_DEFAULT_STREAK_THRESHOLD,
                SmartFakeClickState.LEGACY_DEFAULT_DOMINANCE_THRESHOLD,
                SmartFakeClickState.LEGACY_DEFAULT_MAX_DETAIL_PIXELS));
    }

    @Test
    void customizedFakeClickDetectionValuesArePreserved() {
        JsonObject root = ConfigManager.toJson(ConfigManager.Config.defaults());
        root.addProperty("smartFakeClickStreakThreshold", 2);
        root.addProperty("smartFakeClickDominanceThreshold", 0.95D);
        root.addProperty("smartFakeClickMaxDetailPixels", 12);

        assertFalse(ConfigManager.hasLegacySmartFakeClickDefaults(root, 2, 0.95D, 12));
    }

    @Test
    void generatedFakeActionsUseDecoySwapsThenSameColorClickAndDrag() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(3));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);

        List<PaintAction> actions = state.generateActions(plan, config, available, exact, 32);
        long swaps = actions.stream().filter(action -> action.type() == PaintActionType.FAKE_COLOR_SWAP).count();
        long clicks = actions.stream().filter(action -> action.type() == PaintActionType.MANUAL_CLICK).count();
        long drags = actions.stream().filter(action -> action.type() == PaintActionType.DRAG_RUN).count();
        int fakePaintedPixels = actions.stream()
                .filter(action -> action.type() == PaintActionType.MANUAL_CLICK || action.type() == PaintActionType.DRAG_RUN)
                .mapToInt(PaintAction::affectedCount)
                .sum();

        assertTrue(swaps >= 2 && swaps <= 3);
        assertTrue(clicks >= 1);
        assertTrue(drags >= 1);
        assertTrue(fakePaintedPixels >= 5 && fakePaintedPixels <= 10);
        for (PaintAction action : actions) {
            if (action.type() == PaintActionType.FAKE_COLOR_SWAP) {
                assertNotEquals(BASE.item(), action.color().item());
                assertNotEquals(COAL.item(), action.color().item());
                assertTrue(action.indexes().isEmpty());
            } else {
                assertEquals(BASE, action.color());
                for (int index : action.indexes()) {
                    assertTrue(SmartFakeClickState.templateContainsForTesting(index),
                            "early fake gesture should follow the template path before template exhaustion: " + index);
                }
            }
        }
    }

    @Test
    void generatedDragsFollowAdjacentTemplatePath() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(33));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);

        List<PaintAction> actions = state.generateActions(plan, config, available, exact, 32);

        for (PaintAction action : actions) {
            if (action.type() != PaintActionType.DRAG_RUN) {
                continue;
            }
            for (int i = 1; i < action.indexes().size(); i++) {
                int previous = action.indexes().get(i - 1);
                int current = action.indexes().get(i);
                int dx = Math.abs(CanvasMath.toX(previous, 32) - CanvasMath.toX(current, 32));
                int dy = Math.abs(CanvasMath.toY(previous, 32) - CanvasMath.toY(current, 32));
                assertEquals(1, dx + dy, "fake drag should walk adjacent template pixels");
            }
        }
    }

    @Test
    void fakeClickCanBePersistentlyDisabled() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(4));
        ConfigManager.Config config = ConfigManager.Config.defaults().withSmartFakeClickEnabled(false);

        assertFalse(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
        assertFalse(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
        assertFalse(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
        assertFalse(state.beginImage(planWithDetailPixels(0), config).runFakeClick());
    }

    @Test
    void gestureCursorPersistsAcrossRepeatedBucketHeavyImages() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(5));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);

        Set<Integer> firstPaintIndexes = new HashSet<>();
        for (int image = 0; image < 20; image++) {
            SmartFakeClickState.Decision decision = state.beginImage(plan, config);
            if (!decision.runFakeClick()) {
                continue;
            }
            List<PaintAction> actions = state.generateActions(plan, config, available, exact, 32);
            actions.stream()
                    .filter(action -> action.type() == PaintActionType.MANUAL_CLICK
                            || action.type() == PaintActionType.DRAG_RUN)
                    .findFirst()
                    .ifPresent(action -> firstPaintIndexes.add(action.seedIndex()));
        }

        assertTrue(firstPaintIndexes.size() > 8,
                "fake gesture should continue through the template instead of restarting at one point");
    }

    @Test
    void fakeGestureCursorPersistsAcrossSeparateBatchStartsInSameSession() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(55));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);
        ArrayList<Integer> firstBatchPainted = new ArrayList<>();
        ArrayList<Integer> secondBatchPainted = new ArrayList<>();

        simulateEmptyBatch(state, config, plan, available, exact, 20, firstBatchPainted);
        int pathCursorAfterFirstBatch = state.templatePathCursorForTesting();
        int pointCursorAfterFirstBatch = state.templatePointCursorForTesting();
        simulateEmptyBatch(state, config, plan, available, exact, 20, secondBatchPainted);

        assertFalse(firstBatchPainted.isEmpty());
        assertFalse(secondBatchPainted.isEmpty());
        assertNotEquals(firstBatchPainted.getFirst(), secondBatchPainted.getFirst(),
                "second batch should not restart at the first fake-paint point from the previous batch");
        assertTrue(state.templatePathCursorForTesting() != pathCursorAfterFirstBatch
                        || state.templatePointCursorForTesting() != pointCursorAfterFirstBatch,
                "template cursor should keep advancing across separate batch starts in the same session");
    }

    @Test
    void repeatedBucketHeavyImagesAvoidRepeatingFakePaintPointsBeforePoolExhaustion() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(6));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);
        Set<Integer> painted = new HashSet<>();

        for (int image = 0; image < 300 && painted.size() < exact.size(); image++) {
            SmartFakeClickState.Decision decision = state.beginImage(plan, config);
            if (!decision.runFakeClick()) {
                continue;
            }
            List<PaintAction> actions = state.generateActions(plan, config, available, exact, 32);
            rememberWithoutRepeatsBeforeExhaustion(painted, fakePaintIndexes(actions), exact.size(),
                    "fake same-color paint point repeated before full exact pool exhaustion");
        }
        assertEquals(exact.size(), painted.size());
    }

    @Test
    void hybridDetailedImagesResetOnlyTheEmptyStreakNotGestureMemory() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(66));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan empty = planWithDetailPixels(0);
        PreparedSmartPlan detailed = planWithDetailPixels(200);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);
        ArrayList<Integer> painted = new ArrayList<>();

        assertFalse(state.beginImage(empty, config).runFakeClick());
        assertTrue(state.beginImage(empty, config).runFakeClick());
        painted.addAll(fakePaintIndexes(state.generateActions(empty, config, available, exact, 32)));
        int usedBeforeDetailed = state.usedGesturePointsForTesting();
        assertEquals(0, state.beginImage(detailed, config).streak());
        assertEquals(usedBeforeDetailed, state.usedGesturePointsForTesting(),
                "detailed images should reset only the streak, not the same-session fake path memory");
        assertFalse(state.beginImage(empty, config).runFakeClick());
        assertTrue(state.beginImage(empty, config).runFakeClick());
        List<Integer> afterReset = fakePaintIndexes(state.generateActions(empty, config, available, exact, 32));

        for (int index : afterReset) {
            assertFalse(painted.contains(index),
                    "fake path after detailed reset should continue instead of replaying old pixels: " + index);
        }
    }

    @Test
    void randomizedTemplateStartVariesAcrossSeeds() {
        Set<Integer> starts = new HashSet<>();

        for (int seed = 0; seed < 50; seed++) {
            starts.add(new SmartFakeClickState(new Random(seed)).templatePathCursorForTesting());
        }

        assertTrue(starts.size() > 1, "fake gesture template should not always start at path zero");
    }

    @Test
    void largeEmptyAndHybridSimulationKeepsStepCountsAndNoRepeatsUntilExhaustion() {
        ConfigManager.Config config = ConfigManager.Config.defaults();
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);

        for (int seed = 100; seed < 140; seed++) {
            SmartFakeClickState state = new SmartFakeClickState(new Random(seed));
            Set<Integer> painted = new HashSet<>();
            int fakeImages = simulateEmptyImagesUntilExhaustion(state, config, available, exact, 500, painted);
            assertTrue(fakeImages > 90, "hundreds-style empty simulation should execute many fake image gestures");
            assertEquals(exact.size(), painted.size(), "no repeat should occur until the exact fill pool is exhausted");
        }

        for (int seed = 200; seed < 240; seed++) {
            SmartFakeClickState state = new SmartFakeClickState(new Random(seed));
            Set<Integer> painted = new HashSet<>();
            int fakeImages = 0;
            fakeImages += simulateEmptyImagesUntilExhaustion(state, config, available, exact, 6, painted);
            for (int i = 0; i < 4; i++) {
                assertFalse(state.beginImage(planWithDetailPixels(160 + i), config).runFakeClick());
            }
            fakeImages += simulateEmptyImagesUntilExhaustion(state, config, available, exact, 300, painted);
            assertTrue(fakeImages > 80, "hybrid simulation should resume fake gestures after the second new empty image");
            assertEquals(exact.size(), painted.size(), "hybrid fake path should avoid repeats until exhaustion");
        }
    }

    private static PreparedSmartPlan planWithDetailPixels(int detailPixels) {
        ArrayList<Integer> all = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            all.add(i);
        }
        ArrayList<PaintAction> actions = new ArrayList<>();
        for (int i = 0; i < detailPixels; i++) {
            actions.add(new PaintAction(PaintActionType.MANUAL_CLICK, RED, RED.item(), List.of(i),
                    i, i, i, 1, "test detail"));
        }
        PaintAction baseCoat = new PaintAction(PaintActionType.BUCKET_BASE_COAT, BASE, BASE.item(), all,
                500, 500, 500, 1, "test basecoat");
        return new PreparedSmartPlan(baseCoat, actions,
                SmartBucketAnchorPlanner.naturalCandidates(32, 32), null, null);
    }

    private static ArtMapColor color(String name, String item, boolean tool) {
        return new ArtMapColor(name, name, Identifier.of(item), null, 0xFFFFFF, tool);
    }

    private static void simulateEmptyBatch(SmartFakeClickState state, ConfigManager.Config config,
                                           PreparedSmartPlan plan, List<ArtMapColor> available,
                                           List<Integer> exact, int images, List<Integer> paintedOut) {
        for (int image = 0; image < images; image++) {
            SmartFakeClickState.Decision decision = state.beginImage(plan, config);
            if (decision.runFakeClick()) {
                paintedOut.addAll(fakePaintIndexes(state.generateActions(plan, config, available, exact, 32)));
            }
        }
    }

    private static int simulateEmptyImagesUntilExhaustion(SmartFakeClickState state, ConfigManager.Config config,
                                                          List<ArtMapColor> available, List<Integer> exact,
                                                          int imageCount, Set<Integer> painted) {
        int fakeImages = 0;
        PreparedSmartPlan empty = planWithDetailPixels(0);
        for (int image = 0; image < imageCount && painted.size() < exact.size(); image++) {
            SmartFakeClickState.Decision decision = state.beginImage(empty, config);
            if (!decision.runFakeClick()) {
                continue;
            }
            int lastBeforeActions = state.lastGestureIndex();
            boolean nearbyDragAvailable = hasNearbyAdjacentUnusedRun(painted, exact, lastBeforeActions, 32);
            List<PaintAction> actions = state.generateActions(empty, config, available, exact, 32);
            fakeImages++;
            int fakePixels = 0;
            boolean hasClick = false;
            boolean hasDrag = false;
            ArrayList<Integer> indexesThisImage = new ArrayList<>();
            for (PaintAction action : actions) {
                if (action.type() == PaintActionType.FAKE_COLOR_SWAP) {
                    continue;
                }
                fakePixels += action.affectedCount();
                hasClick |= action.type() == PaintActionType.MANUAL_CLICK;
                hasDrag |= action.type() == PaintActionType.DRAG_RUN;
                indexesThisImage.addAll(action.indexes());
            }
            rememberWithoutRepeatsBeforeExhaustion(painted, indexesThisImage, exact.size(),
                    "fake paint point repeated before full exact pool exhaustion");
            assertTrue(fakePixels >= 5 && fakePixels <= 10,
                    "fake painted pixel count should stay 5-10, got " + fakePixels + " actions=" + actions);
            assertTrue(hasClick);
            if (nearbyDragAvailable) {
                assertTrue(hasDrag,
                        "fake gesture should include a drag when nearby adjacent unused calibrated points are available; "
                                + "painted=" + painted.size() + " capacity=" + exact.size() + " actions=" + actions);
            }
        }
        return fakeImages;
    }

    private static List<Integer> fakePaintIndexes(List<PaintAction> actions) {
        ArrayList<Integer> indexes = new ArrayList<>();
        for (PaintAction action : actions) {
            if (action.type() == PaintActionType.MANUAL_CLICK || action.type() == PaintActionType.DRAG_RUN) {
                indexes.addAll(action.indexes());
            }
        }
        return indexes;
    }

    private static void rememberWithoutRepeatsBeforeExhaustion(Set<Integer> painted, List<Integer> indexes,
                                                               int capacity, String message) {
        int remainingUniqueSlots = capacity - painted.size();
        int checked = 0;
        for (int index : indexes) {
            if (checked >= remainingUniqueSlots) {
                return;
            }
            assertTrue(painted.add(index), message + ": " + index);
            checked++;
        }
    }

    private static boolean hasNearbyAdjacentUnusedRun(Set<Integer> painted, List<Integer> exact,
                                                      int lastIndex, int canvasWidth) {
        if (lastIndex < 0) {
            return true;
        }
        Set<Integer> exactSet = new HashSet<>(exact);
        for (int point : exact) {
            if (painted.contains(point) || !near(point, lastIndex, canvasWidth)) {
                continue;
            }
            int[] neighbors = {point + 1, point - 1, point + canvasWidth, point - canvasWidth};
            for (int neighbor : neighbors) {
                if (exactSet.contains(neighbor) && !painted.contains(neighbor)
                        && near(neighbor, lastIndex, canvasWidth)) {
                    int dx = Math.abs(CanvasMath.toX(point, canvasWidth) - CanvasMath.toX(neighbor, canvasWidth));
                    int dy = Math.abs(CanvasMath.toY(point, canvasWidth) - CanvasMath.toY(neighbor, canvasWidth));
                    if (dx + dy == 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean near(int point, int reference, int canvasWidth) {
        int rowDistance = Math.abs(CanvasMath.toY(point, canvasWidth) - CanvasMath.toY(reference, canvasWidth));
        int columnDistance = Math.abs(CanvasMath.toX(point, canvasWidth) - CanvasMath.toX(reference, canvasWidth));
        return rowDistance <= 8 && columnDistance <= 12;
    }
}

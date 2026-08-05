package com.artmapcolorassistant;

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
    void bucketHeavyStreakStartsFakeGesturesOnFourthEligibleImage() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(1));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);

        assertFalse(state.beginImage(plan, config).runFakeClick());
        assertFalse(state.beginImage(plan, config).runFakeClick());
        assertFalse(state.beginImage(plan, config).runFakeClick());
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
    }

    @Test
    void tinyDetailImagesStillCountAsBucketHeavy() {
        SmartFakeClickState.Analysis analysis =
                SmartFakeClickState.analyze(planWithDetailPixels(55), ConfigManager.Config.defaults());

        assertTrue(analysis.bucketHeavy());
        assertEquals(55, analysis.detailPixels());
        assertTrue(analysis.baseCoatDominance() > 0.90D);
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
            } else {
                assertEquals(BASE, action.color());
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
    void hundredRepeatedBucketHeavyImagesAvoidRepeatingFakePaintPointsBeforePoolExhaustion() {
        SmartFakeClickState state = new SmartFakeClickState(new Random(6));
        ConfigManager.Config config = ConfigManager.Config.defaults();
        PreparedSmartPlan plan = planWithDetailPixels(0);
        List<ArtMapColor> available = List.of(BASE, RED, BLUE, GREEN, COAL);
        List<Integer> exact = SmartBucketAnchorPlanner.fillClickCandidates(32, 32);
        Set<Integer> painted = new HashSet<>();

        for (int image = 0; image < 100; image++) {
            SmartFakeClickState.Decision decision = state.beginImage(plan, config);
            if (!decision.runFakeClick()) {
                continue;
            }
            List<PaintAction> actions = state.generateActions(plan, config, available, exact, 32);
            for (PaintAction action : actions) {
                if (action.type() != PaintActionType.MANUAL_CLICK && action.type() != PaintActionType.DRAG_RUN) {
                    continue;
                }
                for (int index : action.indexes()) {
                    assertTrue(painted.add(index),
                            "fake same-color paint point repeated before full exact pool exhaustion: " + index);
                }
            }
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
}

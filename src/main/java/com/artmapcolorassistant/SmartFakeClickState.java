package com.artmapcolorassistant;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class SmartFakeClickState {
    static final int DEFAULT_STREAK_THRESHOLD = 1;
    static final double DEFAULT_DOMINANCE_THRESHOLD = 1.0D;
    static final int DEFAULT_MAX_DETAIL_PIXELS = 0;
    static final int LEGACY_DEFAULT_STREAK_THRESHOLD = 3;
    static final double LEGACY_DEFAULT_DOMINANCE_THRESHOLD = 0.90D;
    static final int LEGACY_DEFAULT_MAX_DETAIL_PIXELS = 64;
    static final int DEFAULT_MIN_STEPS = 5;
    static final int DEFAULT_MAX_STEPS = 10;
    static final int DEFAULT_MIN_COLOR_SWAPS = 2;
    static final int DEFAULT_MAX_COLOR_SWAPS = 3;

    private final Random random;
    private int bucketHeavyStreak;
    private int templatePathCursor;
    private int templatePointCursor;
    private int lastGestureIndex = -1;
    private final Set<Integer> usedGesturePoints = new HashSet<>();

    SmartFakeClickState() {
        this(new Random());
    }

    SmartFakeClickState(Random random) {
        this.random = random == null ? new Random() : random;
        this.templatePathCursor = this.random.nextInt(TEMPLATE_PATHS.length);
    }

    Decision beginImage(PreparedSmartPlan plan, ConfigManager.Config config) {
        Analysis analysis = analyze(plan, config);
        if (!config.smartFakeClickEnabled() || !analysis.bucketHeavy()) {
            bucketHeavyStreak = 0;
            return new Decision(analysis, bucketHeavyStreak, false);
        }
        bucketHeavyStreak++;
        return new Decision(analysis, bucketHeavyStreak,
                bucketHeavyStreak > Math.max(1, config.smartFakeClickStreakThreshold()));
    }

    List<PaintAction> generateActions(PreparedSmartPlan plan, ConfigManager.Config config,
                                      List<ArtMapColor> availableColors, List<Integer> exactAnchors,
                                      int canvasWidth) {
        if (plan == null || plan.baseCoat() == null || plan.baseCoat().color() == null
                || exactAnchors == null || exactAnchors.isEmpty()) {
            return List.of();
        }
        ArtMapColor baseColor = plan.baseCoat().color();
        ArrayList<PaintAction> actions = new ArrayList<>();
        for (ArtMapColor decoy : decoyColors(baseColor, availableColors, config)) {
            int seed = lastGestureIndex >= 0 ? lastGestureIndex : plan.baseCoat().seedIndex();
            actions.add(new PaintAction(PaintActionType.FAKE_COLOR_SWAP, decoy, decoy.item(), List.of(),
                    seed, seed, seed, ActionCostModel.manual(config), "fake-click decoy color swap"));
        }

        Set<Integer> exact = new HashSet<>(exactAnchors);
        int totalSteps = randomBetween(config.smartFakeClickMinSteps(), config.smartFakeClickMaxSteps());
        int remainingSteps = totalSteps;
        if (totalSteps > 2) {
            int dragSteps = Math.min(totalSteps - 1,
                    2 + random.nextInt(Math.min(4, Math.max(1, totalSteps - 2))));
            List<Integer> run = nextTemplateRun(exact, dragSteps, canvasWidth);
            if (run.size() <= 1 && dragSteps > 2) {
                run = nextTemplateRun(exact, 2, canvasWidth);
            }
            if (run.size() > 1) {
                actions.add(new PaintAction(PaintActionType.DRAG_RUN, baseColor, baseColor.item(), run,
                        run.getFirst(), run.getFirst(), run.getLast(), ActionCostModel.drag(config, run.size()),
                        "same-color fake drag gesture"));
                lastGestureIndex = run.getLast();
                remainingSteps -= run.size();
            } else {
                addFallbackClicks(actions, baseColor, config, exact, canvasWidth, dragSteps);
                remainingSteps -= dragSteps;
            }
        }
        addFallbackClicks(actions, baseColor, config, exact, canvasWidth, remainingSteps);
        return List.copyOf(actions);
    }

    int lastGestureIndex() {
        return lastGestureIndex;
    }

    int templatePathCursorForTesting() {
        return templatePathCursor;
    }

    int templatePointCursorForTesting() {
        return templatePointCursor;
    }

    int usedGesturePointsForTesting() {
        return usedGesturePoints.size();
    }

    static int templatePointCountForTesting() {
        return TEMPLATE_POINT_COUNT;
    }

    static boolean templateContainsForTesting(int index) {
        for (int[] path : TEMPLATE_PATHS) {
            for (int point : path) {
                if (point == index) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addFallbackClicks(List<PaintAction> actions, ArtMapColor baseColor, ConfigManager.Config config,
                                   Set<Integer> exact, int canvasWidth, int count) {
        for (int i = 0; i < count; i++) {
            int index = nextTemplatePoint(exact, canvasWidth);
            actions.add(new PaintAction(PaintActionType.MANUAL_CLICK, baseColor, baseColor.item(), List.of(index),
                    index, index, index, ActionCostModel.manual(config), "same-color fake fallback click"));
            lastGestureIndex = index;
        }
    }

    private List<ArtMapColor> decoyColors(ArtMapColor baseColor, List<ArtMapColor> availableColors,
                                          ConfigManager.Config config) {
        if (availableColors == null || availableColors.isEmpty()) {
            return List.of();
        }
        ArrayList<ArtMapColor> candidates = new ArrayList<>();
        for (ArtMapColor color : availableColors) {
            if (color == null || color.tool() || SmartCanvas.sameColor(color, baseColor)
                    || isCoal(color) || isBucket(color)) {
                continue;
            }
            candidates.add(color);
        }
        shuffle(candidates);
        int max = Math.min(candidates.size(), Math.max(0, config.smartFakeClickMaxColorSwaps()));
        int min = Math.min(max, Math.max(0, config.smartFakeClickMinColorSwaps()));
        int count = max <= min ? min : min + random.nextInt(max - min + 1);
        return List.copyOf(candidates.subList(0, count));
    }

    private static boolean isCoal(ArtMapColor color) {
        return Identifier.of("minecraft", "coal").equals(color.item());
    }

    private static boolean isBucket(ArtMapColor color) {
        return Identifier.of("minecraft", "bucket").equals(color.item());
    }

    private int nextTemplatePoint(Set<Integer> exact, int canvasWidth) {
        for (int attempts = 0; attempts < TEMPLATE_POINT_COUNT * 2; attempts++) {
            int[] path = TEMPLATE_PATHS[Math.floorMod(templatePathCursor, TEMPLATE_PATHS.length)];
            int point = path[Math.floorMod(templatePointCursor, path.length)];
            templatePointCursor++;
            if (templatePointCursor >= path.length) {
                templatePointCursor = 0;
                templatePathCursor++;
            }
            if (exact.contains(point) && !usedGesturePoints.contains(point) && localEnough(point, canvasWidth)) {
                return rememberGesturePoint(point, exact);
            }
        }
        return rememberGesturePoint(nearestUnusedExactPoint(exact, canvasWidth), exact);
    }

    private List<Integer> nextTemplateRun(Set<Integer> exact, int requestedLength, int canvasWidth) {
        int length = Math.max(2, requestedLength);
        for (int pathOffset = 0; pathOffset < TEMPLATE_PATHS.length; pathOffset++) {
            int[] path = TEMPLATE_PATHS[Math.floorMod(templatePathCursor + pathOffset, TEMPLATE_PATHS.length)];
            for (int start = 0; start < path.length; start++) {
                ArrayList<Integer> run = new ArrayList<>();
                int previous = -1;
                for (int offset = 0; offset < path.length && run.size() < length; offset++) {
                    int point = path[(start + offset) % path.length];
                    if (!exact.contains(point) || usedGesturePoints.contains(point) || run.contains(point)
                            || !localEnough(point, canvasWidth)) {
                        run.clear();
                        previous = -1;
                        continue;
                    }
                    if (previous >= 0 && !adjacent(previous, point, canvasWidth)) {
                        run.clear();
                    }
                    run.add(point);
                    previous = point;
                }
                if (run.size() >= length) {
                    templatePathCursor = Math.floorMod(templatePathCursor + pathOffset, TEMPLATE_PATHS.length);
                    templatePointCursor = (start + run.size()) % path.length;
                    for (int point : run) {
                        rememberGesturePoint(point, exact);
                    }
                    return List.copyOf(run);
                }
            }
        }
        List<Integer> localRun = nearestAdjacentRun(exact, length, canvasWidth);
        if (localRun.size() >= length) {
            for (int point : localRun) {
                rememberGesturePoint(point, exact);
            }
            return localRun;
        }
        return List.of();
    }

    private boolean localEnough(int index, int canvasWidth) {
        if (lastGestureIndex < 0) {
            return true;
        }
        int rowDistance = Math.abs(CanvasMath.toY(index, canvasWidth) - CanvasMath.toY(lastGestureIndex, canvasWidth));
        int columnDistance = Math.abs(CanvasMath.toX(index, canvasWidth) - CanvasMath.toX(lastGestureIndex, canvasWidth));
        return rowDistance <= 8 && columnDistance <= 12;
    }

    private int nearestUnusedExactPoint(Set<Integer> exact, int canvasWidth) {
        if (exact == null || exact.isEmpty()) {
            return 0;
        }
        clearUsedIfExhausted(exact);
        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int point : exact) {
            if (usedGesturePoints.contains(point)) {
                continue;
            }
            int score = lastGestureIndex < 0 ? random.nextInt(8)
                    : Math.abs(CanvasMath.toY(point, canvasWidth) - CanvasMath.toY(lastGestureIndex, canvasWidth))
                    + Math.abs(CanvasMath.toX(point, canvasWidth) - CanvasMath.toX(lastGestureIndex, canvasWidth));
            if (score < bestScore || (score == bestScore && random.nextBoolean())) {
                best = point;
                bestScore = score;
            }
        }
        return best >= 0 ? best : exact.iterator().next();
    }

    private List<Integer> nearestAdjacentRun(Set<Integer> exact, int length, int canvasWidth) {
        if (exact == null || exact.isEmpty() || length < 2) {
            return List.of();
        }
        clearUsedIfExhausted(exact);
        ArrayList<Integer> starts = new ArrayList<>(exact);
        starts.removeIf(point -> usedGesturePoints.contains(point) || !localEnough(point, canvasWidth));
        starts.sort((left, right) -> Integer.compare(distanceFromLast(left, canvasWidth), distanceFromLast(right, canvasWidth)));
        int limit = starts.size();
        for (int i = 0; i < limit; i++) {
            int start = starts.get(i);
            ArrayList<Integer> directions = new ArrayList<>(List.of(1, -1, canvasWidth, -canvasWidth));
            shuffle(directions);
            for (int direction : directions) {
                ArrayList<Integer> run = new ArrayList<>();
                int previous = -1;
                for (int step = 0; step < length; step++) {
                    int point = start + direction * step;
                    if (!exact.contains(point) || usedGesturePoints.contains(point) || run.contains(point)
                            || !localEnough(point, canvasWidth)) {
                        run.clear();
                        break;
                    }
                    if (previous >= 0 && !adjacent(previous, point, canvasWidth)) {
                        run.clear();
                        break;
                    }
                    run.add(point);
                    previous = point;
                }
                if (run.size() >= length) {
                    return List.copyOf(run);
                }
            }
        }
        return List.of();
    }

    private int distanceFromLast(int point, int canvasWidth) {
        if (lastGestureIndex < 0) {
            return random.nextInt(8);
        }
        return Math.abs(CanvasMath.toY(point, canvasWidth) - CanvasMath.toY(lastGestureIndex, canvasWidth))
                + Math.abs(CanvasMath.toX(point, canvasWidth) - CanvasMath.toX(lastGestureIndex, canvasWidth));
    }

    private int rememberGesturePoint(int point, Set<Integer> exact) {
        if (exact != null && !exact.isEmpty() && usedGesturePoints.size() >= exact.size()) {
            usedGesturePoints.clear();
        }
        usedGesturePoints.add(point);
        return point;
    }

    private void clearUsedIfExhausted(Set<Integer> exact) {
        if (exact != null && !exact.isEmpty() && usedGesturePoints.size() >= exact.size()) {
            usedGesturePoints.clear();
        }
    }

    private static boolean adjacent(int first, int second, int canvasWidth) {
        int dx = Math.abs(CanvasMath.toX(first, canvasWidth) - CanvasMath.toX(second, canvasWidth));
        int dy = Math.abs(CanvasMath.toY(first, canvasWidth) - CanvasMath.toY(second, canvasWidth));
        return dx + dy == 1;
    }

    private int randomBetween(int min, int max) {
        int low = Math.max(1, min);
        int high = Math.max(low, max);
        return low + random.nextInt(high - low + 1);
    }

    private <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    static Analysis analyze(PreparedSmartPlan plan, ConfigManager.Config config) {
        if (plan == null || plan.baseCoat() == null || !plan.baseCoat().bucket()) {
            return new Analysis(0, 0, 0.0D, false);
        }
        int paintable = Math.max(1, plan.baseCoat().affectedCount());
        Set<Integer> detail = new HashSet<>();
        for (PaintAction action : plan.actions()) {
            if (action.bucket() || action.type() == PaintActionType.FAKE_COLOR_SWAP) {
                continue;
            }
            detail.addAll(action.indexes());
        }
        double dominance = 1.0D - (detail.size() / (double) paintable);
        boolean bucketHeavy = dominance >= config.smartFakeClickDominanceThreshold()
                || detail.size() <= config.smartFakeClickMaxDetailPixels();
        return new Analysis(paintable, detail.size(), dominance, bucketHeavy);
    }

    record Analysis(int paintablePixels, int detailPixels, double baseCoatDominance, boolean bucketHeavy) {
    }

    record Decision(Analysis analysis, int streak, boolean runFakeClick) {
    }

    private static final int[][] TEMPLATE_PATHS = new int[][]{
            {24, 25, 56, 57, 88, 55, 89, 87, 119, 118, 150, 117, 182, 149, 116, 214, 181, 148, 84, 246, 213, 180, 83, 278, 245, 212, 310, 277, 244, 211, 311, 342, 309, 276, 243, 343, 374, 341, 308, 275, 242, 344, 375, 406, 340, 307, 274, 241, 345, 376, 407, 405, 372, 273, 240, 346, 377, 404, 272, 239, 347, 378, 314, 409, 304, 271, 238, 207, 379, 315, 410, 303, 270, 206, 175, 411, 283, 442, 335, 302, 205, 174, 143, 443, 251, 367, 204, 173, 142, 144, 111, 444, 475, 203, 172, 141, 110, 112, 445, 476, 507, 171, 140, 109, 113, 446, 477, 539, 506, 139, 478, 571, 538, 479, 572, 603, 570, 511, 604, 635, 602, 569, 605, 636, 667, 634, 601, 637, 668, 666, 633, 600, 669, 698, 665, 632, 730, 697, 664, 762, 729, 696, 794, 761, 728, 695, 826, 793, 727, 827, 759, 726, 859, 758, 725, 891, 724, 693, 892, 923, 723, 692, 924, 955, 722, 691, 660, 956, 954, 754, 659, 628, 986, 953, 753, 627, 985, 752, 595, 984, 784, 563, 1016, 983, 816, 783, 562, 531, 1015, 982, 817, 848, 815, 530, 532, 499, 981, 849, 529, 498, 500, 467, 1013, 949, 850, 881, 528, 497, 466, 501, 468, 435, 882, 527, 496, 883, 526, 495, 464, 494, 463, 493, 462, 431, 461, 430, 460},
            {6, 38, 70, 102, 134, 166, 198, 230, 231, 262, 263, 264, 295, 296, 328, 360, 392, 424, 425, 456, 457, 455, 487, 454, 519, 486, 551, 518, 583, 615, 616, 647, 648, 679, 680, 711, 712, 743, 744, 775, 742, 745, 776, 807, 774, 741, 746, 777, 808, 773, 740, 747, 778, 772, 739, 748, 779, 771, 749, 716, 770, 750, 717, 684, 802, 769, 685, 652, 834, 801, 651, 866, 833, 898, 930, 931, 962, 932, 963, 933, 964, 995, 934, 965, 901, 935, 966, 902, 967, 903, 968, 999, 1000, 1001, 1002, 1003, 970, 971},
            {37, 69, 101, 133, 165, 197, 164, 229, 196, 163, 261, 228, 260, 292, 324, 356, 388, 420, 421, 452, 419, 422, 453, 484, 451, 423, 485, 516, 483, 450, 391, 517, 548, 515, 359, 549, 580, 547, 358, 327, 550, 326, 294},
            {809, 810, 841, 811, 842, 873, 840, 843, 874, 905, 872, 875, 906, 937, 904, 871, 907, 938, 936, 939, 940, 941, 972, 973, 1004, 974, 1005, 1006},
            {525, 557, 558, 589, 559, 590, 621, 560, 591, 622, 653, 620, 561, 654, 619, 686, 618, 718, 650, 617, 682, 649, 683, 714, 681, 715, 713},
            {12, 13, 44, 45, 76, 77, 75, 74, 73, 105, 137, 104, 138, 169, 136, 170, 201, 168, 135, 202, 233, 200, 167, 232, 199},
            {120, 121, 152, 153, 184, 151, 154, 185, 216, 183, 186, 217, 248, 215, 187, 218, 249, 247, 219, 250, 220, 282},
            {162, 194, 195, 226, 227, 258, 225, 259, 290, 257, 224, 291, 322, 289, 256, 323, 354, 288, 355, 386, 387, 418},
            {699, 700, 731, 701, 732, 763, 733, 764, 795, 765, 796, 797, 828, 798, 799, 830, 831, 767, 862, 863, 735},
            {235, 236, 267, 237, 268, 299, 266, 269, 300, 265, 301, 332, 333, 334, 365, 366, 397, 398, 429, 428},
            {756, 788, 789, 820, 790, 821, 852, 791, 822, 853, 823, 854, 824, 855, 856, 887, 857, 888},
            {913, 914, 945, 915, 946, 977, 944, 947, 978, 1009, 976, 948, 979, 1010, 1008, 980, 1011, 1012}
    };

    private static final int TEMPLATE_POINT_COUNT = 561;
}

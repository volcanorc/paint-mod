package com.artmapcolorassistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SmartPaintPlanner {
    private static final int MAX_PREVIEW_ACTIONS = 4096;

    public Optional<PaintAction> nextAction(SmartCanvas canvas, ConfigManager.Config config) {
        if (canvas == null || canvas.wrongCount() == 0) {
            return Optional.empty();
        }
        if (config.bucketEnabled() && config.smartBaseCoatEnabled() && !canvas.baseCoatDecisionDone() && canvas.trusted()) {
            PaintAction base = plannedBaseCoatAction(canvas, config);
            if (base != null) {
                return Optional.of(base);
            }
            canvas.markBaseCoatDecisionDone();
        }
        if (config.bucketEnabled()) {
            PaintAction bucket = largestSafeBucket(canvas, config);
            if (bucket != null) {
                return Optional.of(bucket);
            }
        }
        PaintAction drag = longestDragRun(canvas, config);
        if (drag != null) {
            return Optional.of(drag);
        }
        return manualFallback(canvas, config);
    }

    public SmartPreview preview(PaintSession session, ConfigManager.Config config) {
        SmartCanvas canvas = SmartCanvas.fresh(session, config);
        int originalWrong = canvas.wrongCount();
        int believedManualTicks = originalWrong * ActionCostModel.manual(config);
        int oldTicks = canvas.paintableCount() * ActionCostModel.manual(config);
        ArtMapColor dominant = dominantTargetColor(canvas);
        SmartPlanEstimate estimate = bestInitialRoute(canvas, config);
        String risk = riskLevel(oldTicks, estimate.totalTicks(), estimate.bucketActions(), estimate.unsafeBucketCandidates(), config);
        return new SmartPreview(oldTicks, believedManualTicks, estimate.totalTicks(), oldTicks - estimate.totalTicks(),
                estimate.manualActions(), estimate.dragActions(), estimate.bucketActions(), estimate.unsafeBucketCandidates(),
                originalWrong, ComponentAnalyzer.wrongPixelComponents(SmartCanvas.fresh(session, config)).size(),
                dominant, estimate.selectedBaseCoatColor(), estimate.baseCoatAction() != null, risk);
    }

    public boolean shouldUseSmart(PaintSession session, ConfigManager.Config config) {
        if (!config.smartEnabled()) {
            return false;
        }
        SmartPreview preview = preview(session, config);
        return preview.expectedSavingsTicks() > ActionCostModel.manual(config) * 4
                && !"high".equals(preview.riskLevel());
    }

    public ArtMapColor dominantTargetColor(SmartCanvas canvas) {
        Map<ArtMapColor, Integer> counts = new HashMap<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (canvas.skipped(i)) {
                continue;
            }
            ArtMapColor color = canvas.targetColor(i);
            if (color != null) {
                counts.put(color, counts.getOrDefault(color, 0) + 1);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private PaintAction plannedBaseCoatAction(SmartCanvas canvas, ConfigManager.Config config) {
        if (canvas.plannedBaseCoat() != null) {
            return canvas.plannedBaseCoat();
        }
        SmartPlanEstimate estimate = bestInitialRoute(canvas, config);
        if (estimate.baseCoatAction() == null) {
            return null;
        }
        canvas.setPlannedBaseCoat(estimate.baseCoatAction());
        return estimate.baseCoatAction();
    }

    private SmartPlanEstimate bestInitialRoute(SmartCanvas canvas, ConfigManager.Config config) {
        SmartPlanEstimate noBaseCoat = simulateRoute(canvas.copy(), config, null, 0);
        SmartPlanEstimate best = noBaseCoat;
        if (!canConsiderBaseCoat(canvas, config)) {
            return noBaseCoat;
        }
        List<ArtMapColor> candidates = orderedTargetColors(canvas);
        int order = 1;
        for (ArtMapColor candidate : candidates) {
            if (SmartCanvas.sameColor(candidate, canvas.blankColor())) {
                order++;
                continue;
            }
            PaintAction base = baseCoatAction(canvas, candidate, config);
            if (base == null) {
                order++;
                continue;
            }
            SmartCanvas simulated = canvas.copy();
            SmartPlanEstimate estimate = simulateRoute(simulated, config, base, order);
            if (estimate.betterThan(best)) {
                best = estimate;
            }
            order++;
        }
        return best.totalTicks() + ActionCostModel.manual(config) <= noBaseCoat.totalTicks() ? best : noBaseCoat;
    }

    private boolean canConsiderBaseCoat(SmartCanvas canvas, ConfigManager.Config config) {
        if (!config.bucketEnabled() || !config.smartBaseCoatEnabled() || !canvas.trusted() || canvas.baseCoatDecisionDone()) {
            return false;
        }
        for (int i = 0; i < canvas.size(); i++) {
            if (canvas.skipped(i)) {
                return false;
            }
        }
        return true;
    }

    private List<ArtMapColor> orderedTargetColors(SmartCanvas canvas) {
        LinkedHashMap<ArtMapColor, Boolean> colors = new LinkedHashMap<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (!canvas.skipped(i) && canvas.targetColor(i) != null) {
                colors.putIfAbsent(canvas.targetColor(i), Boolean.TRUE);
            }
        }
        return List.copyOf(colors.keySet());
    }

    private PaintAction baseCoatAction(SmartCanvas canvas, ArtMapColor color, ConfigManager.Config config) {
        ArrayList<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (!canvas.skipped(i)) {
                indexes.add(i);
            }
        }
        if (indexes.size() < config.smartBucketThreshold()) {
            return null;
        }
        int seed = indexes.stream()
                .filter(index -> SmartCanvas.sameColor(canvas.targetColor(index), color))
                .findFirst()
                .orElse(indexes.getFirst());
        return new PaintAction(PaintActionType.BUCKET_BASE_COAT, color, color.item(), List.copyOf(indexes),
                seed, seed, seed, ActionCostModel.bucket(config), "evaluated base coat");
    }

    private SmartPlanEstimate simulateRoute(SmartCanvas canvas, ConfigManager.Config config, PaintAction baseCoat, int baseCoatOrder) {
        int manual = 0;
        int drag = 0;
        int bucket = 0;
        int bucketPixels = 0;
        int unsafe = 0;
        int ticks = 0;
        if (baseCoat != null) {
            ticks += baseCoat.estimatedTicks();
            bucket++;
            bucketPixels += baseCoat.affectedCount();
            canvas.apply(baseCoat);
        }
        for (int i = 0; i < MAX_PREVIEW_ACTIONS && canvas.wrongCount() > 0; i++) {
            unsafe += countUnsafeBucketCandidates(canvas, config);
            Optional<PaintAction> action = nextNonBaseCoatAction(canvas, config);
            if (action.isEmpty()) {
                break;
            }
            PaintAction current = action.get();
            ticks += current.estimatedTicks();
            if (current.type() == PaintActionType.MANUAL_CLICK) {
                manual++;
            } else if (current.type() == PaintActionType.DRAG_RUN) {
                drag++;
            } else {
                bucket++;
                bucketPixels += current.affectedCount();
            }
            canvas.apply(current);
        }
        if (canvas.wrongCount() > 0) {
            ticks += canvas.wrongCount() * ActionCostModel.manual(config);
            manual += canvas.wrongCount();
        }
        return new SmartPlanEstimate(baseCoat, baseCoat == null ? null : baseCoat.color(), ticks, manual, drag,
                bucket, bucketPixels, unsafe, canvas.wrongCount(), baseCoatOrder);
    }

    private Optional<PaintAction> nextNonBaseCoatAction(SmartCanvas canvas, ConfigManager.Config config) {
        if (canvas == null || canvas.wrongCount() == 0) {
            return Optional.empty();
        }
        if (config.bucketEnabled()) {
            PaintAction bucket = largestSafeBucket(canvas, config);
            if (bucket != null) {
                return Optional.of(bucket);
            }
        }
        PaintAction drag = longestDragRun(canvas, config);
        if (drag != null) {
            return Optional.of(drag);
        }
        return manualFallback(canvas, config);
    }

    private PaintAction largestSafeBucket(SmartCanvas canvas, ConfigManager.Config config) {
        if (!canvas.trusted()) {
            return null;
        }
        Set<Integer> checked = new HashSet<>();
        PaintAction best = null;
        for (int i = 0; i < canvas.size(); i++) {
            if (checked.contains(i) || !canvas.wrong(i)) {
                continue;
            }
            List<Integer> region = FloodFill4.fill(canvas, i);
            checked.addAll(region);
            if (region.size() < config.smartBucketThreshold()) {
                continue;
            }
            ArtMapColor target = canvas.targetColor(i);
            if (target == null || SmartCanvas.sameColor(target, canvas.currentColor(i))) {
                continue;
            }
            if (!safeBucketRegion(canvas, region, target)) {
                continue;
            }
            PaintAction candidate = new PaintAction(PaintActionType.BUCKET_FILL, target, target.item(), List.copyOf(region),
                    i, i, i, ActionCostModel.bucket(config), "safe 4-way flood region");
            if (best == null || candidate.affectedCount() > best.affectedCount()) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean safeBucketRegion(SmartCanvas canvas, List<Integer> region, ArtMapColor target) {
        if (region.isEmpty()) {
            return false;
        }
        for (int index : region) {
            if (canvas.skipped(index) || !SmartCanvas.sameColor(canvas.targetColor(index), target)) {
                return false;
            }
        }
        return true;
    }

    private PaintAction longestDragRun(SmartCanvas canvas, ConfigManager.Config config) {
        int threshold = Math.max(2, config.smartDragThreshold());
        List<Integer> best = List.of();
        boolean[] consumed = new boolean[canvas.size()];
        for (int i = 0; i < canvas.size(); i++) {
            if (consumed[i] || !canvas.wrong(i)) {
                continue;
            }
            List<Integer> run = ComponentAnalyzer.horizontalRun(canvas, i, threshold);
            for (int index : run) {
                consumed[index] = true;
            }
            if (run.size() > best.size()) {
                best = run;
            }
        }
        if (best.isEmpty()) {
            return null;
        }
        int seed = best.getFirst();
        ArtMapColor color = canvas.targetColor(seed);
        return new PaintAction(PaintActionType.DRAG_RUN, color, color.item(), List.copyOf(best),
                seed, best.getFirst(), best.getLast(), ActionCostModel.drag(config, best.size()), "same-color horizontal run");
    }

    private Optional<PaintAction> manualFallback(SmartCanvas canvas, ConfigManager.Config config) {
        for (int i = 0; i < canvas.size(); i++) {
            if (!canvas.wrong(i)) {
                continue;
            }
            ArtMapColor color = canvas.targetColor(i);
            if (color == null) {
                continue;
            }
            return Optional.of(new PaintAction(PaintActionType.MANUAL_CLICK, color, color.item(), List.of(i),
                    i, i, i, ActionCostModel.manual(config), "single unsafe/detail pixel"));
        }
        return Optional.empty();
    }

    private int countUnsafeBucketCandidates(SmartCanvas canvas, ConfigManager.Config config) {
        if (!config.bucketEnabled() || !canvas.trusted()) {
            return 0;
        }
        int rejected = 0;
        Set<Integer> checked = new HashSet<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (checked.contains(i) || !canvas.wrong(i)) {
                continue;
            }
            List<Integer> region = FloodFill4.fill(canvas, i);
            checked.addAll(region);
            ArtMapColor target = canvas.targetColor(i);
            if (region.size() >= config.smartBucketThreshold()
                    && target != null
                    && !SmartCanvas.sameColor(target, canvas.currentColor(i))
                    && !safeBucketRegion(canvas, region, target)) {
                rejected++;
            }
        }
        return rejected;
    }

    private String riskLevel(int oldTicks, int smartTicks, int bucketActions, int unsafeBuckets, ConfigManager.Config config) {
        if (!config.bucketEnabled() || bucketActions == 0) {
            return smartTicks < oldTicks ? "low" : "none";
        }
        if (unsafeBuckets > 0 || config.bucketClickRepeats() > 1) {
            return "medium";
        }
        if (smartTicks >= oldTicks) {
            return "high";
        }
        return "low";
    }

    private record SmartPlanEstimate(
            PaintAction baseCoatAction,
            ArtMapColor selectedBaseCoatColor,
            int totalTicks,
            int manualActions,
            int dragActions,
            int bucketActions,
            int bucketPixels,
            int unsafeBucketCandidates,
            int remainingWrongPixels,
            int baseCoatOrder
    ) {
        private boolean betterThan(SmartPlanEstimate other) {
            if (totalTicks != other.totalTicks) {
                return totalTicks < other.totalTicks;
            }
            if (manualActions != other.manualActions) {
                return manualActions < other.manualActions;
            }
            if (bucketPixels != other.bucketPixels) {
                return bucketPixels > other.bucketPixels;
            }
            return baseCoatOrder < other.baseCoatOrder;
        }
    }
}

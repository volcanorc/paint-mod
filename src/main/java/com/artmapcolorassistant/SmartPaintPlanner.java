package com.artmapcolorassistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SmartPaintPlanner {
    private static final int MAX_DRAG_TRAIL_VISITS = 64;

    public PreparedSmartPlan prepare(PaintSession session, ConfigManager.Config config) {
        if (session == null) {
            return unavailable(null, "no active painting session");
        }
        SmartCanvas canvas = SmartCanvas.fresh(session, config);
        int originalWrong = canvas.wrongCount();
        int oldTicks = canvas.paintableCount() * ActionCostModel.manual(config);
        DeepBlackAnalyzer.Analysis deepBlack = DeepBlackAnalyzer.analyze(canvas, config);
        ArtMapColor dominant = deepBlack.active() ? DeepBlackAnalyzer.findInkSac(config) : dominantNonBlankTargetColor(canvas);
        List<Integer> anchors = scriptedAnchors(canvas);
        String unavailable = baseCoatUnavailableReason(canvas, config, dominant);
        if (unavailable == null && anchors.size() < SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS) {
            unavailable = "canvas is too small for thirty natural bucket anchors";
        }
        if (unavailable == null && deepBlack.active() && DeepBlackAnalyzer.findCoal(config) == null) {
            unavailable = "deep-black Coal bucket is enabled but minecraft:coal is not configured";
        }
        if (unavailable != null) {
            SmartPreview preview = preview(oldTicks, originalWrong, List.of(), dominant, null, unavailable,
                    ComponentAnalyzer.wrongPixelComponents(canvas).size(), 0, DeepBlackAnalyzer.Analysis.disabled());
            return new PreparedSmartPlan(null, List.of(), anchors, preview, unavailable);
        }

        PaintAction baseCoat = baseCoatAction(canvas, dominant, config);
        canvas.apply(baseCoat);
        List<PaintAction> coalActions = coalBlackActions(canvas, deepBlack, config, baseCoat.seedIndex());
        coalActions.forEach(canvas::apply);
        List<PaintAction> actions = connectedActions(canvas, config);
        ArrayList<PaintAction> allActions = new ArrayList<>(coalActions);
        allActions.addAll(actions);
        int componentCount = ComponentAnalyzer.wrongPixelComponents(canvas).size();
        int smartTicks = baseCoat.estimatedTicks() + allActions.stream().mapToInt(PaintAction::estimatedTicks).sum();
        SmartPreview preview = preview(oldTicks, originalWrong, allActions, dominant, dominant,
                deepBlack.active() ? "deep-black Ink Sac base coat with Coal bucket darkening" : "mandatory dominant-color startup base coat",
                componentCount, smartTicks, deepBlack);
        return new PreparedSmartPlan(baseCoat, allActions, anchors, preview, null);
    }

    public SmartPreview preview(PaintSession session, ConfigManager.Config config) {
        return prepare(session, config).preview();
    }

    public boolean shouldUseSmart(PaintSession session, ConfigManager.Config config) {
        return config.smartEnabled() && prepare(session, config).available();
    }

    public Optional<PaintAction> nextAction(SmartCanvas canvas, ConfigManager.Config config) {
        if (canvas == null || canvas.wrongCount() == 0) {
            return Optional.empty();
        }
        if (!canvas.baseCoatDecisionDone()) {
            ArtMapColor dominant = dominantNonBlankTargetColor(canvas);
            String unavailable = baseCoatUnavailableReason(canvas, config, dominant);
            if (unavailable == null) {
                return Optional.of(baseCoatAction(canvas, dominant, config));
            }
            return Optional.empty();
        }
        List<PaintAction> actions = connectedActions(canvas, config);
        return actions.isEmpty() ? Optional.empty() : Optional.of(actions.getFirst());
    }

    public ArtMapColor dominantTargetColor(SmartCanvas canvas) {
        return dominantNonBlankTargetColor(canvas);
    }

    private PreparedSmartPlan unavailable(SmartPreview preview, String reason) {
        return new PreparedSmartPlan(null, List.of(), List.of(), preview, reason);
    }

    private String baseCoatUnavailableReason(SmartCanvas canvas, ConfigManager.Config config, ArtMapColor dominant) {
        if (!config.bucketEnabled()) {
            return "initial bucket base coat is disabled";
        }
        if (!config.smartBaseCoatEnabled()) {
            return "dominant-color base coat is disabled";
        }
        if (!canvas.trusted()) {
            return "canvas state is not trusted";
        }
        for (int i = 0; i < canvas.size(); i++) {
            if (canvas.skipped(i)) {
                return "transparent SKIP pixels cannot be preserved by a whole-canvas base coat";
            }
        }
        if (dominant == null) {
            return "no nonblank dominant color is available";
        }
        return null;
    }

    private ArtMapColor dominantNonBlankTargetColor(SmartCanvas canvas) {
        LinkedHashMap<ArtMapColor, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < canvas.size(); i++) {
            if (canvas.skipped(i)) {
                continue;
            }
            ArtMapColor color = canvas.targetColor(i);
            if (color != null && !SmartCanvas.sameColor(color, canvas.blankColor())) {
                counts.put(color, counts.getOrDefault(color, 0) + 1);
            }
        }
        ArtMapColor best = null;
        int bestCount = -1;
        for (Map.Entry<ArtMapColor, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private PaintAction baseCoatAction(SmartCanvas canvas, ArtMapColor color, ConfigManager.Config config) {
        ArrayList<Integer> indexes = new ArrayList<>(canvas.size());
        int representative = -1;
        for (int i = 0; i < canvas.size(); i++) {
            if (!canvas.skipped(i)) {
                indexes.add(i);
                if (representative < 0 && SmartCanvas.sameColor(canvas.targetColor(i), color)) {
                    representative = i;
                }
            }
        }
        if (representative < 0 && !indexes.isEmpty()) {
            representative = indexes.getFirst();
        }
        return new PaintAction(PaintActionType.BUCKET_BASE_COAT, color, color.item(), List.copyOf(indexes),
                representative, representative, representative, ActionCostModel.bucket(config),
                "mandatory dominant-color startup base coat");
    }

    private List<PaintAction> coalBlackActions(SmartCanvas canvas, DeepBlackAnalyzer.Analysis deepBlack,
                                               ConfigManager.Config config, int representative) {
        if (!deepBlack.active()) {
            return List.of();
        }
        ArtMapColor coal = DeepBlackAnalyzer.findCoal(config);
        if (coal == null) {
            return List.of();
        }
        ArrayList<Integer> indexes = new ArrayList<>(canvas.size());
        for (int i = 0; i < canvas.size(); i++) {
            if (!canvas.skipped(i)) {
                indexes.add(i);
            }
        }
        int seed = representative >= 0 ? representative : indexes.getFirst();
        ArrayList<PaintAction> actions = new ArrayList<>();
        for (int pass = 1; pass <= deepBlack.coalPasses(); pass++) {
            actions.add(new PaintAction(PaintActionType.COAL_BUCKET_DARKEN, coal, coal.item(), List.copyOf(indexes),
                    seed, seed, seed, ActionCostModel.bucket(config), "Coal bucket deep-black pass " + pass));
        }
        return List.copyOf(actions);
    }

    private List<PaintAction> connectedActions(SmartCanvas canvas, ConfigManager.Config config) {
        ArrayList<PaintAction> actions = new ArrayList<>();
        for (List<Integer> component : ComponentAnalyzer.wrongPixelComponents(canvas)) {
            if (component.size() == 1) {
                int index = component.getFirst();
                ArtMapColor color = canvas.targetColor(index);
                actions.add(new PaintAction(PaintActionType.MANUAL_CLICK, color, color.item(), List.of(index),
                        index, index, index, ActionCostModel.manual(config), "isolated single pixel"));
                continue;
            }
            List<Integer> trail = spanningTrail(canvas, component);
            int offset = 0;
            while (offset < trail.size()) {
                int end = Math.min(trail.size(), offset + MAX_DRAG_TRAIL_VISITS);
                List<Integer> segment = List.copyOf(trail.subList(offset, end));
                int seed = segment.getFirst();
                ArtMapColor color = canvas.targetColor(seed);
                actions.add(new PaintAction(PaintActionType.DRAG_RUN, color, color.item(), segment,
                        seed, seed, segment.getLast(), ActionCostModel.drag(config, segment.size()),
                        "4-way connected same-color trail"));
                if (end == trail.size()) {
                    break;
                }
                offset = end - 1;
            }
        }
        return List.copyOf(actions);
    }

    private List<Integer> spanningTrail(SmartCanvas canvas, List<Integer> component) {
        Set<Integer> members = new HashSet<>(component);
        int start = component.stream()
                .filter(index -> neighbors(canvas, index, members).size() <= 1)
                .min(Integer::compareTo)
                .orElse(component.stream().min(Integer::compareTo).orElseThrow());
        ArrayList<Integer> trail = new ArrayList<>();
        HashSet<Integer> visited = new HashSet<>();
        walkUntilCovered(canvas, start, members, visited, trail);
        return List.copyOf(trail);
    }

    private boolean walkUntilCovered(SmartCanvas canvas, int index, Set<Integer> members,
                                     Set<Integer> visited, List<Integer> trail) {
        visited.add(index);
        trail.add(index);
        if (visited.size() == members.size()) {
            return true;
        }
        for (int neighbor : neighbors(canvas, index, members)) {
            if (visited.contains(neighbor)) {
                continue;
            }
            if (walkUntilCovered(canvas, neighbor, members, visited, trail)) {
                return true;
            }
            trail.add(index);
        }
        return visited.size() == members.size();
    }

    private List<Integer> neighbors(SmartCanvas canvas, int index, Set<Integer> members) {
        int x = CanvasMath.toX(index, canvas.width());
        int y = CanvasMath.toY(index, canvas.width());
        ArrayList<Integer> result = new ArrayList<>(4);
        addNeighbor(result, members, x - 1, y, canvas.width(), canvas.height());
        addNeighbor(result, members, x + 1, y, canvas.width(), canvas.height());
        addNeighbor(result, members, x, y - 1, canvas.width(), canvas.height());
        addNeighbor(result, members, x, y + 1, canvas.width(), canvas.height());
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private void addNeighbor(List<Integer> result, Set<Integer> members, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int index = CanvasMath.toIndex(x, y, width);
        if (members.contains(index)) {
            result.add(index);
        }
    }

    private List<Integer> scriptedAnchors(SmartCanvas canvas) {
        return SmartBucketAnchorPlanner.fillClickCandidates(canvas.width(), canvas.height());
    }

    private SmartPreview preview(int oldTicks, int originalWrong, List<PaintAction> actions,
                                 ArtMapColor dominant, ArtMapColor baseCoat, String reason,
                                 int componentCount, int smartTicks, DeepBlackAnalyzer.Analysis deepBlack) {
        int manual = 0;
        int drags = 0;
        int coalBuckets = 0;
        for (PaintAction action : actions) {
            if (action.type() == PaintActionType.MANUAL_CLICK) {
                manual++;
            } else if (action.type() == PaintActionType.DRAG_RUN) {
                drags++;
            } else if (action.type() == PaintActionType.COAL_BUCKET_DARKEN) {
                coalBuckets++;
            }
        }
        int bucketActions = (baseCoat == null ? 0 : 1) + coalBuckets;
        String risk = baseCoat == null ? "blocked" : "low";
        return new SmartPreview(oldTicks, originalWrong * Math.max(1, oldTicks / Math.max(1, originalWrong)),
                smartTicks, oldTicks - smartTicks, manual, drags, bucketActions, 0,
                0, 0, 0, originalWrong, componentCount, dominant, baseCoat,
                baseCoat != null, reason, deepBlack.active(), coalBuckets, deepBlack.deepBlackPixels(), risk);
    }
}

package com.artmapcolorassistant;

import java.util.List;

public record PreparedSmartPlan(
        PaintAction baseCoat,
        List<PaintAction> actions,
        List<Integer> bucketAimAnchors,
        SmartPreview preview,
        String unavailableReason
) {
    public PreparedSmartPlan {
        actions = actions == null ? List.of() : List.copyOf(actions);
        bucketAimAnchors = bucketAimAnchors == null ? List.of() : List.copyOf(bucketAimAnchors);
    }

    public boolean available() {
        return unavailableReason == null && baseCoat != null
                && bucketAimAnchors.size() >= SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS;
    }
}

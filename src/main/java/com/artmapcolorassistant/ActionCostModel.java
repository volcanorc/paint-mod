package com.artmapcolorassistant;

public final class ActionCostModel {
    private ActionCostModel() {
    }

    public static int manual(ConfigManager.Config config) {
        return Math.max(1, config.autoPaintDefaultDelayTicks()) + Math.max(0, config.autoAimSettleTicks()) + 1;
    }

    public static int drag(ConfigManager.Config config, int length) {
        return Math.max(0, config.autoDragStartHoldTicks())
                + Math.max(0, config.autoDragEndHoldTicks())
                + Math.max(1, length) * Math.max(1, config.autoDragPixelTicks())
                + Math.max(1, config.autoPaintDefaultDelayTicks());
    }

    public static int bucket(ConfigManager.Config config) {
        return config.bucketColorSelectDelayTicks()
                + config.bucketHandSwapDelayTicks()
                + config.bucketFillAimSettleTicks()
                + 1
                + config.bucketPostFillDelayTicks()
                + config.bucketHandRestoreDelayTicks();
    }
}

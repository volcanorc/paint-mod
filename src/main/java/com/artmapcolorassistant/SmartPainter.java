package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.GameRendererInvoker;
import com.artmapcolorassistant.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.Optional;

public final class SmartPainter {
    private final MinecraftClient client;
    private final SessionController controller;
    private final CalibrationManager calibrationManager;
    private final SmartPaintPlanner planner = new SmartPaintPlanner();
    private SmartCanvas canvas;
    private PaintAction action;
    private Phase phase = Phase.IDLE;
    private int waitTicks;
    private int aimSettleTicks;
    private int bucketClicksRemaining;
    private int dragOffset;
    private int dragTickWait;
    private int dragStartHoldRemaining;
    private int dragEndHoldRemaining;
    private boolean useKeyHeld;
    private boolean running;
    private boolean paused;
    private String bucketDisabledReason;

    public SmartPainter(MinecraftClient client, SessionController controller, CalibrationManager calibrationManager) {
        this.client = client;
        this.controller = controller;
        this.calibrationManager = calibrationManager;
    }

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return paused;
    }

    public String status(ConfigManager.Config config) {
        int wrong = canvas == null ? -1 : canvas.wrongCount();
        return "SMART PAINT: " + (running ? (paused ? "PAUSED" : "RUNNING") : "STOPPED")
                + " phase=" + phase
                + " trusted=" + (canvas != null && canvas.trusted())
                + " wrong=" + (wrong < 0 ? "n/a" : wrong)
                + " smart=" + config.smartEnabled()
                + " bucket=" + config.bucketEnabled()
                + " threshold=" + config.smartBucketThreshold()
                + " dragThreshold=" + config.smartDragThreshold()
                + (bucketDisabledReason == null ? "" : " bucketDisabled=\"" + bucketDisabledReason + "\"");
    }

    public SmartPreview preview(ConfigManager.Config config) {
        PaintSession session = controller.session();
        if (session == null) {
            return null;
        }
        return planner.preview(session, config);
    }

    public boolean start(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintSession session = controller.session();
        if (session == null) {
            sink.error("No active painting session. Start with #painting <image.png> first.");
            return false;
        }
        if (!config.smartEnabled()) {
            return false;
        }
        if (config.autoRequireCalibration() && !calibrationManager.hasUsableCalibration(config)) {
            sink.error("Smart auto needs calibration first. Use #painting usecalibration <name> or #painting calibrate start <name>.");
            return false;
        }
        String movementWarning = calibrationManager.movementWarning(config);
        if (movementWarning != null) {
            sink.error(movementWarning);
            return false;
        }
        SmartPreview preview = planner.preview(session, config);
        if (!planner.shouldUseSmart(session, config)) {
            sink.info("Smart preview predicts little or risky benefit; using old auto painter. " + preview.summary());
            return false;
        }
        canvas = SmartCanvas.fresh(session, config);
        action = null;
        phase = Phase.PLAN;
        waitTicks = 0;
        aimSettleTicks = 0;
        bucketClicksRemaining = 0;
        dragOffset = 0;
        dragTickWait = 0;
        dragStartHoldRemaining = 0;
        dragEndHoldRemaining = 0;
        bucketDisabledReason = null;
        running = true;
        paused = false;
        sink.info("Smart paint started. " + preview.summary());
        return true;
    }

    public void stop(SessionController.MessageSink sink) {
        releaseUseKey();
        running = false;
        paused = false;
        phase = Phase.IDLE;
        action = null;
        sink.info("Smart paint stopped.");
    }

    public void pause(SessionController.MessageSink sink) {
        if (!running || paused) {
            return;
        }
        releaseUseKey();
        paused = true;
        invalidateTrust("manual pause");
        sink.info("Smart paint paused. Bucket trust disabled for this session.");
    }

    public void resume(SessionController.MessageSink sink) {
        if (!running || !paused) {
            return;
        }
        paused = false;
        sink.info("Smart paint resumed with manual/drag fallback only if trust was lost.");
    }

    public void invalidateTrust(String reason) {
        if (canvas != null) {
            canvas.invalidateTrust();
        }
        bucketDisabledReason = reason;
    }

    public void tick(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!running || paused) {
            return;
        }
        if (client.player == null || client.world == null) {
            emergencyStop(sink, "Smart paint stopped because no client player/world is available.");
            return;
        }
        if (unsafeScreenOpen()) {
            invalidateTrust("menu/container open");
            pause(sink);
            sink.error("Smart paint paused because an inventory/container screen is open.");
            return;
        }
        if (controller.session() == null || canvas == null) {
            stop(sink);
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        switch (phase) {
            case PLAN -> plan(config, sink);
            case SELECT_ITEM -> selectItem(config, sink);
            case WAIT_FOR_ITEM -> waitForItem();
            case AIM -> aim(config, sink);
            case WAIT_FOR_AIM -> waitForAim(config, sink);
            case CLICK -> click(config, sink);
            case BUCKET_CLICK -> bucketClick(config, sink);
            case DRAG_HOLD -> dragHold(config, sink);
            case DRAG_AIM -> dragAim(config, sink);
            case DRAG_RELEASE -> dragRelease(config);
            case APPLY -> applyAction(config, sink);
            case IDLE -> {
            }
        }
    }

    private void plan(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (canvas.wrongCount() == 0) {
            running = false;
            phase = Phase.IDLE;
            controller.finishSmart(sink);
            return;
        }
        Optional<PaintAction> next = planner.nextAction(canvas, config);
        if (next.isEmpty()) {
            emergencyStop(sink, "Smart planner could not find a safe fallback action.");
            return;
        }
        action = next.get();
        if (!validateAction(config, sink)) {
            phase = Phase.PLAN;
            return;
        }
        phase = Phase.SELECT_ITEM;
    }

    private boolean validateAction(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null || seed.skip()) {
            return false;
        }
        if (action.bucket()) {
            if (!canvas.trusted()) {
                return false;
            }
            if (!config.bucketEnabled()) {
                invalidateTrust("bucket disabled");
                return false;
            }
            if (!controller.exactEmptyBucketInOffhand()) {
                invalidateTrust("missing exact empty bucket in offhand");
                sink.error("Smart bucket disabled: offhand must contain exact minecraft:bucket.");
                return false;
            }
            if (!calibrationManager.hasExactFor(seed, config)) {
                invalidateTrust("missing exact seed calibration");
                sink.error("Smart bucket disabled: missing exact calibration for seed pixel " + seed.index() + ".");
                return false;
            }
        }
        if (action.type() == PaintActionType.DRAG_RUN) {
            for (int index : action.indexes()) {
                PaintStep step = canvas.targetStep(index);
                if (!calibrationManager.hasExactFor(step, config)) {
                    invalidateTrust("drag exact calibration missing");
                    sink.error("Smart drag disabled for this run: missing exact calibration at pixel " + step.index() + ".");
                    action = new PaintAction(PaintActionType.MANUAL_CLICK, action.color(), action.item(), java.util.List.of(action.seedIndex()),
                            action.seedIndex(), action.seedIndex(), action.seedIndex(), ActionCostModel.manual(config), "drag fallback");
                    return true;
                }
            }
        }
        return true;
    }

    private void selectItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null) {
            phase = Phase.PLAN;
            return;
        }
        if (!controller.selectStepNow(seed, sink)) {
            invalidateTrust("failed item swap");
            pause(sink);
            return;
        }
        phase = controller.hasPendingInventorySwap() ? Phase.WAIT_FOR_ITEM : Phase.AIM;
        waitTicks = action.bucket() ? Math.max(0, config.bucketSwapDelayTicks()) : 0;
    }

    private void waitForItem() {
        if (!controller.hasPendingInventorySwap()) {
            phase = Phase.AIM;
        }
    }

    private void aim(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null) {
            phase = Phase.PLAN;
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.aimAt(seed, config)) {
            invalidateTrust("aim failure");
            pause(sink);
            sink.error("Smart paint paused because it could not aim at the target pixel.");
            return;
        }
        aimSettleTicks = action.bucket() ? Math.max(0, config.bucketAimSettleTicks()) : Math.max(0, config.autoAimSettleTicks());
        phase = aimSettleTicks == 0 ? nextClickPhase() : Phase.WAIT_FOR_AIM;
    }

    private void waitForAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null) {
            phase = Phase.PLAN;
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.aimAt(seed, config)) {
            invalidateTrust("aim failure");
            pause(sink);
            sink.error("Smart paint paused because it could not keep aiming at the target pixel.");
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.withinTolerance(seed, config)) {
            aimSettleTicks = action.bucket() ? Math.max(0, config.bucketAimSettleTicks()) : Math.max(0, config.autoAimSettleTicks());
            return;
        }
        if (aimSettleTicks > 0) {
            aimSettleTicks--;
            return;
        }
        phase = nextClickPhase();
    }

    private void click(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null) {
            phase = Phase.PLAN;
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.withinTolerance(seed, config)) {
            phase = Phase.AIM;
            return;
        }
        performClick(config.autoPaintClickButton());
        waitTicks = Math.max(1, config.autoPaintDefaultDelayTicks());
        phase = Phase.APPLY;
    }

    private void bucketClick(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null) {
            phase = Phase.PLAN;
            return;
        }
        if (!controller.exactEmptyBucketInOffhand()) {
            invalidateTrust("missing exact empty bucket in offhand");
            pause(sink);
            sink.error("Smart paint paused because the exact empty bucket left offhand.");
            return;
        }
        if (bucketClicksRemaining <= 0) {
            bucketClicksRemaining = Math.max(1, config.bucketClickRepeats());
        }
        if (config.autoRequireCalibration() && !calibrationManager.withinTolerance(seed, config)) {
            phase = Phase.AIM;
            return;
        }
        performClick(AutoClickButton.RIGHT);
        bucketClicksRemaining--;
        if (bucketClicksRemaining > 0) {
            waitTicks = Math.max(0, config.bucketClickGapTicks());
            return;
        }
        waitTicks = Math.max(0, config.bucketAfterDelayTicks());
        phase = Phase.APPLY;
    }

    private void dragHold(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep first = seedStep();
        if (first == null || !calibrationManager.aimAt(first, config)) {
            invalidateTrust("drag aim failure");
            pause(sink);
            sink.error("Smart paint paused because it could not aim at the first drag pixel.");
            return;
        }
        if (!useKeyHeld) {
            client.options.useKey.setPressed(true);
            useKeyHeld = true;
        }
        dragOffset = 0;
        dragTickWait = Math.max(1, config.autoDragPixelTicks());
        dragStartHoldRemaining = Math.max(0, config.autoDragStartHoldTicks());
        dragEndHoldRemaining = 0;
        phase = Phase.DRAG_AIM;
    }

    private void dragAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (dragStartHoldRemaining > 0) {
            PaintStep first = seedStep();
            if (first == null || !calibrationManager.aimAt(first, config)) {
                releaseUseKey();
                invalidateTrust("drag hold failure");
                pause(sink);
                sink.error("Smart paint paused because it could not hold-aim at the first drag pixel.");
                return;
            }
            dragStartHoldRemaining--;
            return;
        }
        if (dragOffset >= action.indexes().size()) {
            dragEndHoldRemaining = Math.max(0, config.autoDragEndHoldTicks());
            phase = Phase.DRAG_RELEASE;
            return;
        }
        PaintStep target = canvas.targetStep(action.indexes().get(dragOffset));
        if (!calibrationManager.aimAt(target, config)) {
            releaseUseKey();
            invalidateTrust("drag aim failure");
            pause(sink);
            sink.error("Smart paint paused because it could not drag-aim at the calibrated pixel.");
            return;
        }
        if (dragTickWait > 0) {
            dragTickWait--;
            return;
        }
        dragOffset++;
        dragTickWait = Math.max(1, config.autoDragPixelTicks());
    }

    private void dragRelease(ConfigManager.Config config) {
        if (dragEndHoldRemaining > 0) {
            PaintStep last = canvas.targetStep(action.indexes().getLast());
            calibrationManager.aimAt(last, config);
            dragEndHoldRemaining--;
            return;
        }
        releaseUseKey();
        waitTicks = Math.max(1, config.autoPaintDefaultDelayTicks());
        phase = Phase.APPLY;
    }

    private void applyAction(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (action != null) {
            canvas.apply(action);
        }
        action = null;
        phase = Phase.PLAN;
    }

    private Phase nextClickPhase() {
        if (action.type() == PaintActionType.DRAG_RUN) {
            return Phase.DRAG_HOLD;
        }
        if (action.bucket()) {
            bucketClicksRemaining = 0;
            return Phase.BUCKET_CLICK;
        }
        return Phase.CLICK;
    }

    private PaintStep seedStep() {
        if (action == null || canvas == null || action.seedIndex() < 0 || action.seedIndex() >= canvas.size()) {
            return null;
        }
        return canvas.targetStep(action.seedIndex());
    }

    private void performClick(AutoClickButton button) {
        MinecraftClientInvoker invoker = (MinecraftClientInvoker) client;
        ((GameRendererInvoker) client.gameRenderer).artmapColorAssistant$updateCrosshairTarget(client.getRenderTickCounter().getTickDelta(false));
        if (button == AutoClickButton.LEFT) {
            invoker.artmapColorAssistant$doAttack();
        } else {
            invoker.artmapColorAssistant$doItemUse();
        }
    }

    private boolean unsafeScreenOpen() {
        Screen screen = client.currentScreen;
        return screen instanceof HandledScreen<?>;
    }

    private void emergencyStop(SessionController.MessageSink sink, String message) {
        releaseUseKey();
        running = false;
        paused = false;
        phase = Phase.IDLE;
        action = null;
        sink.error(message);
    }

    private void releaseUseKey() {
        if (useKeyHeld) {
            client.options.useKey.setPressed(false);
            useKeyHeld = false;
        }
    }

    private enum Phase {
        IDLE,
        PLAN,
        SELECT_ITEM,
        WAIT_FOR_ITEM,
        AIM,
        WAIT_FOR_AIM,
        CLICK,
        BUCKET_CLICK,
        DRAG_HOLD,
        DRAG_AIM,
        DRAG_RELEASE,
        APPLY
    }
}

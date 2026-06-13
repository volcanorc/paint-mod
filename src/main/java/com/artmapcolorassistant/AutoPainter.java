package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.MinecraftClientInvoker;
import com.artmapcolorassistant.mixin.GameRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public final class AutoPainter {
    private final MinecraftClient client;
    private final SessionController controller;
    private final CalibrationManager calibrationManager;
    private int delayTicks;
    private int ticksUntilNextAction;
    private int remainingDelayOnPause;
    private int aimSettleTicksRemaining;
    private int pendingAdvanceCount = 1;
    private int dragRunLength;
    private int dragRunOffset;
    private int dragTickWait;
    private int dragStartHoldRemaining;
    private int dragEndHoldRemaining;
    private boolean dragEnabled;
    private boolean useKeyHeld;
    private boolean running;
    private boolean paused;
    private PaintStep lockTarget;
    private AutoPaintPhase phase = AutoPaintPhase.IDLE;

    public AutoPainter(MinecraftClient client, SessionController controller, CalibrationManager calibrationManager, ConfigManager.Config config) {
        this.client = client;
        this.controller = controller;
        this.calibrationManager = calibrationManager;
        this.delayTicks = AutoPaintSpeed.normalizeDefault(config.autoPaintDefaultDelayTicks(), config.autoPaintMinDelayTicks());
        this.dragEnabled = config.autoDragSameColorRuns();
    }

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return paused;
    }

    public AutoPaintPhase phase() {
        return phase;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public int ticksUntilNextAction() {
        return ticksUntilNextAction;
    }

    public boolean dragEnabled() {
        return dragEnabled;
    }

    public boolean cameraLockActive(ConfigManager.Config config) {
        return running && !paused && config.autoLockCameraDuringAuto() && lockTarget != null;
    }

    public void refreshConfig(ConfigManager.Config config) {
        delayTicks = Math.max(delayTicks, config.autoPaintMinDelayTicks());
        dragEnabled = dragEnabled && config.autoDragSameColorRuns();
    }

    public void setDragEnabled(boolean enabled, SessionController.MessageSink sink) {
        dragEnabled = enabled;
        sink.info("Auto drag " + (dragEnabled ? "enabled." : "disabled."));
    }

    public void start(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (controller.session() == null) {
            sink.error("No active painting session. Start with #painting <image.png> first.");
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.hasUsableCalibration(config)) {
            sink.error("Auto paint needs calibration first. Use #painting usecalibration <name> or #painting calibrate start <name>.");
            return;
        }
        String movementWarning = calibrationManager.movementWarning();
        if (movementWarning != null) {
            sink.error(movementWarning);
            return;
        }
        running = true;
        paused = false;
        ticksUntilNextAction = 0;
        remainingDelayOnPause = 0;
        aimSettleTicksRemaining = 0;
        pendingAdvanceCount = 1;
        dragRunLength = 0;
        dragRunOffset = 0;
        dragTickWait = 0;
        dragStartHoldRemaining = 0;
        dragEndHoldRemaining = 0;
        lockTarget = null;
        phase = AutoPaintPhase.SELECT_ITEM;
        sink.info("Auto paint started at " + delayTicks + " ticks per pixel (" + AutoPaintSpeed.formatSeconds(delayTicks) + ").");
    }

    public void stop(SessionController.MessageSink sink) {
        releaseUseKey();
        running = false;
        paused = false;
        ticksUntilNextAction = 0;
        remainingDelayOnPause = 0;
        aimSettleTicksRemaining = 0;
        lockTarget = null;
        phase = AutoPaintPhase.IDLE;
        sink.info("Auto paint stopped.");
    }

    public void emergencyStop(SessionController.MessageSink sink) {
        releaseUseKey();
        running = false;
        paused = false;
        ticksUntilNextAction = 0;
        remainingDelayOnPause = 0;
        aimSettleTicksRemaining = 0;
        lockTarget = null;
        phase = AutoPaintPhase.IDLE;
        sink.error("Auto paint emergency stopped.");
    }

    public void pause(SessionController.MessageSink sink) {
        if (!running || paused) {
            return;
        }
        releaseUseKey();
        paused = true;
        lockTarget = null;
        remainingDelayOnPause = ticksUntilNextAction;
        sink.info("Auto paint paused.");
    }

    public void resume(SessionController.MessageSink sink) {
        if (!running || !paused) {
            return;
        }
        paused = false;
        ticksUntilNextAction = remainingDelayOnPause;
        sink.info("Auto paint resumed.");
    }

    public void setSpeed(int ticks, ConfigManager.Config config, SessionController.MessageSink sink) {
        AutoPaintSpeed.Validation validation = AutoPaintSpeed.validate(ticks, config.autoPaintMinDelayTicks());
        if (!validation.accepted()) {
            sink.error(validation.message());
            return;
        }
        delayTicks = ticks;
        sink.info(validation.message());
    }

    public String status(ConfigManager.Config config) {
        return "AUTO PAINT: " + (running ? (paused ? "PAUSED" : "RUNNING") : "STOPPED")
                + " phase=" + phase
                + " delay=" + delayTicks + " ticks (" + AutoPaintSpeed.formatSeconds(delayTicks) + ")"
                + " wait=" + ticksUntilNextAction
                + " drag=" + (dragEnabled ? "ON" : "OFF")
                + " cameraLock=" + (config.autoLockCameraDuringAuto() ? (cameraLockActive(config) ? "ACTIVE" : "enabled") : "OFF")
                + (dragRunLength > 0 ? " dragOffset=" + dragRunOffset + "/" + dragRunLength : "");
    }

    public String status() {
        return "AUTO PAINT: " + (running ? (paused ? "PAUSED" : "RUNNING") : "STOPPED")
                + " phase=" + phase
                + " delay=" + delayTicks + " ticks (" + AutoPaintSpeed.formatSeconds(delayTicks) + ")"
                + " wait=" + ticksUntilNextAction
                + " drag=" + (dragEnabled ? "ON" : "OFF")
                + " cameraLock=" + (lockTarget != null ? "ACTIVE" : "idle")
                + (dragRunLength > 0 ? " dragOffset=" + dragRunOffset + "/" + dragRunLength : "");
    }

    public void tick(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!running || paused) {
            return;
        }
        if (client.player == null || client.world == null) {
            releaseUseKey();
            emergencyStop(sink);
            return;
        }
        if (unsafeScreenOpen()) {
            releaseUseKey();
            pause(sink);
            sink.error("Auto paint paused because an inventory/container screen is open. Close it before auto painting or put the required item in the hotbar.");
            return;
        }
        PaintSession session = controller.session();
        if (session == null || session.stopped()) {
            stop(sink);
            return;
        }
        maintainCameraLock(session, config);
        if (ticksUntilNextAction > 0) {
            ticksUntilNextAction--;
            return;
        }
        switch (phase) {
            case SELECT_ITEM -> selectItem(session, config, sink);
            case WAIT_FOR_ITEM -> waitForItem(session, sink);
            case AIM -> aim(session, config, sink);
            case WAIT_FOR_AIM -> waitForAim(session, config, sink);
            case CLICK -> click(session, config, sink);
            case DRAG_HOLD -> dragHold(config, sink);
            case DRAG_AIM -> dragAim(session, config, sink);
            case DRAG_RELEASE -> dragRelease(config);
            case WAIT_AFTER_CLICK -> phase = AutoPaintPhase.ADVANCE;
            case ADVANCE -> advance(sink);
            case DONE -> stop(sink);
            case IDLE -> {
            }
        }
    }

    private void selectItem(PaintSession session, ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep step = session.currentStep();
        if (step == null) {
            phase = AutoPaintPhase.DONE;
            return;
        }
        lockTarget = null;
        if (calibrationManager.usingExactCalibration() && !calibrationManager.hasExactFor(step, config)) {
            stopAtCalibrationLimit(step, sink);
            return;
        }
        if (step.skip()) {
            pendingAdvanceCount = 1;
            ticksUntilNextAction = delayTicks;
            phase = AutoPaintPhase.WAIT_AFTER_CLICK;
            return;
        }
        dragRunLength = detectDragRun(session, config);
        dragRunOffset = 0;
        dragTickWait = 0;
        controller.switchCurrentNow(sink);
        if (session.paused()) {
            pause(sink);
            return;
        }
        phase = controller.hasPendingInventorySwap() ? AutoPaintPhase.WAIT_FOR_ITEM : AutoPaintPhase.AIM;
    }

    private void waitForItem(PaintSession session, SessionController.MessageSink sink) {
        if (controller.hasPendingInventorySwap()) {
            return;
        }
        if (session.paused()) {
            pause(sink);
            return;
        }
        phase = AutoPaintPhase.AIM;
    }

    private void aim(PaintSession session, ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep step = session.currentStep();
        if (step == null) {
            phase = AutoPaintPhase.DONE;
            return;
        }
        if (calibrationManager.usingExactCalibration() && !calibrationManager.hasExactFor(step, config)) {
            stopAtCalibrationLimit(step, sink);
            return;
        }
        if (step.skip()) {
            phase = AutoPaintPhase.CLICK;
            return;
        }
        if (config.autoRequireCalibration() && !calibrationManager.hasUsableCalibration(config)) {
            pause(sink);
            sink.error("Auto paint paused because calibration is incomplete.");
            return;
        }
        if (!config.autoRequireCalibration()) {
            phase = AutoPaintPhase.CLICK;
            return;
        }
        if (!calibrationManager.aimAt(step, config)) {
            pause(sink);
            sink.error("Auto paint paused because it could not aim at the current calibrated pixel.");
            return;
        }
        lockTarget = step;
        aimSettleTicksRemaining = Math.max(0, config.autoAimSettleTicks());
        phase = aimSettleTicksRemaining == 0 ? nextPaintPhase() : AutoPaintPhase.WAIT_FOR_AIM;
    }

    private void waitForAim(PaintSession session, ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep step = session.currentStep();
        if (step == null) {
            phase = AutoPaintPhase.DONE;
            return;
        }
        if (!calibrationManager.aimAt(step, config)) {
            pause(sink);
            sink.error("Auto paint paused because it could not keep aiming at the current calibrated pixel.");
            return;
        }
        lockTarget = step;
        if (!calibrationManager.withinTolerance(step, config)) {
            aimSettleTicksRemaining = Math.max(0, config.autoAimSettleTicks());
            return;
        }
        if (aimSettleTicksRemaining > 0) {
            aimSettleTicksRemaining--;
            return;
        }
        phase = nextPaintPhase();
    }

    private void click(PaintSession session, ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep step = session.currentStep();
        if (step == null) {
            phase = AutoPaintPhase.DONE;
            return;
        }
        if (!step.skip() && config.autoRequireCalibration() && !calibrationManager.withinTolerance(step, config)) {
            phase = AutoPaintPhase.AIM;
            return;
        }
        if (step.matchedColor() != null && !controller.currentItemStillAvailable()) {
            pause(sink);
            sink.error("Auto paint paused because the current item is missing.");
            return;
        }
        lockTarget = step;
        performClick(config.autoPaintClickButton());
        pendingAdvanceCount = 1;
        ticksUntilNextAction = delayTicks;
        phase = AutoPaintPhase.WAIT_AFTER_CLICK;
    }

    private void dragHold(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintSession session = controller.session();
        if (session == null) {
            phase = AutoPaintPhase.DRAG_RELEASE;
            return;
        }
        PaintStep first = session.currentStep();
        if (first == null) {
            phase = AutoPaintPhase.DRAG_RELEASE;
            return;
        }
        if (!calibrationManager.aimAt(first, config)) {
            pause(sink);
            sink.error("Auto paint paused because it could not aim at the first drag pixel.");
            return;
        }
        lockTarget = first;
        if (!useKeyHeld) {
            client.options.useKey.setPressed(true);
            useKeyHeld = true;
        }
        dragRunOffset = 0;
        dragTickWait = Math.max(1, config.autoDragPixelTicks());
        dragStartHoldRemaining = Math.max(0, config.autoDragStartHoldTicks());
        dragEndHoldRemaining = 0;
        phase = AutoPaintPhase.DRAG_AIM;
    }

    private void dragAim(PaintSession session, ConfigManager.Config config, SessionController.MessageSink sink) {
        if (dragStartHoldRemaining > 0) {
            PaintStep first = session.currentStep();
            if (first == null || !calibrationManager.aimAt(first, config)) {
                releaseUseKey();
                pause(sink);
                sink.error("Auto paint paused because it could not hold-aim at the first drag pixel.");
                return;
            }
            lockTarget = first;
            dragStartHoldRemaining--;
            return;
        }
        if (dragRunOffset >= dragRunLength) {
            dragEndHoldRemaining = Math.max(0, config.autoDragEndHoldTicks());
            phase = AutoPaintPhase.DRAG_RELEASE;
            return;
        }
        int targetIndex = session.currentIndex() + dragRunOffset;
        if (targetIndex < 0 || targetIndex >= session.steps().size()) {
            phase = AutoPaintPhase.DRAG_RELEASE;
            return;
        }
        PaintStep target = session.steps().get(targetIndex);
        if (!calibrationManager.hasExactFor(target, config)) {
            releaseUseKey();
            stopAtCalibrationLimit(target, sink);
            return;
        }
        if (!calibrationManager.aimAt(target, config)) {
            releaseUseKey();
            pause(sink);
            sink.error("Auto paint paused because it could not drag-aim at the calibrated pixel.");
            return;
        }
        lockTarget = target;
        if (dragTickWait > 0) {
            dragTickWait--;
            return;
        }
        dragRunOffset++;
        dragTickWait = Math.max(1, config.autoDragPixelTicks());
    }

    private void dragRelease(ConfigManager.Config config) {
        if (dragEndHoldRemaining > 0) {
            PaintSession session = controller.session();
            if (session != null && dragRunLength > 0) {
                int lastIndex = Math.min(session.currentIndex() + dragRunLength - 1, session.steps().size() - 1);
                PaintStep last = session.steps().get(lastIndex);
                calibrationManager.aimAt(last, config);
                lockTarget = last;
            }
            dragEndHoldRemaining--;
            return;
        }
        releaseUseKey();
        pendingAdvanceCount = Math.max(1, dragRunLength);
        ticksUntilNextAction = delayTicks;
        dragRunLength = 0;
        dragRunOffset = 0;
        dragTickWait = 0;
        dragStartHoldRemaining = 0;
        dragEndHoldRemaining = 0;
        phase = AutoPaintPhase.WAIT_AFTER_CLICK;
    }

    private void advance(SessionController.MessageSink sink) {
        controller.autoAdvanceAfterDrag(pendingAdvanceCount, sink);
        pendingAdvanceCount = 1;
        if (controller.session() == null || controller.session().stopped()) {
            lockTarget = null;
            phase = AutoPaintPhase.DONE;
            return;
        }
        phase = AutoPaintPhase.SELECT_ITEM;
    }

    private void maintainCameraLock(PaintSession session, ConfigManager.Config config) {
        if (!config.autoLockCameraDuringAuto()) {
            return;
        }
        PaintStep target = lockTarget;
        if (target == null && session != null) {
            target = session.currentStep();
        }
        if (target == null || target.skip() || !calibrationManager.hasUsableCalibration(config)) {
            return;
        }
        calibrationManager.aimAt(target, config);
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

    private AutoPaintPhase nextPaintPhase() {
        return dragRunLength >= 2 ? AutoPaintPhase.DRAG_HOLD : AutoPaintPhase.CLICK;
    }

    private int detectDragRun(PaintSession session, ConfigManager.Config config) {
        if (!dragEnabled
                || !config.autoDragSameColorRuns()
                || config.autoPaintClickButton() != AutoClickButton.RIGHT) {
            return 0;
        }
        int length = RowRunDetector.sameItemRowRun(session.steps(), session.currentIndex(), step ->
                !config.autoDragRequireExactCalibration() || calibrationManager.hasExactFor(step, config));
        return length >= config.autoDragMinRunLength() ? length : 0;
    }

    private void releaseUseKey() {
        if (useKeyHeld) {
            client.options.useKey.setPressed(false);
            useKeyHeld = false;
        }
    }

    private boolean unsafeScreenOpen() {
        Screen screen = client.currentScreen;
        return screen instanceof HandledScreen<?>;
    }

    private void stopAtCalibrationLimit(PaintStep step, SessionController.MessageSink sink) {
        releaseUseKey();
        running = false;
        paused = false;
        ticksUntilNextAction = 0;
        remainingDelayOnPause = 0;
        aimSettleTicksRemaining = 0;
        lockTarget = null;
        phase = AutoPaintPhase.IDLE;
        sink.info(calibrationManager.exactLimitMessage(step));
    }
}

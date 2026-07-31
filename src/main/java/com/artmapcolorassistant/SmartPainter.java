package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.GameRendererInvoker;
import com.artmapcolorassistant.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class SmartPainter {
    private static final int MAX_BUCKET_HAND_SWAP_RETRIES = 2;

    private final MinecraftClient client;
    private final SessionController controller;
    private final CalibrationManager calibrationManager;
    private final SmartPaintPlanner planner = new SmartPaintPlanner();
    private final ArrayDeque<PaintAction> actions = new ArrayDeque<>();
    private final BucketExecutionState bucketExecution = new BucketExecutionState();
    private final BucketNaturalTiming bucketTiming = new BucketNaturalTiming();
    private final SmartBucketSwapRetryPolicy bucketRetryPolicy =
            new SmartBucketSwapRetryPolicy(MAX_BUCKET_HAND_SWAP_RETRIES);
    private final SmartWaypointClock dragWaypointClock = new SmartWaypointClock();
    private SmartCanvas canvas;
    private PreparedSmartPlan preparedPlan;
    private PaintAction action;
    private Phase phase = Phase.IDLE;
    private int waitTicks;
    private int aimSettleTicks;
    private int bucketPhaseTicks;
    private int dragOffset;
    private int dragStartHoldRemaining;
    private int dragEndHoldRemaining;
    private boolean useKeyHeld;
    private boolean bucketHandsSwapped;
    private boolean running;
    private boolean paused;
    private String bucketDisabledReason;
    private int completedActionBoundary;
    private int recoveredActionBoundary;
    private List<Integer> resolvedBucketAnchors = List.of();
    private List<Integer> resolvedBucketFillAnchors = List.of();
    private boolean finishCooldownUsed;

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
        ArtMapColor dominant = preparedPlan == null || preparedPlan.baseCoat() == null ? null : preparedPlan.baseCoat().color();
        return "SMART PAINT: " + (running ? (paused ? "PAUSED" : "RUNNING") : "STOPPED")
                + " phase=" + phase
                + " trusted=" + (canvas != null && canvas.trusted())
                + " wrong=" + (wrong < 0 ? "n/a" : wrong)
                + " queued=" + actions.size()
                + " boundary=" + completedActionBoundary
                + " smartWaypointTicks=" + SmartWaypointClock.WAYPOINT_TICKS
                + " bucket=single-initial-left-click"
                + " bucketNatural=required"
                + " delay=" + config.bucketNaturalDelayMinTicks() + "-" + config.bucketNaturalDelayMaxTicks() + "t"
                + " path=natural-30"
                + " coalBlack=" + (preparedPlan != null && preparedPlan.preview().coalBlackPlanned())
                + " coalPasses=" + (preparedPlan == null ? 0 : preparedPlan.preview().coalBlackPasses())
                + " hands={" + controller.bucketHandStatus(dominant) + "}"
                + (bucketDisabledReason == null ? "" : " blocked=\"" + bucketDisabledReason + "\"");
    }

    public SmartPreview preview(ConfigManager.Config config) {
        PaintSession session = controller.session();
        return session == null ? null : planner.prepare(session, config).preview();
    }

    public boolean start(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintSession session = controller.session();
        if (session == null) {
            sink.error("No active painting session. Start with #painting <image.png> first.");
            return false;
        }
        if (!config.smartEnabled()) {
            sink.error("Smart painting is disabled.");
            return false;
        }
        if (config.autoRequireCalibration() && !calibrationManager.hasUsableCalibration(config)) {
            sink.error("Smart auto needs exact calibration first. Use #painting usecalibration <name>.");
            return false;
        }
        String movementWarning = calibrationManager.movementWarning(config);
        if (movementWarning != null) {
            sink.error(movementWarning);
            return false;
        }

        PreparedSmartPlan plan = planner.prepare(session, config);
        if (!plan.available()) {
            sink.error("Smart paint did not start: " + plan.unavailableReason() + ".");
            return false;
        }
        String preflightFailure = preflight(plan, config);
        if (preflightFailure != null) {
            sink.error("Smart paint paused before touching the canvas: " + preflightFailure + ".");
            return false;
        }

        preparedPlan = plan;
        bucketExecution.beginImage(resolvedBucketAnchors, resolvedBucketFillAnchors, config.canvasWidth());
        canvas = SmartCanvas.fresh(session, config);
        actions.clear();
        actions.add(plan.baseCoat());
        actions.addAll(plan.actions());
        action = null;
        phase = Phase.PLAN;
        waitTicks = 0;
        aimSettleTicks = 0;
        bucketPhaseTicks = 0;
        dragOffset = 0;
        dragWaypointClock.reset();
        dragStartHoldRemaining = 0;
        dragEndHoldRemaining = 0;
        bucketRetryPolicy.resetBucketAction();
        bucketHandsSwapped = false;
        bucketDisabledReason = null;
        resolvedBucketAnchors = List.of();
        resolvedBucketFillAnchors = List.of();
        completedActionBoundary = 0;
        finishCooldownUsed = false;
        running = true;
        paused = false;
        applyRecoveredBoundary();
        controller.saveRecovery(true, null, completedActionBoundary, false, sink);
        sink.info("Smart paint started with a prepared linear plan. " + plan.preview().summary());
        return true;
    }

    public void recoverActionBoundary(int boundary) {
        recoveredActionBoundary = Math.max(0, boundary);
    }

    private String preflight(PreparedSmartPlan plan, ConfigManager.Config config) {
        ArtMapColor dominant = plan.baseCoat().color();
        if (!controller.itemExists(dominant)) {
            return "dominant item " + dominant.item() + " is missing";
        }
        for (PaintAction planned : plan.actions()) {
            if (planned.bucket() && !controller.itemExists(planned.color())) {
                return "bucket item " + planned.item() + " is missing";
            }
        }
        if (!controller.exactEmptyBucketAvailableForOffhand()) {
            return "exact minecraft:bucket is missing from inventory/offhand";
        }
        List<Integer> bucketAnchors = resolveBucketAnchors(plan, config);
        if (bucketAnchors.isEmpty()) {
            return "Smart bucket needs 30 exact natural bucket anchors; exact calibration is incomplete";
        }
        List<Integer> bucketFillAnchors = resolveBucketFillAnchors(config);
        if (bucketFillAnchors.isEmpty()) {
            return "Smart bucket needs at least one exact fill-click anchor; exact calibration is incomplete";
        }
        resolvedBucketAnchors = bucketAnchors;
        resolvedBucketFillAnchors = bucketFillAnchors;
        for (int anchor : bucketAnchors) {
            PaintStep step = controller.session().steps().get(anchor);
            if (!calibrationManager.hasExactFor(step, config)) {
                return "missing exact calibration at bucket anchor " + anchor;
            }
        }
        for (PaintAction planned : plan.actions()) {
            if (planned.type() != PaintActionType.DRAG_RUN) {
                continue;
            }
            for (int index : planned.indexes()) {
                if (!calibrationManager.hasExactFor(controller.session().steps().get(index), config)) {
                    return "missing exact calibration at connected drag pixel " + index;
                }
            }
        }
        return null;
    }

    public void stop(SessionController.MessageSink sink) {
        releaseUseKey();
        restoreBucketHandsIfVerified();
        running = false;
        paused = false;
        phase = Phase.IDLE;
        actions.clear();
        action = null;
        sink.info("Smart paint stopped.");
    }

    public void pause(SessionController.MessageSink sink) {
        if (!running || paused) {
            return;
        }
        releaseUseKey();
        restoreBucketHandsIfVerified();
        paused = true;
        invalidateTrust("manual pause");
        controller.saveRecovery(true, "smart paused", completedActionBoundary, action != null && action.bucket(), sink);
        sink.info("Smart paint paused safely.");
    }

    public void resume(SessionController.MessageSink sink) {
        if (!running || !paused) {
            return;
        }
        if (action != null && action.bucket()) {
            boolean handsSwapped = controller.bucketPairSwapped(action.color());
            boolean handsReady = controller.bucketPairReady(action.color());
            switch (bucketExecution.resumeRoute(handsSwapped, handsReady)) {
                case RESTART_BEFORE_CLICK -> {
                    bucketHandsSwapped = false;
                    phase = Phase.SELECT_ITEM;
                }
                case CONTINUE_POST_FILL -> {
                    bucketHandsSwapped = true;
                    phase = Phase.BUCKET_POST_FILL;
                }
                case APPLY_COMPLETED_FILL -> {
                    bucketHandsSwapped = false;
                    phase = Phase.APPLY;
                }
                case BLOCKED -> {
                    sink.error("Smart bucket remains paused because the post-click hand state is not verifiable: "
                            + controller.bucketHandStatus(action.color()) + ".");
                    return;
                }
            }
        }
        paused = false;
        controller.saveRecovery(true, null, completedActionBoundary, false, sink);
        sink.info("Smart paint resumed.");
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
            pauseWithError(sink, "Smart paint paused because an inventory/container screen is open.");
            return;
        }
        if (controller.session() == null || canvas == null) {
            emergencyStop(sink, "Smart paint stopped because its session disappeared.");
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        switch (phase) {
            case PLAN -> plan(sink);
            case SELECT_ITEM -> selectItem(sink);
            case WAIT_FOR_ITEM -> waitForItem(config, sink);
            case AIM -> aim(config, sink);
            case WAIT_FOR_AIM -> waitForAim(config, sink);
            case CLICK -> click(config);
            case BUCKET_STAGE_AIM -> bucketStageAim(config, sink);
            case BUCKET_VERIFY_SWAPPED -> bucketVerifySwapped(config, sink);
            case BUCKET_FILL_AIM -> bucketFillAim(config, sink);
            case BUCKET_POST_FILL -> bucketPostFill(config, sink);
            case BUCKET_VERIFY_RESTORED -> bucketVerifyRestored(config, sink);
            case DRAG_HOLD -> dragHold(config, sink);
            case DRAG_AIM -> dragAim(config, sink);
            case DRAG_RELEASE -> dragRelease(config);
            case APPLY -> applyAction(config, sink);
            case FINISH_COOLDOWN -> completeSmart(sink);
            case IDLE -> { }
        }
    }

    private void plan(SessionController.MessageSink sink) {
        action = actions.pollFirst();
        if (action == null) {
            if (shouldUseNaturalFinishCooldown()) {
                finishCooldownUsed = true;
                waitTicks = SmartFinishCooldown.randomDelayTicks();
                phase = Phase.FINISH_COOLDOWN;
                controller.saveRecovery(true, "smart waiting natural finish cooldown", completedActionBoundary, false,
                        sink);
                sink.info("Smart paint bucket-only finish: waiting a natural cooldown before save/post-paint.");
                return;
            }
            completeSmart(sink);
            return;
        }
        phase = Phase.SELECT_ITEM;
    }

    private void completeSmart(SessionController.MessageSink sink) {
        running = false;
        phase = Phase.IDLE;
        controller.finishSmart(sink);
    }

    private void selectItem(SessionController.MessageSink sink) {
        PaintStep seed = selectionStep();
        if (seed == null || !controller.selectStepNow(seed, sink)) {
            pauseWithError(sink, "Smart paint paused because the required color could not be selected.");
            return;
        }
        phase = Phase.WAIT_FOR_ITEM;
    }

    private void waitForItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (controller.hasPendingInventorySwap()) {
            return;
        }
        if (action.bucket()) {
            InventoryHelper.SwitchResult bucketSetup = controller.prepareExactEmptyBucketInOffhand(sink);
            if (bucketSetup.pending()) {
                return;
            }
            if (!bucketSetup.success()) {
                pauseWithError(sink, bucketSetup.message());
                return;
            }
            bucketExecution.beginBucketAction();
            bucketRetryPolicy.resetBucketAction();
            controller.saveRecovery(true, "smart bucket action in progress", completedActionBoundary, true,
                    sink);
            bucketPhaseTicks = bucketDelay(config, config.bucketColorSelectDelayTicks());
            phase = Phase.BUCKET_STAGE_AIM;
        } else {
            phase = Phase.AIM;
        }
    }

    private void aim(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null || !calibrationManager.aimAt(seed, config)) {
            pauseWithError(sink, "Smart paint paused because it could not aim at the target pixel.");
            return;
        }
        aimSettleTicks = Math.max(0, config.autoAimSettleTicks());
        phase = aimSettleTicks == 0 ? nextPaintPhase() : Phase.WAIT_FOR_AIM;
    }

    private void waitForAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep seed = seedStep();
        if (seed == null || !calibrationManager.aimAt(seed, config)) {
            pauseWithError(sink, "Smart paint paused because it could not keep aiming at the target pixel.");
            return;
        }
        if (!calibrationManager.withinTolerance(seed, config)) {
            aimSettleTicks = Math.max(0, config.autoAimSettleTicks());
            return;
        }
        if (aimSettleTicks-- > 0) {
            return;
        }
        phase = nextPaintPhase();
    }

    private void click(ConfigManager.Config config) {
        performClick(config.autoPaintClickButton());
        waitTicks = Math.max(1, config.autoPaintDefaultDelayTicks());
        phase = Phase.APPLY;
    }

    private void bucketStageAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_AFTER_SELECT_LOOK, config)) {
            pauseWithError(sink, "Smart bucket could not aim at its staging anchor.");
            return;
        }
        if (bucketPhaseTicks-- > 0) {
            return;
        }
        if (!controller.bucketPairReady(action.color())) {
            pauseWithError(sink, "Smart bucket hand preflight changed: " + controller.bucketHandStatus(action.color()) + ".");
            return;
        }
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_BEFORE_SWAP_LOOK, config)) {
            pauseWithError(sink, "Smart bucket could not aim at its pre-swap anchor.");
            return;
        }
        if (!controller.requestSwapHands()) {
            pauseWithError(sink, "Smart bucket could not request the vanilla hand swap.");
            return;
        }
        bucketRetryPolicy.resetSwap();
        bucketPhaseTicks = bucketDelay(config, config.bucketHandSwapDelayTicks());
        phase = Phase.BUCKET_VERIFY_SWAPPED;
    }

    private void bucketVerifySwapped(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_AFTER_SWAP_LOOK, config)) {
            pauseWithError(sink, "Smart bucket lost its swap-verification aim anchor.");
            return;
        }
        if (controller.bucketPairSwapped(action.color())) {
            bucketHandsSwapped = true;
            bucketPhaseTicks = bucketDelay(config, config.bucketFillAimSettleTicks());
            phase = Phase.BUCKET_FILL_AIM;
            return;
        }
        if (bucketPhaseTicks-- <= 0) {
            if (retryBucketSwapIfStillSafe(config, sink)) {
                return;
            }
            pauseWithError(sink, "Smart bucket hand swap was not verified after "
                    + bucketRetryPolicy.swapRetries() + " retry attempt(s): " + controller.bucketHandStatus(action.color()) + ".");
        }
    }

    private void bucketFillAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep anchor = bucketAnchor(BucketExecutionState.STAGE_FILL_CLICK);
        if (anchor == null || !calibrationManager.aimAt(anchor, config)) {
            pauseWithError(sink, "Smart bucket could not aim at its fill anchor.");
            return;
        }
        if (!controller.bucketPairSwapped(action.color())) {
            pauseWithError(sink, "Smart bucket hand state changed before fill: " + controller.bucketHandStatus(action.color()) + ".");
            return;
        }
        if (!calibrationManager.withinTolerance(anchor, config)) {
            bucketPhaseTicks = bucketDelay(config, config.bucketFillAimSettleTicks());
            return;
        }
        if (bucketPhaseTicks-- > 0) {
            return;
        }
        if (bucketExecution.markFillClickIfFirst()) {
            performClick(AutoClickButton.LEFT);
        }
        bucketPhaseTicks = bucketDelay(config, config.bucketPostFillDelayTicks());
        phase = Phase.BUCKET_POST_FILL;
    }

    private void bucketPostFill(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_AFTER_CLICK_LOOK, config)) {
            pauseWithError(sink, "Smart bucket lost its post-fill aim anchor.");
            return;
        }
        if (bucketPhaseTicks-- > 0) {
            return;
        }
        if (!controller.bucketPairSwapped(action.color())) {
            pauseWithError(sink, "Smart bucket cannot safely restore changed hands: " + controller.bucketHandStatus(action.color()) + ".");
            return;
        }
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_BEFORE_RESTORE_LOOK, config)) {
            pauseWithError(sink, "Smart bucket could not aim at its pre-restore anchor.");
            return;
        }
        if (!controller.requestSwapHands()) {
            pauseWithError(sink, "Smart bucket could not request the restoring hand swap.");
            return;
        }
        bucketRetryPolicy.resetRestore();
        bucketPhaseTicks = bucketDelay(config, config.bucketHandRestoreDelayTicks());
        phase = Phase.BUCKET_VERIFY_RESTORED;
    }

    private void bucketVerifyRestored(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtBucketAnchor(BucketExecutionState.STAGE_AFTER_RESTORE_LOOK, config)) {
            pauseWithError(sink, "Smart bucket lost its restoration aim anchor.");
            return;
        }
        if (controller.bucketPairReady(action.color())) {
            bucketHandsSwapped = false;
            phase = Phase.APPLY;
            return;
        }
        if (bucketPhaseTicks-- <= 0) {
            if (retryBucketRestoreIfStillSafe(config, sink)) {
                return;
            }
            pauseWithError(sink, "Smart bucket hand restoration was not verified after "
                    + bucketRetryPolicy.restoreRetries() + " retry attempt(s): " + controller.bucketHandStatus(action.color()) + ".");
        }
    }

    private boolean retryBucketSwapIfStillSafe(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!bucketRetryPolicy.retrySwapIfReady(controller.bucketPairReady(action.color()))) {
            return false;
        }
        if (!controller.requestSwapHands()) {
            return false;
        }
        bucketPhaseTicks = bucketDelay(config, config.bucketHandSwapDelayTicks());
        sink.info("Smart bucket hand swap did not verify yet; retrying safe swap "
                + bucketRetryPolicy.swapRetries() + "/" + bucketRetryPolicy.maxRetries() + ".");
        return true;
    }

    private boolean retryBucketRestoreIfStillSafe(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!bucketRetryPolicy.retryRestoreIfSwapped(controller.bucketPairSwapped(action.color()))) {
            return false;
        }
        if (!controller.requestSwapHands()) {
            return false;
        }
        bucketPhaseTicks = bucketDelay(config, config.bucketHandRestoreDelayTicks());
        sink.info("Smart bucket hand restoration did not verify yet; retrying safe restore "
                + bucketRetryPolicy.restoreRetries() + "/" + bucketRetryPolicy.maxRetries() + ".");
        return true;
    }

    private boolean aimAtBucketAnchor(int offset, ConfigManager.Config config) {
        PaintStep anchor = bucketAnchor(offset);
        return anchor != null && calibrationManager.aimAt(anchor, config);
    }

    private PaintStep bucketAnchor(int offset) {
        if (preparedPlan == null) {
            return null;
        }
        int anchor = bucketExecution.activeAnchor(offset);
        return anchor < 0 ? null : canvas.targetStep(anchor);
    }

    private int bucketDelay(ConfigManager.Config config, int fixedDelayTicks) {
        return bucketTiming.delay(config, fixedDelayTicks);
    }

    private void dragHold(ConfigManager.Config config, SessionController.MessageSink sink) {
        PaintStep first = seedStep();
        if (first == null || !calibrationManager.aimAt(first, config)) {
            pauseWithError(sink, "Smart paint paused because it could not aim at the first drag pixel.");
            return;
        }
        client.options.useKey.setPressed(true);
        useKeyHeld = true;
        dragOffset = 0;
        dragWaypointClock.reset();
        dragStartHoldRemaining = Math.max(0, config.autoDragStartHoldTicks());
        dragEndHoldRemaining = 0;
        phase = Phase.DRAG_AIM;
    }

    private void dragAim(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (dragStartHoldRemaining > 0) {
            if (!calibrationManager.aimAt(seedStep(), config)) {
                releaseUseKey();
                pauseWithError(sink, "Smart drag lost its starting calibration.");
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
            pauseWithError(sink, "Smart drag lost exact calibration at pixel " + target.index() + ".");
            return;
        }
        if (!dragWaypointClock.tick()) {
            return;
        }
        dragOffset++;
        dragWaypointClock.reset();
    }

    private void dragRelease(ConfigManager.Config config) {
        if (dragEndHoldRemaining > 0) {
            calibrationManager.aimAt(canvas.targetStep(action.indexes().getLast()), config);
            dragEndHoldRemaining--;
            return;
        }
        releaseUseKey();
        waitTicks = Math.max(1, config.autoPaintDefaultDelayTicks());
        phase = Phase.APPLY;
    }

    private void applyAction(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (action != null) {
            boolean bucket = action.bucket();
            canvas.apply(action);
            completedActionBoundary++;
            controller.saveRecovery(bucket, null, completedActionBoundary, false,
                    sink);
            if (bucket && config.bucketNaturalMovementEnabled()) {
                waitTicks = bucketDelay(config, 0);
            }
        }
        action = null;
        phase = Phase.PLAN;
    }

    private List<Integer> resolveBucketAnchors(PreparedSmartPlan plan, ConfigManager.Config config) {
        PaintSession session = controller.session();
        if (session == null) {
            return List.of();
        }
        List<Integer> exactNatural = exactAnchors(plan.bucketAimAnchors(), config, Integer.MAX_VALUE);
        if (exactNatural.size() >= SmartBucketAnchorPlanner.NATURAL_TARGET_POINTS) {
            return exactNatural;
        }
        return List.of();
    }

    private List<Integer> resolveBucketFillAnchors(ConfigManager.Config config) {
        PaintSession session = controller.session();
        if (session == null) {
            return List.of();
        }
        return exactAnchors(SmartBucketAnchorPlanner.fillClickCandidates(config.canvasWidth(), config.canvasHeight()),
                config, Integer.MAX_VALUE);
    }

    private List<Integer> exactAnchors(List<Integer> candidates, ConfigManager.Config config, int limit) {
        PaintSession session = controller.session();
        if (session == null) {
            return List.of();
        }
        ArrayList<Integer> exact = new ArrayList<>(Math.min(limit, candidates.size()));
        for (int index : candidates) {
            if (index < 0 || index >= session.steps().size()) {
                continue;
            }
            if (calibrationManager.hasExactFor(session.steps().get(index), config)) {
                exact.add(index);
                if (exact.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(exact);
    }

    private void applyRecoveredBoundary() {
        if (recoveredActionBoundary <= 0 || canvas == null) {
            recoveredActionBoundary = 0;
            return;
        }
        while (completedActionBoundary < recoveredActionBoundary && !actions.isEmpty()) {
            PaintAction completed = actions.pollFirst();
            canvas.apply(completed);
            completedActionBoundary++;
        }
        recoveredActionBoundary = 0;
    }

    private Phase nextPaintPhase() {
        return action.type() == PaintActionType.DRAG_RUN ? Phase.DRAG_HOLD : Phase.CLICK;
    }

    private boolean shouldUseNaturalFinishCooldown() {
        return !finishCooldownUsed && completedActionBoundary > 0 && plannedActionsWereBucketOnly();
    }

    private boolean plannedActionsWereBucketOnly() {
        if (preparedPlan == null || preparedPlan.baseCoat() == null || !preparedPlan.baseCoat().bucket()) {
            return false;
        }
        return preparedPlan.actions().stream().allMatch(PaintAction::bucket);
    }

    private PaintStep seedStep() {
        if (action == null || canvas == null || action.seedIndex() < 0 || action.seedIndex() >= canvas.size()) {
            return null;
        }
        return canvas.targetStep(action.seedIndex());
    }

    private PaintStep selectionStep() {
        PaintStep seed = seedStep();
        if (seed == null || action == null || SmartCanvas.sameColor(seed.matchedColor(), action.color())) {
            return seed;
        }
        return new PaintStep(seed.index(), seed.x(), seed.y(), seed.originalArgb(), false, action.color(), action.item());
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

    private void pauseWithError(SessionController.MessageSink sink, String message) {
        releaseUseKey();
        restoreBucketHandsIfVerified();
        paused = true;
        invalidateTrust(message);
        controller.saveRecovery(true, message, completedActionBoundary, action != null && action.bucket(), sink);
        sink.error(message);
    }

    private void emergencyStop(SessionController.MessageSink sink, String message) {
        releaseUseKey();
        restoreBucketHandsIfVerified();
        running = false;
        paused = false;
        phase = Phase.IDLE;
        action = null;
        sink.error(message);
    }

    private void restoreBucketHandsIfVerified() {
        ArtMapColor color = action != null && action.bucket() ? action.color()
                : preparedPlan == null || preparedPlan.baseCoat() == null ? null : preparedPlan.baseCoat().color();
        if (color != null && bucketHandsSwapped && controller.bucketPairSwapped(color)) {
            controller.requestSwapHands();
            bucketHandsSwapped = false;
        }
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

    private enum Phase {
        IDLE,
        PLAN,
        SELECT_ITEM,
        WAIT_FOR_ITEM,
        AIM,
        WAIT_FOR_AIM,
        CLICK,
        BUCKET_STAGE_AIM,
        BUCKET_VERIFY_SWAPPED,
        BUCKET_FILL_AIM,
        BUCKET_POST_FILL,
        BUCKET_VERIFY_RESTORED,
        DRAG_HOLD,
        DRAG_AIM,
        DRAG_RELEASE,
        APPLY,
        FINISH_COOLDOWN
    }
}

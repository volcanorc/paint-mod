package com.artmapcolorassistant;

public final class BatchManager {
    private final ConfigManager configManager;
    private final SessionController controller;
    private final AutoPainter autoPainter;
    private final SmartPainter smartPainter;
    private final CalibrationManager calibrationManager;
    private final PostPaintWorkflow postPaintWorkflow;
    private boolean active;
    private boolean paintingActive;
    private boolean waitingForSetup;
    private int first;
    private int last;
    private int current;
    private int nextImageDelayTicks;
    private String nameSuffix = "";
    private String activeFilename;

    public BatchManager(ConfigManager configManager, SessionController controller, AutoPainter autoPainter,
                        SmartPainter smartPainter, CalibrationManager calibrationManager,
                        PostPaintWorkflow postPaintWorkflow) {
        this.configManager = configManager;
        this.controller = controller;
        this.autoPainter = autoPainter;
        this.smartPainter = smartPainter;
        this.calibrationManager = calibrationManager;
        this.postPaintWorkflow = postPaintWorkflow;
    }

    public void start(int first, int last, String suffix, SessionController.MessageSink sink) {
        if (!configManager.config().paintingMode().allowsBatch()) {
            sink.error("Batch requires painting type auto or smart. Run #painting set auto or #painting set smart first.");
            return;
        }
        if (first <= 0 || last < first) {
            sink.error("Usage: #painting batch start <first> <last> <nameSuffix>");
            return;
        }
        stopSilently();
        controller.clearRecovery(sink);
        this.active = true;
        this.paintingActive = false;
        this.waitingForSetup = false;
        this.first = first;
        this.last = last;
        this.current = first;
        this.nextImageDelayTicks = 0;
        this.nameSuffix = suffix == null ? "" : suffix.trim();
        controller.setRecoveryBatchSnapshot(snapshot());
        sink.info("Batch started: " + first + ".png to " + last + ".png suffix=\"" + this.nameSuffix + "\".");
        startCurrent(sink);
    }

    public void continueBatch(SessionController.MessageSink sink) {
        if (!configManager.config().paintingMode().allowsBatch()) {
            sink.error("Batch requires painting type auto or smart. Run #painting set auto or #painting set smart first.");
            return;
        }
        if (!active) {
            sink.error("No batch is active. Use #painting batch start <first> <last> <nameSuffix>.");
            return;
        }
        if (paintingActive || autoPainter.running() || smartPainter.running()) {
            sink.error("Batch is already painting " + activeFilename + ".");
            return;
        }
        if (current > last) {
            sink.info("Batch complete. Painted " + first + ".png through " + last + ".png.");
            active = false;
            waitingForSetup = false;
            nextImageDelayTicks = 0;
            controller.setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot.none());
            controller.clearRecovery(sink);
            return;
        }
        waitingForSetup = false;
        controller.setRecoveryBatchSnapshot(snapshot());
        startCurrent(sink);
    }

    public void stop(SessionController.MessageSink sink) {
        stopSilently();
        controller.clearRecovery(sink);
        sink.info("Batch stopped.");
    }

    public void status(SessionController.MessageSink sink) {
        if (!active) {
            sink.info("No batch is active.");
            return;
        }
        sink.info("Batch status: current=" + current
                + " last=" + last
                + " nextFile=" + nextFilename()
                + " suffix=\"" + nameSuffix + "\""
                + " waitingForSetup=" + waitingForSetup
                + " paintingActive=" + paintingActive
                + " " + postPaintWorkflow.statusLine() + ".");
    }

    public String statusLine() {
        if (!active) {
            return "batch=none";
        }
        return "batch=" + current + "/" + last
                + (waitingForSetup ? " waiting" : paintingActive ? " painting" : " ready")
                + " next=" + nextFilename()
                + " " + postPaintWorkflow.statusLine();
    }

    public boolean active() {
        return active;
    }

    public boolean paintingActive() {
        return paintingActive;
    }

    public void tick(SessionController.MessageSink sink) {
        if (!active || !paintingActive) {
            tickPostPaint(sink);
            return;
        }
        if (controller.session() != null || autoPainter.running() || smartPainter.running()) {
            return;
        }
        String completedFilename = activeFilename == null ? (current + ".png") : activeFilename;
        String saveName = saveName(current);
        paintingActive = false;
        if (configManager.config().postPaintAutomationEnabled()) {
            waitingForSetup = false;
            int completedNumber = current;
            current++;
            controller.setRecoveryBatchSnapshot(snapshot());
            controller.saveRecovery(true, "post-paint automation starting", sink);
            postPaintWorkflow.start(completedNumber, nameSuffix, sink);
            return;
        }
        waitingForSetup = true;
        current++;
        if (current > last) {
            active = false;
            waitingForSetup = false;
            controller.setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot.none());
            controller.clearRecovery(sink);
            sink.info("Painting " + completedFilename + " complete. Save as \"" + saveName
                    + "\", store it, and the batch is complete.");
            return;
        }
        controller.setRecoveryBatchSnapshot(snapshot());
        controller.saveRecovery(true, "batch waiting for next setup", sink);
        sink.info("Painting " + completedFilename + " complete. Save as \"" + saveName
                + "\", store it, place the next blank canvas, enter painting mode, then run #painting batch continue.");
    }

    private void startCurrent(SessionController.MessageSink sink) {
        if (current > last) {
            active = false;
            waitingForSetup = false;
            sink.info("Batch complete. Painted " + first + ".png through " + last + ".png.");
            return;
        }
        ConfigManager.Config config = configManager.config();
        if (!config.paintingMode().allowsBatch()) {
            paintingActive = false;
            waitingForSetup = true;
            sink.error("Batch requires painting type auto or smart. Run #painting set auto or #painting set smart first.");
            return;
        }
        String filename = nextFilename();
        activeFilename = filename;
        controller.setRecoveryBatchSnapshot(snapshot());
        boolean started = controller.start(filename, sink);
        if (!started || controller.session() == null) {
            paintingActive = false;
            waitingForSetup = true;
            controller.setRecoveryBatchSnapshot(snapshot());
            sink.error("Batch could not start " + filename + ". Fix the issue, then run #painting batch continue.");
            return;
        }
        int batchSpeed = Math.max(config.batchDefaultSpeedTicks(), config.autoPaintMinDelayTicks());
        autoPainter.setSpeed(batchSpeed, config, sink);
        autoPainter.setDragEnabled(config.batchEnableDrag(), sink);
        if (config.batchAutoStartAfterContinue()) {
            boolean paintStarted = startConfiguredPainter(config, sink);
            if (!paintStarted) {
                paintingActive = false;
                waitingForSetup = true;
                controller.setRecoveryBatchSnapshot(snapshot());
                controller.saveRecovery(true, "batch loaded but painter did not start", sink);
                sink.error("Batch loaded " + filename + " but auto paint did not start. Fix setup, then run #painting batch continue.");
                return;
            }
        }
        paintingActive = true;
        waitingForSetup = false;
        controller.setRecoveryBatchSnapshot(snapshot());
        controller.saveRecovery(true, null, sink);
    }

    private boolean startConfiguredPainter(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!calibrationManager.prepareBundledDirectionalCalibration(config, sink)) {
            return false;
        }
        if (config.paintingMode() == PaintingMode.SMART) {
            if (smartPainter.start(config, sink)) {
                if (autoPainter.running()) {
                    autoPainter.stop(sink);
                }
                return true;
            }
            return false;
        }
        if (smartPainter.running()) {
            smartPainter.stop(sink);
        }
        autoPainter.start(config, sink);
        return autoPainter.running();
    }

    private void tickPostPaint(SessionController.MessageSink sink) {
        if (nextImageDelayTicks > 0) {
            nextImageDelayTicks--;
            if (nextImageDelayTicks <= 0) {
                controller.setRecoveryBatchSnapshot(snapshot());
                startCurrent(sink);
            }
            return;
        }
        PostPaintWorkflow.Result result = postPaintWorkflow.tick(configManager.config(), sink);
        if (result == PostPaintWorkflow.Result.COMPLETE) {
            if (current > last) {
                active = false;
                waitingForSetup = false;
                nextImageDelayTicks = 0;
                controller.setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot.none());
                controller.clearRecovery(sink);
                sink.info("Batch complete. Painted " + first + ".png through " + last + ".png.");
                return;
            }
            waitingForSetup = false;
            controller.setRecoveryBatchSnapshot(snapshot());
            nextImageDelayTicks = BatchNaturalTiming.randomNextImageDelayTicks();
            controller.saveRecovery(true, "batch waiting natural next-image delay", sink);
            sink.info("Post-paint setup complete. Waiting a natural delay before starting " + nextFilename() + ".");
        } else if (result == PostPaintWorkflow.Result.FAILED) {
            waitingForSetup = true;
            nextImageDelayTicks = 0;
            controller.setRecoveryBatchSnapshot(snapshot());
            controller.saveRecovery(true, "post-paint automation paused", sink);
            sink.error("Post-paint automation paused. Fix the issue or finish setup manually, then run #painting batch continue.");
        }
    }

    public void restore(RecoveryProgress progress, SessionController.MessageSink sink) {
        if (progress == null || !progress.hasBatch()) {
            active = false;
            paintingActive = false;
            waitingForSetup = false;
            nextImageDelayTicks = 0;
            controller.setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot.none());
            return;
        }
        active = true;
        paintingActive = true;
        waitingForSetup = false;
        nextImageDelayTicks = 0;
        first = progress.batchFirst();
        last = progress.batchLast();
        current = progress.batchCurrent();
        nameSuffix = progress.batchSuffix();
        activeFilename = progress.filename();
        controller.setRecoveryBatchSnapshot(snapshot());
        sink.info("Recovered batch " + first + ".png to " + last + ".png at " + activeFilename
                + " suffix=\"" + nameSuffix + "\".");
    }

    private String nextFilename() {
        return current + ".png";
    }

    private String saveName(int number) {
        return nameSuffix.isBlank() ? Integer.toString(number) : number + " " + nameSuffix;
    }

    private void stopSilently() {
        postPaintWorkflow.stop();
        active = false;
        paintingActive = false;
        waitingForSetup = false;
        nextImageDelayTicks = 0;
        first = 0;
        last = 0;
        current = 0;
        activeFilename = null;
        nameSuffix = "";
        controller.setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot.none());
    }

    private RecoveryProgress.BatchSnapshot snapshot() {
        if (!active) {
            return RecoveryProgress.BatchSnapshot.none();
        }
        return new RecoveryProgress.BatchSnapshot(true, first, last, current, nameSuffix);
    }
}

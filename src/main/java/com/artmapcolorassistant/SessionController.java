package com.artmapcolorassistant;

import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SessionController {
    private final ConfigManager configManager;
    private final ImageLoader imageLoader;
    private final InventoryHelper inventoryHelper;
    private final ColorMatcher colorMatcher;
    private PaintSession session;
    private int switchDelayTicks = -1;

    public SessionController(ConfigManager configManager, ImageLoader imageLoader, InventoryHelper inventoryHelper, ColorMatcher colorMatcher) {
        this.configManager = configManager;
        this.imageLoader = imageLoader;
        this.inventoryHelper = inventoryHelper;
        this.colorMatcher = colorMatcher;
    }

    public PaintSession session() {
        return session;
    }

    public boolean hasActiveSession() {
        return session != null && !session.stopped();
    }

    public void start(String filename, MessageSink sink) {
        try {
            ConfigManager.Config config = configManager.config();
            ImageLoader.LoadedImage image = imageLoader.load(configManager.importsPath(), filename, config.canvasWidth(), config.canvasHeight());
            InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
            List<ArtMapColor> palette = colorMatcher.buildMatchingPalette(config, inventory);
            List<PaintStep> steps = colorMatcher.convert(image, config, palette);
            session = new PaintSession(filename, steps, palette);
            sink.info("Started " + filename + " with " + steps.size() + " steps and " + palette.size() + " usable colors.");
            switchCurrentNow(sink);
        } catch (ImageLoader.ImageLoadException | ColorMatcher.MatchException e) {
            sink.error(e.getMessage());
        }
    }

    public void dryrun(String filename, MessageSink sink) {
        try {
            ConfigManager.Config config = configManager.config();
            ImageLoader.LoadedImage image = imageLoader.load(configManager.importsPath(), filename, config.canvasWidth(), config.canvasHeight());
            InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
            List<ArtMapColor> palette = colorMatcher.buildMatchingPalette(config, inventory);
            List<PaintStep> steps = colorMatcher.convert(image, config, palette);
            Map<ArtMapColor, Long> counts = colorMatcher.counts(steps);
            long skipped = steps.stream().filter(PaintStep::skip).count();
            sink.info("Dryrun " + filename + ": " + palette.size() + " usable colors, " + skipped + " transparent skips.");
            String top = counts.entrySet().stream()
                    .sorted(Map.Entry.<ArtMapColor, Long>comparingByValue().reversed())
                    .limit(8)
                    .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
            if (!top.isBlank()) {
                sink.info("Top matched colors: " + top);
            }
            var tools = colorMatcher.detectedTools(config, inventory);
            if (!tools.isEmpty()) {
                sink.info("Detected tools: " + tools.stream().map(ArtMapColor::name).collect(Collectors.joining(", ")));
            }
        } catch (ImageLoader.ImageLoadException | ColorMatcher.MatchException e) {
            sink.error(e.getMessage());
        }
    }

    public void stop(MessageSink sink) {
        if (session != null) {
            session.stop();
            session = null;
        }
        sink.info("Painting stopped.");
    }

    public void pause(MessageSink sink) {
        if (session != null) {
            session.pause();
            sink.info("Painting paused.");
        }
    }

    public void resume(MessageSink sink) {
        if (session != null) {
            session.resume();
            sink.info("Painting resumed.");
            switchCurrentNow(sink);
        }
    }

    public void back(MessageSink sink) {
        if (session == null) {
            sink.error("No active painting session.");
            return;
        }
        session.back();
        sink.info("Moved back to " + describeCurrent());
        switchCurrentNow(sink);
    }

    public void skip(MessageSink sink) {
        if (session == null) {
            sink.error("No active painting session.");
            return;
        }
        if (!session.skip()) {
            finish(sink);
            return;
        }
        sink.info("Skipped to " + describeCurrent());
        switchCurrentNow(sink);
    }

    public void gotoIndex(int index, MessageSink sink) {
        if (session == null) {
            sink.error("No active painting session.");
            return;
        }
        if (!session.gotoIndex(index)) {
            sink.error("Index out of range. Use 0-" + (session.steps().size() - 1) + ".");
            return;
        }
        sink.info("Moved to " + describeCurrent());
        switchCurrentNow(sink);
    }

    public void gotoXY(int x, int y, MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        if (x < 0 || y < 0 || x >= config.canvasWidth() || y >= config.canvasHeight()) {
            sink.error("Position out of range. Use x=0-" + (config.canvasWidth() - 1) + " y=0-" + (config.canvasHeight() - 1) + ".");
            return;
        }
        gotoIndex(CanvasMath.toIndex(x, y, config.canvasWidth()), sink);
    }

    public void status(MessageSink sink) {
        if (session == null) {
            sink.info("No active painting session.");
            return;
        }
        PaintStep current = session.currentStep();
        PaintStep next = session.nextStep();
        sink.info("File=" + session.filename() + " progress=" + (session.currentIndex() + 1) + "/" + session.steps().size()
                + " paused=" + session.paused() + " current=" + describeStep(current) + " next=" + describeStep(next));
        if (session.warning() != null) {
            sink.error(session.warning());
        }
    }

    public void onAdvanceInput(MessageSink sink) {
        if (session == null || session.paused() || session.stopped()) {
            return;
        }
        PaintStep clicked = session.currentStep();
        if (clicked == null) {
            finish(sink);
            return;
        }
        if (!session.advance()) {
            finish(sink);
            return;
        }
        switchDelayTicks = 2;
    }

    public void tick(MessageSink sink) {
        if (inventoryHelper.hasPendingSwap()) {
            InventoryHelper.SwitchResult result = inventoryHelper.tickPendingSwap();
            if (!result.pending() && !result.success()) {
                pauseForWarning(result.message(), sink);
            }
        }
        if (switchDelayTicks >= 0) {
            switchDelayTicks--;
            if (switchDelayTicks <= 0) {
                switchDelayTicks = -1;
                switchCurrentNow(sink);
            }
        }
    }

    public void switchCurrentNow(MessageSink sink) {
        if (session == null || session.stopped()) {
            return;
        }
        PaintStep step = session.currentStep();
        if (step == null) {
            finish(sink);
            return;
        }
        if (step.matchedColor() != null && !inventoryHelper.itemExists(step.matchedColor())) {
            pauseForWarning(InventoryHelper.missingMessage(step), sink);
            return;
        }
        InventoryHelper.SwitchResult result = inventoryHelper.selectOrSwap(step, configManager.config(), text -> sink.info(text.getString()));
        if (!result.success() && !result.pending()) {
            pauseForWarning(result.message(), sink);
        } else if (result.success()) {
            session.clearWarning();
        }
    }

    private void pauseForWarning(String warning, MessageSink sink) {
        if (session != null) {
            session.pause();
            session.setWarning(warning);
        }
        sink.error(warning);
    }

    private void finish(MessageSink sink) {
        if (session != null) {
            session.stop();
        }
        sink.info("Painting complete.");
        session = null;
    }

    public String describeCurrent() {
        return describeStep(session == null ? null : session.currentStep());
    }

    public String describeStep(PaintStep step) {
        if (step == null) {
            return "none";
        }
        if (step.skip()) {
            return "index=" + step.index() + " x=" + step.x() + " y=" + step.y() + " SKIP";
        }
        return "index=" + step.index() + " x=" + step.x() + " y=" + step.y()
                + " " + step.matchedColor().name() + " " + step.item();
    }

    public interface MessageSink {
        void info(String message);

        void error(String message);
    }
}

package com.artmapcolorassistant;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SessionController {
    private final ConfigManager configManager;
    private final ImageLoader imageLoader;
    private final InventoryHelper inventoryHelper;
    private final ColorMatcher colorMatcher;
    private final RecoveryStore recoveryStore;
    private PaintSession session;
    private int switchDelayTicks = -1;
    private int preferredPostPaintAimIndex = -1;
    private RecoveryProgress.BatchSnapshot recoveryBatchSnapshot = RecoveryProgress.BatchSnapshot.none();

    public SessionController(ConfigManager configManager, ImageLoader imageLoader, InventoryHelper inventoryHelper, ColorMatcher colorMatcher) {
        this(configManager, imageLoader, inventoryHelper, colorMatcher, null);
    }

    public SessionController(ConfigManager configManager, ImageLoader imageLoader, InventoryHelper inventoryHelper,
                             ColorMatcher colorMatcher, RecoveryStore recoveryStore) {
        this.configManager = configManager;
        this.imageLoader = imageLoader;
        this.inventoryHelper = inventoryHelper;
        this.colorMatcher = colorMatcher;
        this.recoveryStore = recoveryStore;
    }

    public PaintSession session() {
        return session;
    }

    public boolean hasActiveSession() {
        return session != null && !session.stopped();
    }

    public boolean hasPendingInventorySwap() {
        return inventoryHelper.hasPendingSwap();
    }

    public void setRecoveryBatchSnapshot(RecoveryProgress.BatchSnapshot snapshot) {
        recoveryBatchSnapshot = snapshot == null ? RecoveryProgress.BatchSnapshot.none() : snapshot;
    }

    public void setPreferredPostPaintAimIndex(int index) {
        preferredPostPaintAimIndex = index;
    }

    public int consumePreferredPostPaintAimIndex() {
        int index = preferredPostPaintAimIndex;
        preferredPostPaintAimIndex = -1;
        return index;
    }

    public boolean start(String filename, MessageSink sink) {
        return startAt(filename, 0, sink, true);
    }

    public boolean restore(RecoveryProgress progress, MessageSink sink) {
        if (progress == null) {
            sink.error("No painting recovery checkpoint is saved.");
            return false;
        }
        if (progress.smartBucketInFlight()) {
            sink.error("Recovery is blocked because Smart bucket/Coal darkening was in progress. "
                    + "To avoid double bucket clicks, inspect the canvas and start a new batch or run #painting recovery clear.");
            return false;
        }
        configManager.setPaintingMode(progress.paintingMode(), text -> sink.error(text.getString()));
        boolean restored = startAt(progress.filename(), progress.currentIndex(), sink, false);
        if (restored) {
            sink.info("Recovered " + progress.filename() + " at index " + (session.currentIndex() + 1)
                    + "/" + session.steps().size() + ". Start Auto/Smart again when ready.");
            saveRecovery(true, "recovered checkpoint", progress.smartActionBoundary(), false, sink);
        }
        return restored;
    }

    private boolean startAt(String filename, int index, MessageSink sink, boolean saveProgress) {
        try {
            ConfigManager.Config config = configManager.config();
            ImageLoader.LoadedImage image = imageLoader.load(configManager.importsPath(), filename, config.canvasWidth(), config.canvasHeight());
            InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
            List<ArtMapColor> palette = colorMatcher.buildMatchingPalette(config, inventory);
            List<PaintStep> steps = colorMatcher.convert(image, config, palette);
            session = new PaintSession(filename, steps, palette);
            preferredPostPaintAimIndex = -1;
            if (index < 0 || index >= steps.size()) {
                sink.error("Saved recovery index is out of range for " + filename + ".");
                session = null;
                return false;
            }
            if (index > 0) {
                session.gotoIndex(index);
            }
            sink.info("Started " + filename + " with " + steps.size() + " steps and " + palette.size() + " usable colors.");
            switchCurrentNow(sink);
            if (saveProgress) {
                saveRecovery(true, null, sink);
            }
            return true;
        } catch (ImageLoader.ImageLoadException | ColorMatcher.MatchException e) {
            sink.error(e.getMessage());
            return false;
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
            sink.info("Dryrun " + filename + ": " + palette.size() + " usable colors, " + skipped
                    + " transparent skips, matchMode=" + config.colorMatchMode() + ".");
            String top = counts.entrySet().stream()
                    .sorted(Map.Entry.<ArtMapColor, Long>comparingByValue().reversed())
                    .limit(8)
                    .map(entry -> entry.getKey().name() + "/" + entry.getKey().item() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
            if (!top.isBlank()) {
                sink.info("Matched item counts: " + top);
            }
            reportUnusedAvailableReds(palette, counts, sink);
            reportRedCollapseWarning(image, palette, counts, sink);
            var tools = colorMatcher.detectedTools(config, inventory);
            if (!tools.isEmpty()) {
                sink.info("Detected tools: " + tools.stream().map(ArtMapColor::name).collect(Collectors.joining(", ")));
            }
        } catch (ImageLoader.ImageLoadException | ColorMatcher.MatchException e) {
            sink.error(e.getMessage());
        }
    }

    public void paletteStatus(MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
        List<ArtMapColor> palette = colorMatcher.buildMatchingPalette(config, inventory);
        long configuredTools = config.effectiveArtMapColors().stream().filter(ArtMapColor::tool).count();
        long inventoryMatches = config.effectiveArtMapColors().stream()
                .filter(color -> colorAvailable(color, inventory.availableItemIds()))
                .count();
        sink.info("Palette status: configured=" + config.artMapColors().size()
                + " effective=" + config.effectiveArtMapColors().size()
                + " usableNow=" + palette.size()
                + " inInventory=" + inventoryMatches
                + " toolsConfigured=" + configuredTools
                + " includeTools=" + config.includeToolsInColorMatching()
                + " inventoryOnly=" + config.useOnlyInventoryAvailableColors()
                + " matchMode=" + config.colorMatchMode()
                + " serverOverrides=" + config.serverColorOverridesEnabled()
                + " applied=" + configManager.serverColorOverridesApplied()
                + " skipped=" + configManager.serverColorOverridesSkipped() + ".");
    }

    public void paletteReds(MessageSink sink) {
        ConfigManager.Config config = configManager.config();
        InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
        Set<Identifier> available = inventory.availableItemIds();
        List<ArtMapColor> reds = config.effectiveArtMapColors().stream()
                .filter(color -> !color.tool() || config.includeToolsInColorMatching())
                .filter(colorMatcher::isReddish)
                .filter(color -> colorAvailable(color, available))
                .sorted(Comparator.comparing(ArtMapColor::name))
                .toList();
        if (reds.isEmpty()) {
            sink.error("No red/pink/maroon ArtMap color items were found in hotbar/main inventory.");
            return;
        }
        sink.info("Available red-ish ArtMap colors:");
        for (ArtMapColor color : reds) {
            sink.info(color.name() + " " + color.item() + " rgb=" + RgbUtil.toHex(color.rgb()));
        }
    }

    public void paletteWhy(String hex, MessageSink sink) {
        int rgb;
        try {
            rgb = RgbUtil.parseHex(hex);
        } catch (IllegalArgumentException e) {
            sink.error("Usage: #painting palette why <hex>, example #painting palette why #AA2222");
            return;
        }
        ConfigManager.Config config = configManager.config();
        InventoryHelper.InventorySnapshot inventory = inventoryHelper.scan();
        Set<Identifier> available = inventory.availableItemIds();
        List<ColorMatcher.ColorDistance> nearest = colorMatcher.nearestColors(rgb, config.effectiveArtMapColors(), config.colorMatchMode(), 8);
        sink.info("Nearest configured colors for " + RgbUtil.toHex(rgb) + " using " + config.colorMatchMode() + ":");
        for (ColorMatcher.ColorDistance entry : nearest) {
            ArtMapColor color = entry.color();
            sink.info(color.name() + " " + color.item()
                    + " rgb=" + RgbUtil.toHex(color.rgb())
                    + " distance=" + String.format("%.2f", entry.distance())
                    + " inventory=" + (colorAvailable(color, available) ? "yes" : "no"));
        }
    }

    public void stop(MessageSink sink) {
        if (session != null) {
            session.stop();
            session = null;
        }
        clearRecovery(sink);
        sink.info("Painting stopped.");
    }

    public void pause(MessageSink sink) {
        if (session != null) {
            session.pause();
            saveRecovery(true, "paused", sink);
            sink.info("Painting paused.");
        }
    }

    public boolean resume(MessageSink sink) {
        if (session != null) {
            session.resume();
            sink.info("Painting resumed.");
            switchCurrentNow(sink);
            saveRecovery(true, null, sink);
            return true;
        }
        return false;
    }

    public void back(MessageSink sink) {
        if (session == null) {
            sink.error("No active painting session.");
            return;
        }
        session.back();
        sink.info("Moved back to " + describeCurrent());
        switchCurrentNow(sink);
        saveRecovery(true, null, sink);
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
        saveRecovery(true, null, sink);
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
        saveRecovery(true, null, sink);
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
        saveRecovery(false, null, sink);
        switchDelayTicks = 2;
    }

    public void autoAdvanceAfterClick(MessageSink sink) {
        if (session == null || session.paused() || session.stopped()) {
            return;
        }
        if (!session.advance()) {
            finish(sink);
            return;
        }
        saveRecovery(false, null, sink);
    }

    public void autoAdvanceAfterDrag(int count, MessageSink sink) {
        if (session == null || session.paused() || session.stopped()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            if (!session.advance()) {
                finish(sink);
                return;
            }
        }
        saveRecovery(false, null, sink);
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

    public boolean selectStepNow(PaintStep step, MessageSink sink) {
        if (step == null || step.skip()) {
            return true;
        }
        if (step.matchedColor() != null && !inventoryHelper.itemExists(step.matchedColor())) {
            pauseForWarning(InventoryHelper.missingMessage(step), sink);
            return false;
        }
        InventoryHelper.SwitchResult result = inventoryHelper.selectOrSwap(step, configManager.config(), text -> sink.info(text.getString()));
        if (!result.success() && !result.pending()) {
            pauseForWarning(result.message(), sink);
            return false;
        }
        if (session != null && result.success()) {
            session.clearWarning();
        }
        return true;
    }

    public boolean exactEmptyBucketInOffhand() {
        return inventoryHelper.exactEmptyBucketInOffhand();
    }

    public boolean exactEmptyBucketAvailableForOffhand() {
        return inventoryHelper.exactEmptyBucketAvailableForOffhand();
    }

    public InventoryHelper.SwitchResult prepareExactEmptyBucketInOffhand(MessageSink sink) {
        return inventoryHelper.prepareExactEmptyBucketInOffhand(text -> sink.info(text.getString()));
    }

    public boolean itemExists(ArtMapColor color) {
        return inventoryHelper.itemExists(color);
    }

    public boolean bucketPairReady(ArtMapColor color) {
        return inventoryHelper.bucketPairReady(color);
    }

    public boolean bucketPairSwapped(ArtMapColor color) {
        return inventoryHelper.bucketPairSwapped(color);
    }

    public boolean requestSwapHands() {
        return inventoryHelper.requestSwapHands();
    }

    public String bucketHandStatus(ArtMapColor color) {
        return inventoryHelper.bucketHandStatus(color);
    }

    public void finishSmart(MessageSink sink) {
        finish(sink);
    }

    public boolean currentItemStillAvailable() {
        if (session == null || session.currentStep() == null || session.currentStep().matchedColor() == null) {
            return true;
        }
        return inventoryHelper.itemExists(session.currentStep().matchedColor());
    }

    private void pauseForWarning(String warning, MessageSink sink) {
        if (session != null) {
            session.pause();
            session.setWarning(warning);
            saveRecovery(true, warning, sink);
        }
        sink.error(warning);
    }

    private void finish(MessageSink sink) {
        if (session != null) {
            session.stop();
        }
        sink.info("Painting complete.");
        session = null;
        if (!recoveryBatchSnapshot.active()) {
            clearRecovery(sink);
        }
    }

    public void saveRecovery(boolean immediate, String warning, MessageSink sink) {
        saveRecovery(immediate, warning, 0, false, sink);
    }

    public void saveRecovery(boolean immediate, String warning, int smartActionBoundary,
                             boolean smartBucketInFlight, MessageSink sink) {
        if (recoveryStore == null || session == null) {
            return;
        }
        recoveryStore.saveForSession(session, configManager.config().paintingMode(), recoveryBatchSnapshot,
                smartActionBoundary, smartBucketInFlight, warning == null ? session.warning() : warning,
                immediate, text -> sink.error(text));
    }

    public void clearRecovery(MessageSink sink) {
        if (recoveryStore != null) {
            recoveryStore.clear(text -> sink.error(text));
        }
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

    private void reportUnusedAvailableReds(List<ArtMapColor> palette, Map<ArtMapColor, Long> counts, MessageSink sink) {
        String unused = palette.stream()
                .filter(colorMatcher::isReddish)
                .filter(color -> !counts.containsKey(color))
                .sorted(Comparator.comparing(ArtMapColor::name))
                .map(color -> color.name() + "/" + color.item() + " " + RgbUtil.toHex(color.rgb()))
                .collect(Collectors.joining(", "));
        if (!unused.isBlank()) {
            sink.info("Unused available red-ish colors: " + unused);
        }
    }

    private void reportRedCollapseWarning(ImageLoader.LoadedImage image, List<ArtMapColor> palette,
                                          Map<ArtMapColor, Long> counts, MessageSink sink) {
        long redPixels = 0;
        for (int argb : image.argb()) {
            int alpha = (argb >>> 24) & 0xFF;
            if (alpha > configManager.config().alphaThreshold() && RgbUtil.isReddish(argb & 0xFFFFFF)) {
                redPixels++;
            }
        }
        if (redPixels < image.argb().length / 4L) {
            return;
        }
        long matchedRedColors = counts.keySet().stream().filter(colorMatcher::isReddish).count();
        long availableRedColors = palette.stream().filter(colorMatcher::isReddish).count();
        if (availableRedColors >= 4 && matchedRedColors <= 3) {
            sink.error("Red-heavy image matched only " + matchedRedColors + " red-ish colors. Use #painting palette why <hex> or try colorMatchMode PERCEPTUAL.");
        }
    }

    private boolean colorAvailable(ArtMapColor color, Set<Identifier> available) {
        return available.contains(color.item()) || (color.legacyItem() != null && available.contains(color.legacyItem()));
    }

    public interface MessageSink {
        void info(String message);

        void error(String message);

        default void info(Text message) {
            info(message.getString());
        }

        default void error(Text message) {
            error(message.getString());
        }
    }
}

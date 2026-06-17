package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.GameRendererInvoker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class CalibrationManager {
    private static final double MAX_EYE_MOVE_DISTANCE = 0.25D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftClient client;
    private final Path calibrationsPath;
    private final CanvasCalibration calibration = new CanvasCalibration();
    private String recordingName;
    private int recordingWidth;
    private int recordingHeight;
    private int nextRecordingIndex;
    private int savedExactCount;
    private String lastCalibrationName = "1";
    private CalibrationSource activeSource = CalibrationSource.NONE;
    private CalibrationDirection activeDirection;
    private boolean bundledPortableOverride;

    public CalibrationManager(MinecraftClient client, Path calibrationsPath) {
        this.client = client;
        this.calibrationsPath = calibrationsPath;
    }

    public CanvasCalibration calibration() {
        return calibration;
    }

    public boolean complete() {
        return calibration.exactComplete(recordingOrDefaultWidth(), recordingOrDefaultHeight()) || calibration.complete();
    }

    public boolean hasUsableCalibration(ConfigManager.Config config) {
        return calibration.exactCount() > 0 || calibration.complete();
    }

    public boolean recording() {
        return recordingName != null;
    }

    public String recordingName() {
        return recordingName;
    }

    public String lastCalibrationName() {
        return lastCalibrationName;
    }

    public void setLastCalibrationName(String rawName) {
        lastCalibrationName = ConfigManager.sanitizeCalibrationName(rawName);
    }

    public int savedExactCountForLast(ConfigManager.Config config) {
        return savedExactCount(lastCalibrationName, config);
    }

    public boolean savedExactCompleteForLast(ConfigManager.Config config) {
        return savedExactCountForLast(config) >= config.canvasWidth() * config.canvasHeight();
    }

    public List<CalibrationFileInfo> calibrationFiles(ConfigManager.Config config) {
        List<CalibrationFileInfo> files = new ArrayList<>();
        try {
            Files.createDirectories(calibrationsPath);
            try (Stream<Path> stream = Files.list(calibrationsPath)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .forEach(path -> files.add(readCalibrationInfo(path, config)));
            }
        } catch (IOException e) {
            files.add(new CalibrationFileInfo("error", 0, config.canvasWidth() * config.canvasHeight(), "folder error"));
        }
        return files;
    }

    public int recordingTotal() {
        return recordingWidth * recordingHeight;
    }

    public int recordingCurrentCount() {
        return recording() ? calibration.exactCount() : 0;
    }

    public int recordingSavedCount() {
        return recording() ? savedExactCount : 0;
    }

    public int recordingUnsavedCount() {
        return Math.max(0, recordingCurrentCount() - recordingSavedCount());
    }

    public int recordingNextIndex() {
        return nextRecordingIndex;
    }

    public int recordingNextX() {
        return recording() && nextRecordingIndex < recordingTotal() ? CanvasMath.toX(nextRecordingIndex, recordingWidth) : -1;
    }

    public int recordingNextY() {
        return recording() && nextRecordingIndex < recordingTotal() ? CanvasMath.toY(nextRecordingIndex, recordingWidth) : -1;
    }

    public boolean usingExactCalibration() {
        return calibration.exactCount() > 0;
    }

    public Map<Integer, CalibrationSample> exactSamples() {
        return calibration.exactSamples();
    }

    public String activeCalibrationSourceLabel() {
        return activeSource.label();
    }

    public String activeCalibrationDirectionLabel() {
        return activeDirection == null ? "none" : activeDirection.resourceName();
    }

    public boolean bundledPortableOverrideActive() {
        return activeSource == CalibrationSource.BUNDLED && bundledPortableOverride && usingExactCalibration();
    }

    public String activeCalibrationStatusLine() {
        String direction = activeDirection == null ? "none" : activeDirection.resourceName();
        String name = calibration.loadedExactName() == null ? "none" : calibration.loadedExactName();
        return "Calibration source=" + activeSource.label()
                + " direction=" + direction
                + " loaded=" + name
                + " points=" + calibration.exactCount()
                + " portableOverride=" + bundledPortableOverrideActive() + ".";
    }

    public boolean hasExactFor(PaintStep step, ConfigManager.Config config) {
        return calibration.hasExact(CanvasMath.toIndex(step.x(), step.y(), config.canvasWidth()));
    }

    public String exactLimitMessage(PaintStep step) {
        return "Painting done at calibration limit: " + step.index()
                + " calibrated clicks complete. Next missing calibration index=" + step.index()
                + " x=" + step.x() + " y=" + step.y() + ".";
    }

    public void capture(CalibrationPoint point, SessionController.MessageSink sink) {
        if (client.player == null || client.world == null) {
            sink.error("Cannot calibrate before joining a world.");
            return;
        }
        WorldPoint hit = null;
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
            hit = WorldPoint.from(client.crosshairTarget.getPos());
        }
        AimAngles angles = new AimAngles(client.player.getYaw(), client.player.getPitch());
        WorldPoint eye = WorldPoint.from(client.player.getEyePos());
        calibration.set(point, new CalibrationSample(angles, eye, hit));
        sink.info("Set calibration " + point.commandName() + " yaw="
                + String.format("%.2f pitch=%.2f", angles.yaw(), angles.pitch())
                + " hit=" + (hit == null ? "none" : formatPoint(hit)) + ".");
    }

    public void startRecording(String rawName, ConfigManager.Config config, SessionController.MessageSink sink) {
        String name = sanitizeName(rawName == null || rawName.isBlank() ? "1" : rawName);
        lastCalibrationName = name;
        try {
            Files.createDirectories(calibrationsPath);
        } catch (IOException e) {
            sink.error("Failed to create calibration folder: " + e.getMessage());
            return;
        }
        calibration.clearExact();
        clearLoadedSource();
        recordingName = name;
        recordingWidth = config.canvasWidth();
        recordingHeight = config.canvasHeight();
        calibration.setLoadedMetadata(name, recordingWidth, recordingHeight);
        activeSource = CalibrationSource.USER_FILE;
        savedExactCount = savedExactCount(name, config);
        nextRecordingIndex = firstMissingIndex(recordingWidth, recordingHeight);
        if (nextRecordingIndex >= recordingWidth * recordingHeight) {
            sink.info("Exact calibration '" + name + "' is already complete.");
            return;
        }
        sink.info("Started fresh unsaved calibration '" + name + "'. Right-click each pixel left-to-right, top-to-bottom. Next: x="
                + CanvasMath.toX(nextRecordingIndex, recordingWidth) + " y=" + CanvasMath.toY(nextRecordingIndex, recordingWidth) + ".");
    }

    public void continueRecording(String rawName, ConfigManager.Config config, SessionController.MessageSink sink) {
        String name = sanitizeName(rawName == null || rawName.isBlank() ? "1" : rawName);
        lastCalibrationName = name;
        try {
            Files.createDirectories(calibrationsPath);
        } catch (IOException e) {
            sink.error("Failed to create calibration folder: " + e.getMessage());
            return;
        }
        calibration.clearExact();
        clearLoadedSource();
        recordingName = name;
        recordingWidth = config.canvasWidth();
        recordingHeight = config.canvasHeight();
        calibration.setLoadedMetadata(name, recordingWidth, recordingHeight);
        activeSource = CalibrationSource.USER_FILE;
        loadExistingForRecording(name, config, sink);
        savedExactCount = calibration.exactCount();
        nextRecordingIndex = firstMissingIndex(recordingWidth, recordingHeight);
        if (nextRecordingIndex >= recordingWidth * recordingHeight) {
            sink.info("Exact calibration '" + name + "' is already complete.");
            return;
        }
        sink.info("Continuing calibration '" + name + "' from saved progress. Next: x="
                + CanvasMath.toX(nextRecordingIndex, recordingWidth) + " y=" + CanvasMath.toY(nextRecordingIndex, recordingWidth) + ".");
    }

    public void stopRecording(SessionController.MessageSink sink) {
        if (!recording()) {
            sink.error("No exact calibration recording is active.");
            return;
        }
        sink.info("Stopped exact calibration '" + recordingName + "' at " + nextRecordingIndex
                + "/" + (recordingWidth * recordingHeight) + " points. Unsaved changes were not written.");
        clearRecordingState();
    }

    public void recordClick(SessionController.MessageSink sink) {
        if (!recording()) {
            return;
        }
        if (client.player == null || client.world == null) {
            sink.error("Cannot record calibration before joining a world.");
            return;
        }
        int total = recordingWidth * recordingHeight;
        if (nextRecordingIndex >= total) {
            sink.info("Exact calibration '" + recordingName + "' is complete in memory. Use #painting calibrate save " + recordingName + " to save.");
            return;
        }
        int x = CanvasMath.toX(nextRecordingIndex, recordingWidth);
        int y = CanvasMath.toY(nextRecordingIndex, recordingWidth);
        refreshCrosshairTarget();
        CalibrationSample sample = currentSample();
        calibration.setExact(nextRecordingIndex, sample);
        nextRecordingIndex++;
        if (sample.hitPosition() == null) {
            sink.error("Recorded calibration direction, but no hit point was available for marker rendering.");
        }
        if (nextRecordingIndex >= total) {
            sink.info("Recorded x=" + x + " y=" + y + ". Exact calibration '" + recordingName + "' complete in memory. Use #painting calibrate save " + recordingName + " to save.");
            return;
        }
        int nextX = CanvasMath.toX(nextRecordingIndex, recordingWidth);
        int nextY = CanvasMath.toY(nextRecordingIndex, recordingWidth);
        sink.info("Recorded x=" + x + " y=" + y + ". Next: x=" + nextX + " y=" + nextY + ".");
    }

    public void undoLastRecordingPoint(SessionController.MessageSink sink) {
        if (!recording()) {
            sink.error("No exact calibration recording is active.");
            return;
        }
        if (nextRecordingIndex <= 0) {
            sink.error("No calibration point to undo.");
            return;
        }
        int index = nextRecordingIndex - 1;
        CalibrationSample removed = calibration.removeExact(index);
        if (removed == null) {
            sink.error("No calibration point to undo.");
            return;
        }
        nextRecordingIndex = index;
        int x = CanvasMath.toX(nextRecordingIndex, recordingWidth);
        int y = CanvasMath.toY(nextRecordingIndex, recordingWidth);
        sink.info("Removed calibration point index=" + index + " x=" + x + " y=" + y
                + ". Next click will record this pixel again.");
    }

    public void saveRecordingAs(String rawName, SessionController.MessageSink sink) {
        String name = sanitizeName(rawName == null || rawName.isBlank() ? recordingName : rawName);
        if (name == null || name.isBlank()) {
            sink.error("Usage: #painting calibrate save <name>");
            return;
        }
        lastCalibrationName = name;
        if (recordingName == null && calibration.exactCount() == 0) {
            sink.error("No in-memory exact calibration to save.");
            return;
        }
        int width = recordingWidth > 0 ? recordingWidth : recordingOrDefaultWidth();
        int height = recordingHeight > 0 ? recordingHeight : recordingOrDefaultHeight();
        saveExact(name, width, height, sink);
        recordingName = name;
        recordingWidth = width;
        recordingHeight = height;
        calibration.setLoadedMetadata(name, width, height);
        savedExactCount = calibration.exactCount();
        nextRecordingIndex = firstMissingIndex(width, height);
        sink.info("Saved exact calibration '" + name + "' with " + savedExactCount + "/" + (width * height) + " points.");
    }

    public void resetRecording(String rawName, SessionController.MessageSink sink) {
        String name = sanitizeName(rawName == null || rawName.isBlank() ? "1" : rawName);
        lastCalibrationName = name;
        try {
            Files.deleteIfExists(calibrationPath(name));
            if (name.equals(recordingName)) {
                calibration.clearExact();
                clearLoadedSource();
                clearRecordingState();
            }
            sink.info("Reset exact calibration '" + name + "'.");
        } catch (IOException e) {
            sink.error("Failed to reset calibration '" + name + "': " + e.getMessage());
        }
    }

    public void loadExact(String rawName, ConfigManager.Config config, SessionController.MessageSink sink) {
        String name = sanitizeName(rawName == null || rawName.isBlank() ? "1" : rawName);
        lastCalibrationName = name;
        Path path = calibrationPath(name);
        if (Files.notExists(path)) {
            sink.error("Calibration file not found: " + path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            loadExactFromRoot(name, root, config, CalibrationSource.USER_FILE, null, false, sink);
        } catch (RuntimeException | IOException e) {
            sink.error("Failed to load calibration '" + name + "': " + e.getMessage());
        }
    }

    public boolean prepareBundledDirectionalCalibration(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!config.useBundledDirectionalCalibration() || !config.autoDetectCalibrationDirectionOnAutoStart()) {
            return true;
        }
        if (client.player == null || client.world == null) {
            sink.error("Cannot auto-detect calibration direction before joining a world.");
            return false;
        }
        Optional<CalibrationDirection> direction = CalibrationDirection.nearest(
                client.player.getYaw(),
                config.cardinalDirectionToleranceDegrees()
        );
        if (direction.isEmpty()) {
            sink.error("Face the canvas directly before starting auto paint. Current direction is too diagonal.");
            return false;
        }
        return loadBundledDirectional(config.defaultBundledCalibrationPrefix(), direction.get(), config, sink);
    }

    public boolean loadBundledDirectional(String rawPrefix, CalibrationDirection direction, ConfigManager.Config config,
                                          SessionController.MessageSink sink) {
        String prefix = ConfigManager.sanitizeCalibrationName(rawPrefix == null || rawPrefix.isBlank() ? "ee" : rawPrefix);
        String name = prefix + "_" + direction.resourceName();
        String resourcePath = "assets/" + ConfigManager.MOD_ID + "/calibrations/" + name + ".json";
        try (InputStream stream = CalibrationManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                sink.error("Built-in calibration missing: " + resourcePath);
                return false;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                return loadExactFromRoot(name, root, config, CalibrationSource.BUNDLED, direction, true, sink);
            }
        } catch (RuntimeException | IOException e) {
            sink.error("Failed to load built-in calibration '" + name + "': " + e.getMessage());
            return false;
        }
    }

    private boolean loadExactFromRoot(String name, JsonObject root, ConfigManager.Config config,
                                      CalibrationSource source, CalibrationDirection direction,
                                      boolean requireComplete, SessionController.MessageSink sink) {
        if (root == null) {
            throw new IllegalArgumentException("empty JSON");
        }
        int width = root.get("canvasWidth").getAsInt();
        int height = root.get("canvasHeight").getAsInt();
        int expectedTotal = config.canvasWidth() * config.canvasHeight();
        if (width != config.canvasWidth() || height != config.canvasHeight()) {
            sink.error("Calibration '" + name + "' is " + width + "x" + height
                    + " but config canvas is " + config.canvasWidth() + "x" + config.canvasHeight() + ".");
            return false;
        }
        JsonArray samples = root.getAsJsonArray("samples");
        if (samples == null) {
            sink.error("Calibration '" + name + "' has no samples array.");
            return false;
        }
        Map<Integer, CalibrationSample> parsed = new HashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonElement element : samples) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject sample = element.getAsJsonObject();
            int index = sample.get("index").getAsInt();
            if (index < 0 || index >= expectedTotal) {
                sink.error("Calibration '" + name + "' has out-of-range sample index " + index + ".");
                return false;
            }
            if (!seen.add(index)) {
                sink.error("Calibration '" + name + "' has duplicate sample index " + index + ".");
                return false;
            }
            parsed.put(index, parseSample(sample));
        }
        if (requireComplete && parsed.size() != expectedTotal) {
            sink.error("Built-in calibration '" + name + "' has wrong sample count "
                    + parsed.size() + "/" + expectedTotal + ".");
            return false;
        }
        calibration.clearExact();
        calibration.setLoadedMetadata(name, width, height);
        parsed.forEach(calibration::setExact);
        activeSource = source;
        activeDirection = direction;
        bundledPortableOverride = source == CalibrationSource.BUNDLED && config.autoEnablePortableForBundledCalibration();
        String sourceLabel = source == CalibrationSource.BUNDLED ? "built-in" : "exact";
        sink.info("Loaded " + sourceLabel + " calibration '" + name + "': " + calibration.exactCount()
                + "/" + (width * height) + " points"
                + (direction == null ? "." : " direction=" + direction.resourceName() + "."));
        if (!requireComplete && !calibration.exactComplete(width, height)) {
            sink.info("Calibration is partial. Auto paint will stop at the first pixel without a recorded calibration point.");
        }
        return true;
    }

    public void clear(SessionController.MessageSink sink) {
        calibration.clear();
        clearLoadedSource();
        recordingName = null;
        nextRecordingIndex = 0;
        sink.info("Calibration cleared.");
    }

    public void status(SessionController.MessageSink sink) {
        if (recording()) {
            int total = recordingWidth * recordingHeight;
            int x = Math.min(CanvasMath.toX(nextRecordingIndex, recordingWidth), recordingWidth - 1);
            int y = Math.min(CanvasMath.toY(nextRecordingIndex, recordingWidth), recordingHeight - 1);
            sink.info("Recording exact calibration '" + recordingName + "': "
                    + calibration.exactCount() + "/" + total
                    + " saved=" + savedExactCount
                    + " unsaved=" + recordingUnsavedCount()
                    + " next x=" + x + " y=" + y + ".");
        }
        sink.info(calibration.exactStatus(recordingOrDefaultWidth(), recordingOrDefaultHeight()));
        sink.info(activeCalibrationStatusLine());
        sink.info(calibration.status());
        if (!calibration.complete() && calibration.exactCount() == 0) {
            sink.error("Calibration incomplete. Use #painting calibrate start <name> or set top-left, top-right, bottom-left, and bottom-right.");
            return;
        }
        String moveWarning = movementWarning();
        if (moveWarning != null) {
            sink.error(moveWarning);
        }
    }

    public void testAim(int x, int y, ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!validateCoordinates(x, y, config, sink)) {
            return;
        }
        if (!hasUsableCalibration(config)) {
            sink.error("Calibration missing. Use #painting usecalibration <name> or #painting calibrate start <name>.");
            return;
        }
        int index = CanvasMath.toIndex(x, y, config.canvasWidth());
        if (usingExactCalibration() && !calibration.hasExact(index)) {
            sink.error("No exact calibration for index=" + index + " x=" + x + " y=" + y + ".");
            return;
        }
        if (client.player == null) {
            sink.error("Cannot test aim before joining a world.");
            return;
        }
        String moveWarning = movementWarning(config);
        if (moveWarning != null) {
            sink.error(moveWarning);
            return;
        }
        aimAt(x, y, config);
        sink.info("Aimed at calibrated pixel x=" + x + " y=" + y + " without clicking.");
    }

    public boolean aimAt(PaintStep step, ConfigManager.Config config) {
        return aimAt(step.x(), step.y(), config);
    }

    public boolean aimAt(int x, int y, ConfigManager.Config config) {
        if (client.player == null || !hasUsableCalibration(config)) {
            return false;
        }
        int index = CanvasMath.toIndex(x, y, config.canvasWidth());
        if (usingExactCalibration() && !calibration.hasExact(index)) {
            return false;
        }
        if (movementWarning(config) != null) {
            return false;
        }
        AimAngles angles = targetAngles(x, y, config);
        client.player.setYaw(angles.yaw());
        client.player.setPitch(angles.pitch());
        return withinTolerance(x, y, config);
    }

    public boolean withinTolerance(PaintStep step, ConfigManager.Config config) {
        return withinTolerance(step.x(), step.y(), config);
    }

    public boolean withinTolerance(int x, int y, ConfigManager.Config config) {
        if (client.player == null || !hasUsableCalibration(config)) {
            return false;
        }
        int index = CanvasMath.toIndex(x, y, config.canvasWidth());
        if (usingExactCalibration() && !calibration.hasExact(index)) {
            return false;
        }
        AimAngles targetAngles = targetAngles(x, y, config);
        AimAngles currentAngles = new AimAngles(client.player.getYaw(), client.player.getPitch());
        return AimMath.withinTolerance(currentAngles, targetAngles, config.autoAimToleranceDegrees());
    }

    public String movementWarning() {
        if (bundledPortableOverrideActive()) {
            return null;
        }
        if (client.player == null || (!calibration.complete() && calibration.exactCount() == 0)) {
            return null;
        }
        WorldPoint reference = calibration.referenceEyePosition();
        if (reference == null) {
            return null;
        }
        Vec3d currentEye = client.player.getEyePos();
        double dx = currentEye.x - reference.x();
        double dy = currentEye.y - reference.y();
        double dz = currentEye.z - reference.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > MAX_EYE_MOVE_DISTANCE) {
            return "Player moved since calibration. Recalibrate from the current ArtMap seat/view before auto painting.";
        }
        return null;
    }

    public String movementWarning(ConfigManager.Config config) {
        if ((config.portableExactCalibrationMode() || bundledPortableOverrideActive()) && usingExactCalibration()) {
            return null;
        }
        return movementWarning();
    }

    private boolean validateCoordinates(int x, int y, ConfigManager.Config config, SessionController.MessageSink sink) {
        if (x < 0 || y < 0 || x >= config.canvasWidth() || y >= config.canvasHeight()) {
            sink.error("Calibration test position out of range. Use x=0-" + (config.canvasWidth() - 1)
                    + " y=0-" + (config.canvasHeight() - 1) + ".");
            return false;
        }
        return true;
    }

    private AimAngles targetAngles(int x, int y, ConfigManager.Config config) {
        int index = CanvasMath.toIndex(x, y, config.canvasWidth());
        CalibrationSample exact = calibration.exact(index);
        if (exact != null) {
            return exact.angles();
        }
        return AimMath.pixelAngles(calibration, x, y, config.canvasWidth(), config.canvasHeight());
    }

    private void refreshCrosshairTarget() {
        if (client.gameRenderer == null) {
            return;
        }
        ((GameRendererInvoker) client.gameRenderer)
                .artmapColorAssistant$updateCrosshairTarget(client.getRenderTickCounter().getTickDelta(false));
    }

    private CalibrationSample currentSample() {
        WorldPoint hit = null;
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
            hit = WorldPoint.from(client.crosshairTarget.getPos());
        }
        return new CalibrationSample(
                new AimAngles(client.player.getYaw(), client.player.getPitch()),
                WorldPoint.from(client.player.getEyePos()),
                hit
        );
    }

    private void saveExact(String name, int width, int height, SessionController.MessageSink sink) {
        try {
            Files.createDirectories(calibrationsPath);
            try (Writer writer = Files.newBufferedWriter(calibrationPath(name))) {
                GSON.toJson(toJson(name, width, height), writer);
            }
        } catch (IOException e) {
            sink.error("Failed to save calibration '" + name + "': " + e.getMessage());
        }
    }

    private void loadExistingForRecording(String name, ConfigManager.Config config, SessionController.MessageSink sink) {
        Path path = calibrationPath(name);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            int width = root.get("canvasWidth").getAsInt();
            int height = root.get("canvasHeight").getAsInt();
            if (width != config.canvasWidth() || height != config.canvasHeight()) {
                sink.error("Existing calibration '" + name + "' is " + width + "x" + height
                        + " but config canvas is " + config.canvasWidth() + "x" + config.canvasHeight()
                        + ". Starting a new recording for current size.");
                calibration.clearExact();
                calibration.setLoadedMetadata(name, recordingWidth, recordingHeight);
                return;
            }
            JsonArray samples = root.getAsJsonArray("samples");
            for (JsonElement element : samples) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject sample = element.getAsJsonObject();
                int index = sample.get("index").getAsInt();
                calibration.setExact(index, parseSample(sample));
            }
            sink.info("Continuing exact calibration '" + name + "' from "
                    + calibration.exactCount() + "/" + (width * height) + " points.");
        } catch (RuntimeException | IOException e) {
            sink.error("Failed to continue existing calibration '" + name + "': " + e.getMessage()
                    + ". Starting a new recording.");
            calibration.clearExact();
            calibration.setLoadedMetadata(name, recordingWidth, recordingHeight);
        }
    }

    private int firstMissingIndex(int width, int height) {
        int total = width * height;
        for (int index = 0; index < total; index++) {
            if (!calibration.hasExact(index)) {
                return index;
            }
        }
        return total;
    }

    private int savedExactCount(String name, ConfigManager.Config config) {
        Path path = calibrationPath(name);
        if (Files.notExists(path)) {
            return 0;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            int width = root.get("canvasWidth").getAsInt();
            int height = root.get("canvasHeight").getAsInt();
            if (width != config.canvasWidth() || height != config.canvasHeight()) {
                return 0;
            }
            JsonArray samples = root.getAsJsonArray("samples");
            return samples == null ? 0 : samples.size();
        } catch (RuntimeException | IOException e) {
            return 0;
        }
    }

    private CalibrationFileInfo readCalibrationInfo(Path path, ConfigManager.Config config) {
        String filename = path.getFileName().toString();
        String name = sanitizeName(filename.substring(0, filename.length() - ".json".length()));
        int expectedTotal = config.canvasWidth() * config.canvasHeight();
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            int width = root.get("canvasWidth").getAsInt();
            int height = root.get("canvasHeight").getAsInt();
            JsonArray samples = root.getAsJsonArray("samples");
            int count = samples == null ? 0 : samples.size();
            int total = width * height;
            if (width != config.canvasWidth() || height != config.canvasHeight()) {
                return new CalibrationFileInfo(name, count, total, "wrong size " + width + "x" + height);
            }
            return new CalibrationFileInfo(name, count, expectedTotal, count >= expectedTotal ? "complete" : "partial");
        } catch (RuntimeException | IOException e) {
            return new CalibrationFileInfo(name, 0, expectedTotal, "invalid");
        }
    }

    private JsonObject toJson(String name, int width, int height) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("name", name);
        root.addProperty("canvasWidth", width);
        root.addProperty("canvasHeight", height);
        JsonArray samples = new JsonArray();
        calibration.exactSamples().entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(entry -> samples.add(sampleToJson(entry.getKey(), entry.getValue(), width)));
        root.add("samples", samples);
        return root;
    }

    private JsonObject sampleToJson(int index, CalibrationSample sample, int width) {
        JsonObject object = new JsonObject();
        object.addProperty("index", index);
        object.addProperty("x", CanvasMath.toX(index, width));
        object.addProperty("y", CanvasMath.toY(index, width));
        object.addProperty("yaw", sample.angles().yaw());
        object.addProperty("pitch", sample.angles().pitch());
        object.add("eye", pointToJson(sample.eyePosition()));
        if (sample.hitPosition() != null) {
            object.add("hit", pointToJson(sample.hitPosition()));
        }
        return object;
    }

    private CalibrationSample parseSample(JsonObject object) {
        WorldPoint hit = object.has("hit") && object.get("hit").isJsonObject()
                ? pointFromJson(object.getAsJsonObject("hit"))
                : null;
        return new CalibrationSample(
                new AimAngles(object.get("yaw").getAsFloat(), object.get("pitch").getAsFloat()),
                pointFromJson(object.getAsJsonObject("eye")),
                hit
        );
    }

    private JsonObject pointToJson(WorldPoint point) {
        JsonObject object = new JsonObject();
        object.addProperty("x", point.x());
        object.addProperty("y", point.y());
        object.addProperty("z", point.z());
        return object;
    }

    private WorldPoint pointFromJson(JsonObject object) {
        return new WorldPoint(object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble());
    }

    private Path calibrationPath(String name) {
        return calibrationsPath.resolve(name + ".json");
    }

    private String sanitizeName(String value) {
        return ConfigManager.sanitizeCalibrationName(value);
    }

    private void clearRecordingState() {
        recordingName = null;
        recordingWidth = 0;
        recordingHeight = 0;
        nextRecordingIndex = 0;
        savedExactCount = 0;
    }

    private void clearLoadedSource() {
        activeSource = CalibrationSource.NONE;
        activeDirection = null;
        bundledPortableOverride = false;
    }

    private int recordingOrDefaultWidth() {
        return recordingWidth > 0 ? recordingWidth : 32;
    }

    private int recordingOrDefaultHeight() {
        return recordingHeight > 0 ? recordingHeight : 32;
    }

    private String formatPoint(WorldPoint point) {
        return String.format("%.3f %.3f %.3f", point.x(), point.y(), point.z());
    }

    public record CalibrationFileInfo(String name, int savedCount, int total, String status) {
    }

    private enum CalibrationSource {
        NONE("none"),
        USER_FILE("user file"),
        BUNDLED("bundled");

        private final String label;

        CalibrationSource(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}

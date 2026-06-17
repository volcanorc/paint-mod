package com.artmapcolorassistant;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class CanvasCalibration {
    private final EnumMap<CalibrationPoint, CalibrationSample> points = new EnumMap<>(CalibrationPoint.class);
    private final Map<Integer, CalibrationSample> exactSamples = new HashMap<>();
    private String loadedName;
    private int loadedWidth;
    private int loadedHeight;

    public void set(CalibrationPoint point, CalibrationSample sample) {
        points.put(point, sample);
    }

    public void clear() {
        points.clear();
        clearExact();
    }

    public boolean complete() {
        for (CalibrationPoint point : CalibrationPoint.values()) {
            if (!points.containsKey(point)) {
                return false;
            }
        }
        return true;
    }

    public CalibrationSample get(CalibrationPoint point) {
        return points.get(point);
    }

    public void setExact(int index, CalibrationSample sample) {
        exactSamples.put(index, sample);
    }

    public CalibrationSample exact(int index) {
        return exactSamples.get(index);
    }

    public int exactCount() {
        return exactSamples.size();
    }

    public boolean hasExact(int index) {
        return exactSamples.containsKey(index);
    }

    public CalibrationSample removeExact(int index) {
        return exactSamples.remove(index);
    }

    public boolean exactComplete(int width, int height) {
        return exactSamples.size() >= width * height;
    }

    public void clearExact() {
        exactSamples.clear();
        loadedName = null;
        loadedWidth = 0;
        loadedHeight = 0;
    }

    public Map<Integer, CalibrationSample> exactSamples() {
        return Map.copyOf(exactSamples);
    }

    public void setLoadedMetadata(String name, int width, int height) {
        loadedName = name;
        loadedWidth = width;
        loadedHeight = height;
    }

    public String loadedExactName() {
        return loadedName;
    }

    public String exactStatus(int width, int height) {
        String name = loadedName == null ? "none" : loadedName;
        String size = loadedWidth > 0 && loadedHeight > 0 ? loadedWidth + "x" + loadedHeight : "unknown";
        return "Exact calibration=" + name + " size=" + size + " points=" + exactSamples.size() + "/" + (width * height);
    }

    public WorldPoint referenceEyePosition() {
        CalibrationSample sample = points.get(CalibrationPoint.TOP_LEFT);
        if (sample != null) {
            return sample.eyePosition();
        }
        CalibrationSample exactSample = exactSamples.get(0);
        if (exactSample != null) {
            return exactSample.eyePosition();
        }
        return points.values().stream()
                .findFirst()
                .map(CalibrationSample::eyePosition)
                .or(() -> exactSamples.values().stream().findFirst().map(CalibrationSample::eyePosition))
                .orElse(null);
    }

    public String status() {
        if (points.isEmpty()) {
            return "Calibration empty.";
        }
        return points.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().commandName() + "=" + format(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String format(CalibrationSample sample) {
        String hit = sample.hitPosition() == null ? "none" : formatPoint(sample.hitPosition());
        return String.format("yaw=%.2f pitch=%.2f hit=%s",
                sample.angles().yaw(), sample.angles().pitch(), hit);
    }

    private String formatPoint(WorldPoint point) {
        return String.format("%.3f %.3f %.3f", point.x(), point.y(), point.z());
    }
}

package com.artmapcolorassistant;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class CalibrationMarkerRenderer {
    private static final double MARKER_HALF_SIZE = 0.05D;
    private final MinecraftClient client;
    private final CalibrationManager calibrationManager;
    private boolean visible = true;
    private int totalSamples;
    private int drawableSamples;
    private int drawnLastFrame;

    public CalibrationMarkerRenderer(MinecraftClient client, CalibrationManager calibrationManager) {
        this.client = client;
        this.calibrationManager = calibrationManager;
    }

    public void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!visible || client.world == null || context.matrixStack() == null || context.consumers() == null) {
                recountSamples();
                drawnLastFrame = 0;
                return;
            }
            Vec3d camera = context.camera().getPos();
            recountSamples();
            int drawn = 0;
            for (Map.Entry<Integer, CalibrationSample> entry : calibrationManager.exactSamples().entrySet()) {
                CalibrationSample sample = entry.getValue();
                if (sample.hitPosition() == null) {
                    continue;
                }
                WorldPoint point = sample.hitPosition();
                double x = point.x() - camera.x;
                double y = point.y() - camera.y;
                double z = point.z() - camera.z;
                Box box = new Box(
                        x - MARKER_HALF_SIZE,
                        y - MARKER_HALF_SIZE,
                        z - MARKER_HALF_SIZE,
                        x + MARKER_HALF_SIZE,
                        y + MARKER_HALF_SIZE,
                        z + MARKER_HALF_SIZE
                );
                if (calibrationManager.recording() && entry.getKey() == calibrationManager.recordingNextIndex() - 1) {
                    DebugRenderer.drawBox(context.matrixStack(), context.consumers(), box, 0.2F, 1.0F, 0.2F, 1.0F);
                } else {
                    DebugRenderer.drawBox(context.matrixStack(), context.consumers(), box, 1.0F, 0.85F, 0.1F, 1.0F);
                }
                drawn++;
            }
            drawnLastFrame = drawn;
        });
    }

    public boolean visible() {
        return visible;
    }

    public int totalSamples() {
        return totalSamples;
    }

    public int drawableSamples() {
        return drawableSamples;
    }

    public int drawnLastFrame() {
        return drawnLastFrame;
    }

    public void toggle(SessionController.MessageSink sink) {
        visible = !visible;
        recountSamples();
        if (!visible) {
            sink.info("Calibration markers hidden.");
            return;
        }
        sink.info("Calibration markers shown. Drawable points: " + drawableSamples + "/" + totalSamples + ".");
        if (drawableSamples == 0) {
            sink.error("No calibration hit positions recorded yet. Aim at the canvas surface while right-clicking calibration points.");
        }
    }

    private void recountSamples() {
        totalSamples = calibrationManager.exactSamples().size();
        drawableSamples = 0;
        for (CalibrationSample sample : calibrationManager.exactSamples().values()) {
            if (sample.hitPosition() != null) {
                drawableSamples++;
            }
        }
    }
}

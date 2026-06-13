package com.artmapcolorassistant;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class HudOverlay {
    private final MinecraftClient client;
    private final SessionController controller;
    private final AutoPainter autoPainter;
    private final CalibrationManager calibrationManager;
    private final CalibrationMarkerRenderer markerRenderer;

    public HudOverlay(MinecraftClient client, SessionController controller, AutoPainter autoPainter,
                      CalibrationManager calibrationManager, CalibrationMarkerRenderer markerRenderer) {
        this.client = client;
        this.controller = controller;
        this.autoPainter = autoPainter;
        this.calibrationManager = calibrationManager;
        this.markerRenderer = markerRenderer;
    }

    public void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> render(drawContext));
    }

    private void render(DrawContext context) {
        PaintSession session = controller.session();
        int y = 8;
        y = renderCalibration(context, y);
        if (session == null) {
            return;
        }
        PaintStep current = session.currentStep();
        PaintStep next = session.nextStep();
        draw(context, "ArtMap: " + session.filename(), y);
        y += 10;
        draw(context, "Pixel: " + Math.min(session.currentIndex() + 1, session.steps().size()) + " / " + session.steps().size(), y);
        y += 10;
        if (current != null) {
            draw(context, "Pos: x=" + current.x() + " y=" + current.y(), y);
            y += 10;
            draw(context, "Current: " + stepLabel(current), y);
            y += 10;
        }
        draw(context, "Next: " + stepLabel(next), y);
        y += 10;
        draw(context, "Paused: " + session.paused(), y);
        y += 10;
        draw(context, "Calibration: " + (calibrationManager.complete() ? "complete" : "incomplete"), y);
        y += 10;
        if (autoPainter.running()) {
            draw(context, autoPainter.status(), y);
            y += 10;
        }
        if (session.warning() != null) {
            draw(context, session.warning(), y);
            y += 10;
        }
        draw(context, "Click row-by-row: left to right, next row", y);
    }

    private int renderCalibration(DrawContext context, int y) {
        if (!calibrationManager.recording()) {
            return y;
        }
        draw(context, "Calibration: " + calibrationManager.recordingName(), y);
        y += 10;
        draw(context, "Recorded: " + calibrationManager.recordingCurrentCount() + " / " + calibrationManager.recordingTotal()
                + " saved=" + calibrationManager.recordingSavedCount()
                + " unsaved=" + calibrationManager.recordingUnsavedCount(), y);
        y += 10;
        draw(context, "Markers: " + (markerRenderer.visible() ? "ON" : "OFF") + " (K)"
                + " drawn=" + markerRenderer.drawnLastFrame()
                + "/" + markerRenderer.drawableSamples(), y);
        y += 10;
        if (calibrationManager.recordingNextX() >= 0) {
            draw(context, "Next: x=" + calibrationManager.recordingNextX() + " y=" + calibrationManager.recordingNextY(), y);
            y += 10;
        }
        return y + 4;
    }

    private String stepLabel(PaintStep step) {
        if (step == null) {
            return "none";
        }
        if (step.skip()) {
            return "SKIP";
        }
        return step.matchedColor().name() + " " + step.item();
    }

    private void draw(DrawContext context, String text, int y) {
        context.drawTextWithShadow(client.textRenderer, text, 8, y, 0xFFFFFF);
    }
}

package com.artmapcolorassistant;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeybindHandler {
    private static final String CATEGORY = "category.artmap_color_assistant";
    private KeyBinding pauseResume;
    private KeyBinding back;
    private KeyBinding skip;
    private KeyBinding stop;
    private KeyBinding status;
    private KeyBinding advance;
    private KeyBinding toggleCalibrationMarkers;
    private KeyBinding undoCalibrationPoint;

    public void register() {
        pauseResume = register("key.artmap_color_assistant.pause_resume", GLFW.GLFW_KEY_P);
        back = register("key.artmap_color_assistant.back", GLFW.GLFW_KEY_LEFT_BRACKET);
        skip = register("key.artmap_color_assistant.skip", GLFW.GLFW_KEY_RIGHT_BRACKET);
        stop = register("key.artmap_color_assistant.stop", GLFW.GLFW_KEY_O);
        status = register("key.artmap_color_assistant.status", GLFW.GLFW_KEY_I);
        advance = register("key.artmap_color_assistant.advance", GLFW.GLFW_KEY_APOSTROPHE);
        toggleCalibrationMarkers = register("key.artmap_color_assistant.toggle_calibration_markers", GLFW.GLFW_KEY_K);
        undoCalibrationPoint = register("key.artmap_color_assistant.undo_calibration_point", GLFW.GLFW_KEY_BACKSPACE);
    }

    public void tick(SessionController controller, AutoPainter autoPainter, CalibrationManager calibrationManager,
                     CalibrationMarkerRenderer markerRenderer, SessionController.MessageSink sink) {
        while (pauseResume.wasPressed()) {
            if (autoPainter.running()) {
                if (autoPainter.paused()) {
                    autoPainter.resume(sink);
                } else {
                    autoPainter.pause(sink);
                }
                continue;
            }
            PaintSession session = controller.session();
            if (session != null && session.paused()) {
                controller.resume(sink);
            } else {
                controller.pause(sink);
            }
        }
        while (back.wasPressed()) {
            controller.back(sink);
        }
        while (skip.wasPressed()) {
            controller.skip(sink);
        }
        while (stop.wasPressed()) {
            if (autoPainter.running()) {
                autoPainter.emergencyStop(sink);
            } else {
                controller.stop(sink);
            }
        }
        while (status.wasPressed()) {
            controller.status(sink);
            if (autoPainter.running()) {
                sink.info(autoPainter.status());
            }
        }
        while (advance.wasPressed()) {
            controller.onAdvanceInput(sink);
        }
        while (toggleCalibrationMarkers.wasPressed()) {
            markerRenderer.toggle(sink);
        }
        while (undoCalibrationPoint.wasPressed()) {
            calibrationManager.undoLastRecordingPoint(sink);
        }
    }

    private KeyBinding register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                keyCode,
                CATEGORY
        ));
    }
}

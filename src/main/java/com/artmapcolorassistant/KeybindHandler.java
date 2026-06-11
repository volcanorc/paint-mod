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

    public void register() {
        pauseResume = register("key.artmap_color_assistant.pause_resume", GLFW.GLFW_KEY_P);
        back = register("key.artmap_color_assistant.back", GLFW.GLFW_KEY_LEFT_BRACKET);
        skip = register("key.artmap_color_assistant.skip", GLFW.GLFW_KEY_RIGHT_BRACKET);
        stop = register("key.artmap_color_assistant.stop", GLFW.GLFW_KEY_O);
        status = register("key.artmap_color_assistant.status", GLFW.GLFW_KEY_I);
        advance = register("key.artmap_color_assistant.advance", GLFW.GLFW_KEY_APOSTROPHE);
    }

    public void tick(SessionController controller, SessionController.MessageSink sink) {
        while (pauseResume.wasPressed()) {
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
            controller.stop(sink);
        }
        while (status.wasPressed()) {
            controller.status(sink);
        }
        while (advance.wasPressed()) {
            controller.onAdvanceInput(sink);
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

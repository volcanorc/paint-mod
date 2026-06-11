package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class ClickTracker {
    private final MinecraftClient client;
    private boolean leftDown;
    private boolean rightDown;

    public ClickTracker(MinecraftClient client) {
        this.client = client;
    }

    public void tick(ConfigManager.Config config, boolean confirmMode, SessionController controller, SessionController.MessageSink sink) {
        long handle = client.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean leftPressed = left && !leftDown;
        boolean rightPressed = right && !rightDown;
        leftDown = left;
        rightDown = right;

        if (confirmMode || client.player == null || client.currentScreen instanceof ChatScreen || client.currentScreen != null) {
            return;
        }
        if (config.onlyAdvanceWhenCrosshairTargetExists()
                && (client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.MISS)) {
            return;
        }
        if ((config.advanceOnLeftClick() && leftPressed) || (config.advanceOnRightClick() && rightPressed)) {
            controller.onAdvanceInput(sink);
        }
    }
}

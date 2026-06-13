package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class GuiClickRecorder {
    private final MinecraftClient client;
    private final ConfigManager configManager;
    private Target target = Target.NONE;

    public GuiClickRecorder(MinecraftClient client, ConfigManager configManager) {
        this.client = client;
        this.configManager = configManager;
    }

    public void armRename(SessionController.MessageSink sink) {
        target = Target.RENAME;
        sink.info("Rename click recording armed. Open the ArtMap save GUI and click the save/done item location.");
    }

    public void armPv2(SessionController.MessageSink sink) {
        target = Target.PV2;
        sink.info("PV2 click recording armed. Open /pv 2 and click the item location to shift-click during automation.");
    }

    public void clearRename(SessionController.MessageSink sink) {
        configManager.setPostPaintRenameClickPoint(null, text -> sink.error(text.getString()));
        sink.info("Rename click point cleared.");
    }

    public void clearPv2(SessionController.MessageSink sink) {
        configManager.setPostPaintPv2ClickPoint(null, text -> sink.error(text.getString()));
        sink.info("PV2 click point cleared.");
    }

    public boolean tick(boolean leftPressed, boolean rightPressed, SessionController.MessageSink sink) {
        if (target == Target.NONE || (!leftPressed && !rightPressed)) {
            return false;
        }
        if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            sink.error("Open the target GUI first, then click the point to record.");
            return true;
        }
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), x, y);
        double scaledX = x[0] * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double scaledY = y[0] * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        RecordedClickPoint point = new RecordedClickPoint(
                scaledX,
                scaledY,
                scaledX / client.getWindow().getScaledWidth(),
                scaledY / client.getWindow().getScaledHeight(),
                rightPressed ? 1 : 0
        );
        if (target == Target.RENAME) {
            configManager.setPostPaintRenameClickPoint(point, text -> sink.error(text.getString()));
            sink.info("Recorded rename click point x=" + Math.round(scaledX) + " y=" + Math.round(scaledY) + ".");
        } else {
            configManager.setPostPaintPv2ClickPoint(point, text -> sink.error(text.getString()));
            sink.info("Recorded PV2 click point x=" + Math.round(scaledX) + " y=" + Math.round(scaledY) + ".");
        }
        target = Target.NONE;
        return true;
    }

    private enum Target {
        NONE,
        RENAME,
        PV2
    }
}

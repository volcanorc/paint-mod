package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.GameRendererInvoker;
import com.artmapcolorassistant.mixin.HandledScreenInvoker;
import com.artmapcolorassistant.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PostPaintWorkflow {
    private static final int SCREEN_TIMEOUT_TICKS = 120;
    private static final int ITEM_TIMEOUT_TICKS = 120;
    private static final int SHORT_WAIT_TICKS = 8;

    private final MinecraftClient client;
    private final CalibrationManager calibrationManager;
    private boolean active;
    private int imageNumber;
    private String suffix = "";
    private int waitTicks;
    private int timeoutTicks;
    private int aimSettleTicks;
    private int rightClickAttempts;
    private int funJumpsRemaining;
    private int funJumpPressTicksRemaining;
    private int funJumpGapTicksRemaining;
    private boolean funJumpKeyHeld;
    private Phase phase = Phase.IDLE;

    public PostPaintWorkflow(MinecraftClient client, CalibrationManager calibrationManager) {
        this.client = client;
        this.calibrationManager = calibrationManager;
    }

    public boolean active() {
        return active;
    }

    public String statusLine() {
        return active
                ? "postpaint=" + phase + " rightClickAttempts=" + rightClickAttempts
                + (phase == Phase.FUN_JUMP_PRESS || phase == Phase.FUN_JUMP_GAP ? " funJumpsRemaining=" + funJumpsRemaining : "")
                : "postpaint=idle";
    }

    public void start(int imageNumber, String suffix, SessionController.MessageSink sink) {
        this.active = true;
        this.imageNumber = imageNumber;
        this.suffix = suffix == null ? "" : suffix.trim();
        this.waitTicks = 0;
        this.timeoutTicks = 0;
        this.aimSettleTicks = 0;
        this.rightClickAttempts = 0;
        this.funJumpsRemaining = 0;
        this.funJumpPressTicksRemaining = 0;
        this.funJumpGapTicksRemaining = 0;
        this.funJumpKeyHeld = false;
        this.phase = Phase.SELECT_SAVE_ITEM;
        sink.info("Post-paint automation started for save name \"" + saveName() + "\".");
    }

    public void stop() {
        releaseJumpKey();
        active = false;
        waitTicks = 0;
        timeoutTicks = 0;
        aimSettleTicks = 0;
        rightClickAttempts = 0;
        funJumpsRemaining = 0;
        funJumpPressTicksRemaining = 0;
        funJumpGapTicksRemaining = 0;
        phase = Phase.IDLE;
    }

    public Result tick(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!active) {
            return Result.IDLE;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return Result.RUNNING;
        }
        switch (phase) {
            case SELECT_SAVE_ITEM -> selectSaveItem(config, sink);
            case WAIT_SAVE_SELECT -> waitSaveSelect(config, sink);
            case AIM_SAVE_TARGET -> aimSaveTarget(config, sink);
            case RIGHT_CLICK_SAVE -> rightClickSave(config, sink);
            case WAIT_RENAME_SCREEN -> waitRenameScreen(config, sink);
            case TYPE_RENAME -> typeRename(config, sink);
            case CLICK_RENAME_POINT -> clickRenamePoint(config, sink);
            case WAIT_FINISHED_ITEM -> waitFinishedItem(config, sink);
            case OPEN_PV2 -> openPv2(config, sink);
            case WAIT_PV2_SCREEN -> waitPv2Screen(sink);
            case SHIFT_CLICK_PV2_POINT -> shiftClickPv2Point(config, sink);
            case WAIT_FINISHED_ITEM_REMOVED -> waitFinishedItemRemoved(config, sink);
            case CLOSE_PV2 -> closePv2(sink);
            case SELECT_BLANK_CANVAS -> selectBlankCanvas(config, sink);
            case PLACE_BLANK_CANVAS -> placeBlankCanvas(config, sink);
            case FUN_JUMP_START -> startFunJumps(config);
            case FUN_JUMP_PRESS -> pressFunJump(config);
            case FUN_JUMP_GAP -> gapFunJump(config);
            case ENTER_EASEL -> enterEasel(config, sink);
            case COMPLETE -> {
                stop();
                return Result.COMPLETE;
            }
            case FAILED -> {
                stop();
                return Result.FAILED;
            }
            case IDLE -> {
            }
        }
        return active ? Result.RUNNING : Result.FAILED;
    }

    private void selectSaveItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hasPlayer(sink)) {
            fail();
            return;
        }
        if (!hotbarNonEmpty(config.postPaintSaveHotbarSlot())) {
            fail(sink, "Post-paint save item missing from hotbar slot " + (config.postPaintSaveHotbarSlot() + 1) + ".");
            return;
        }
        if (hotbarNonEmpty(config.postPaintFinishedHotbarSlot())) {
            fail(sink, "Post-paint finished-item slot " + (config.postPaintFinishedHotbarSlot() + 1) + " is not empty.");
            return;
        }
        client.player.getInventory().selectedSlot = config.postPaintSaveHotbarSlot();
        waitTicks = config.postPaintSaveSelectDelayTicks();
        phase = Phase.WAIT_SAVE_SELECT;
    }

    private void waitSaveSelect(ConfigManager.Config config, SessionController.MessageSink sink) {
        phase = Phase.AIM_SAVE_TARGET;
        aimSettleTicks = Math.max(0, config.postPaintSaveAimSettleTicks());
    }

    private void aimSaveTarget(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtConfiguredIndex(config, sink)) {
            return;
        }
        if (aimSettleTicks > 0) {
            aimSettleTicks--;
            return;
        }
        phase = Phase.RIGHT_CLICK_SAVE;
    }

    private void rightClickSave(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtConfiguredIndex(config, sink)) {
            return;
        }
        rightClickAttempts++;
        rightClick();
        timeoutTicks = SCREEN_TIMEOUT_TICKS;
        phase = Phase.WAIT_RENAME_SCREEN;
    }

    private void waitRenameScreen(ConfigManager.Config config, SessionController.MessageSink sink) {
        Screen screen = client.currentScreen;
        if (screen != null && !(screen instanceof ChatScreen)) {
            phase = Phase.TYPE_RENAME;
            waitTicks = config.postPaintRenameOpenDelayTicks();
            return;
        }
        if (--timeoutTicks <= 0) {
            if (rightClickAttempts <= config.postPaintRightClickRetries()) {
                sink.info("Post-paint save GUI did not open yet. Retrying right-click "
                        + rightClickAttempts + "/" + config.postPaintRightClickRetries() + ".");
                phase = Phase.AIM_SAVE_TARGET;
                aimSettleTicks = Math.max(0, config.postPaintSaveAimSettleTicks());
                return;
            }
            fail(sink, "Post-paint save GUI did not open after right-click. Check save item, easel aim, and calibration index 500.");
        }
    }

    private void typeRename(ConfigManager.Config config, SessionController.MessageSink sink) {
        Screen screen = client.currentScreen;
        if (screen == null || screen instanceof ChatScreen) {
            fail(sink, "Rename GUI closed before the title could be entered.");
            return;
        }
        if (!clearAndTypeRename(screen, saveName()) && config.debug()) {
            sink.info("Rename text field was not found directly; used keyboard clear fallback.");
        }
        phase = Phase.CLICK_RENAME_POINT;
        waitTicks = SHORT_WAIT_TICKS;
    }

    private boolean clearAndTypeRename(Screen screen, String name) {
        if (setTextFieldValue(screen, name)) {
            return true;
        }
        keyboardClearAndType(screen, name);
        return false;
    }

    private boolean setTextFieldValue(Object root, String name) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return setTextFieldValue(root, name, seen, 0);
    }

    private boolean setTextFieldValue(Object value, String name, Set<Object> seen, int depth) {
        if (value == null || depth > 5 || seen.contains(value)) {
            return false;
        }
        seen.add(value);
        if (value instanceof TextFieldWidget textField) {
            textField.setFocused(true);
            textField.setText("");
            textField.setText(name);
            return true;
        }
        if (value instanceof Collection<?> collection) {
            for (Object child : collection) {
                if (setTextFieldValue(child, name, seen, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!shouldInspectField(field)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (setTextFieldValue(child, name, seen, depth + 1)) {
                        return true;
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private boolean shouldInspectField(Field field) {
        Class<?> type = field.getType();
        return TextFieldWidget.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || type.getName().startsWith("net.minecraft.client.gui");
    }

    private void keyboardClearAndType(Screen screen, String name) {
        for (int i = 0; i < 2; i++) {
            screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
        }
        for (char character : name.toCharArray()) {
            screen.charTyped(character, 0);
        }
    }

    private void clickRenamePoint(ConfigManager.Config config, SessionController.MessageSink sink) {
        Screen screen = client.currentScreen;
        if (screen == null || screen instanceof ChatScreen) {
            fail(sink, "Rename GUI is not open for the recorded rename click.");
            return;
        }
        if (config.postPaintRenameClickPoint() == null) {
            fail(sink, "Run #painting rename click, then click the save/done item in the rename GUI.");
            return;
        }
        clickScreenPoint(screen, config.postPaintRenameClickPoint());
        timeoutTicks = ITEM_TIMEOUT_TICKS;
        phase = Phase.WAIT_FINISHED_ITEM;
    }

    private void waitFinishedItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (hotbarNonEmpty(config.postPaintFinishedHotbarSlot())) {
            phase = Phase.OPEN_PV2;
            waitTicks = SHORT_WAIT_TICKS;
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, "Saved canvas did not appear in hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1) + ".");
        }
    }

    private void openPv2(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hasPlayer(sink)) {
            fail();
            return;
        }
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        String command = config.postPaintVaultCommand().trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isBlank()) {
            fail(sink, "postPaintVaultCommand is blank.");
            return;
        }
        client.player.networkHandler.sendChatCommand(command);
        timeoutTicks = SCREEN_TIMEOUT_TICKS;
        phase = Phase.WAIT_PV2_SCREEN;
    }

    private void waitPv2Screen(SessionController.MessageSink sink) {
        if (client.currentScreen instanceof HandledScreen<?>) {
            phase = Phase.SHIFT_CLICK_PV2_POINT;
            waitTicks = SHORT_WAIT_TICKS;
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, "/pv 2 did not open a handled vault screen. Store the canvas manually.");
        }
    }

    private void shiftClickPv2Point(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!(client.currentScreen instanceof HandledScreen<?> screen) || client.interactionManager == null || client.player == null) {
            fail(sink, "PV2 vault screen is not open for the recorded shift-click.");
            return;
        }
        RecordedClickPoint point = config.postPaintPv2ClickPoint();
        if (point == null) {
            fail(sink, "Run #painting pv2 click, then Shift-click the finished item location in /pv 2.");
            return;
        }
        Slot slot = ((HandledScreenInvoker) screen).artmapColorAssistant$getSlotAt(
                point.replayX(client.getWindow().getScaledWidth()),
                point.replayY(client.getWindow().getScaledHeight())
        );
        if (slot == null) {
            fail(sink, "Recorded PV2 click point is not over a slot. Re-record with #painting pv2 click.");
            return;
        }
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
        timeoutTicks = ITEM_TIMEOUT_TICKS;
        phase = Phase.WAIT_FINISHED_ITEM_REMOVED;
    }

    private void waitFinishedItemRemoved(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hotbarNonEmpty(config.postPaintFinishedHotbarSlot())) {
            phase = Phase.CLOSE_PV2;
            waitTicks = SHORT_WAIT_TICKS;
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, "Finished canvas stayed in hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1) + ". Vault may be full or click point is wrong.");
        }
    }

    private void closePv2(SessionController.MessageSink sink) {
        if (client.player != null) {
            client.player.closeHandledScreen();
        }
        client.setScreen(null);
        phase = Phase.SELECT_BLANK_CANVAS;
        waitTicks = SHORT_WAIT_TICKS;
    }

    private void selectBlankCanvas(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hasPlayer(sink)) {
            fail();
            return;
        }
        if (!hotbarNonEmpty(config.postPaintBlankCanvasHotbarSlot())) {
            fail(sink, "Blank canvas missing from hotbar slot " + (config.postPaintBlankCanvasHotbarSlot() + 1) + ".");
            return;
        }
        client.player.getInventory().selectedSlot = config.postPaintBlankCanvasHotbarSlot();
        phase = Phase.PLACE_BLANK_CANVAS;
        waitTicks = SHORT_WAIT_TICKS;
    }

    private void placeBlankCanvas(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtConfiguredIndex(config, sink)) {
            return;
        }
        rightClick();
        phase = config.postPaintFunJumpsEnabled() && config.postPaintFunJumpCount() > 0
                ? Phase.FUN_JUMP_START
                : Phase.ENTER_EASEL;
        waitTicks = SHORT_WAIT_TICKS;
    }

    private void startFunJumps(ConfigManager.Config config) {
        funJumpsRemaining = Math.max(0, config.postPaintFunJumpCount());
        funJumpPressTicksRemaining = 0;
        funJumpGapTicksRemaining = 0;
        phase = funJumpsRemaining > 0 ? Phase.FUN_JUMP_PRESS : Phase.ENTER_EASEL;
    }

    private void pressFunJump(ConfigManager.Config config) {
        if (client.player == null || client.world == null) {
            releaseJumpKey();
            phase = Phase.FAILED;
            return;
        }
        if (!funJumpKeyHeld) {
            client.options.jumpKey.setPressed(true);
            funJumpKeyHeld = true;
            funJumpPressTicksRemaining = Math.max(1, config.postPaintFunJumpPressTicks());
        }
        funJumpPressTicksRemaining--;
        if (funJumpPressTicksRemaining > 0) {
            return;
        }
        releaseJumpKey();
        funJumpsRemaining--;
        if (funJumpsRemaining <= 0) {
            phase = Phase.ENTER_EASEL;
            waitTicks = SHORT_WAIT_TICKS;
            return;
        }
        funJumpGapTicksRemaining = Math.max(0, config.postPaintFunJumpGapTicks());
        phase = funJumpGapTicksRemaining > 0 ? Phase.FUN_JUMP_GAP : Phase.FUN_JUMP_PRESS;
    }

    private void gapFunJump(ConfigManager.Config config) {
        releaseJumpKey();
        funJumpGapTicksRemaining--;
        if (funJumpGapTicksRemaining <= 0) {
            phase = Phase.FUN_JUMP_PRESS;
        }
    }

    private void enterEasel(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!aimAtConfiguredIndex(config, sink)) {
            return;
        }
        rightClick();
        phase = Phase.COMPLETE;
        waitTicks = SHORT_WAIT_TICKS;
        sink.info("Post-paint setup complete. Starting next batch image.");
    }

    private boolean aimAtConfiguredIndex(ConfigManager.Config config, SessionController.MessageSink sink) {
        int total = config.canvasWidth() * config.canvasHeight();
        int index = config.postPaintAimCalibrationIndex();
        if (index < 0 || index >= total) {
            fail(sink, "postPaintAimCalibrationIndex must be 0-" + (total - 1) + ".");
            return false;
        }
        int x = CanvasMath.toX(index, config.canvasWidth());
        int y = CanvasMath.toY(index, config.canvasWidth());
        if (!calibrationManager.aimAt(x, y, config)) {
            fail(sink, "Could not aim at calibration index " + index + ". Load exact calibration or choose another index.");
            return false;
        }
        return true;
    }

    private void clickScreenPoint(Screen screen, RecordedClickPoint point) {
        screen.mouseClicked(
                point.replayX(client.getWindow().getScaledWidth()),
                point.replayY(client.getWindow().getScaledHeight()),
                point.button()
        );
    }

    private void rightClick() {
        ((GameRendererInvoker) client.gameRenderer).artmapColorAssistant$updateCrosshairTarget(client.getRenderTickCounter().getTickDelta(false));
        ((MinecraftClientInvoker) client).artmapColorAssistant$doItemUse();
    }

    private boolean hasPlayer(SessionController.MessageSink sink) {
        if (client.player == null || client.world == null) {
            sink.error("Post-paint automation needs an active world and player.");
            return false;
        }
        return true;
    }

    private boolean hotbarNonEmpty(int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null || slot < 0 || slot > 8 || slot >= player.getInventory().main.size()) {
            return false;
        }
        ItemStack stack = player.getInventory().main.get(slot);
        return stack != null && !stack.isEmpty();
    }

    private String saveName() {
        return suffix.isBlank() ? Integer.toString(imageNumber) : imageNumber + " " + suffix;
    }

    private void fail() {
        releaseJumpKey();
        phase = Phase.FAILED;
    }

    private void fail(SessionController.MessageSink sink, String message) {
        releaseJumpKey();
        sink.error(message);
        phase = Phase.FAILED;
    }

    private void releaseJumpKey() {
        if (funJumpKeyHeld) {
            client.options.jumpKey.setPressed(false);
            funJumpKeyHeld = false;
        }
    }

    public enum Result {
        IDLE,
        RUNNING,
        COMPLETE,
        FAILED
    }

    private enum Phase {
        IDLE,
        SELECT_SAVE_ITEM,
        WAIT_SAVE_SELECT,
        AIM_SAVE_TARGET,
        RIGHT_CLICK_SAVE,
        WAIT_RENAME_SCREEN,
        TYPE_RENAME,
        CLICK_RENAME_POINT,
        WAIT_FINISHED_ITEM,
        OPEN_PV2,
        WAIT_PV2_SCREEN,
        SHIFT_CLICK_PV2_POINT,
        WAIT_FINISHED_ITEM_REMOVED,
        CLOSE_PV2,
        SELECT_BLANK_CANVAS,
        PLACE_BLANK_CANVAS,
        FUN_JUMP_START,
        FUN_JUMP_PRESS,
        FUN_JUMP_GAP,
        ENTER_EASEL,
        COMPLETE,
        FAILED
    }
}

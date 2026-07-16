package com.artmapcolorassistant;

import com.artmapcolorassistant.mixin.GameRendererInvoker;
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
    private static final int PLAYER_VAULT_SLOT_ACTION_DELAY_TICKS = 20;

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
    private int overflowClearAttempts;
    private boolean funJumpKeyHeld;
    private ItemStack pendingVaultTransferStack = ItemStack.EMPTY;
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
        this.overflowClearAttempts = 0;
        this.funJumpKeyHeld = false;
        this.pendingVaultTransferStack = ItemStack.EMPTY;
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
        overflowClearAttempts = 0;
        pendingVaultTransferStack = ItemStack.EMPTY;
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
            case OPEN_OVERFLOW_PLAYER_VAULT -> openOverflowPlayerVault(sink);
            case WAIT_OVERFLOW_PLAYER_VAULT_SCREEN -> waitOverflowPlayerVaultScreen(config, sink);
            case QUICK_MOVE_BLOCKER_ITEM -> quickMoveBlockerItem(config, sink);
            case WAIT_BLOCKER_ITEM_REMOVED -> waitBlockerItemRemoved(config, sink);
            case CLOSE_OVERFLOW_PLAYER_VAULT -> closeOverflowPlayerVault();
            case WAIT_SAVE_SELECT -> waitSaveSelect(config, sink);
            case AIM_SAVE_TARGET -> aimSaveTarget(config, sink);
            case RIGHT_CLICK_SAVE -> rightClickSave(config, sink);
            case WAIT_RENAME_SCREEN -> waitRenameScreen(config, sink);
            case TYPE_RENAME -> typeRename(config, sink);
            case CLICK_RENAME_POINT -> clickRenamePoint(config, sink);
            case WAIT_FINISHED_ITEM -> waitFinishedItem(config, sink);
            case OPEN_PLAYER_VAULT -> openPlayerVault(config, sink);
            case WAIT_PLAYER_VAULT_SCREEN -> waitPlayerVaultScreen(config, sink);
            case QUICK_MOVE_FINISHED_ITEM -> quickMoveFinishedItem(config, sink);
            case WAIT_FINISHED_ITEM_REMOVED -> waitFinishedItemRemoved(config, sink);
            case CLOSE_PLAYER_VAULT -> closePlayerVault(sink);
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
            startOverflowBlockerClear(config, sink);
            return;
        }
        overflowClearAttempts = 0;
        client.player.getInventory().selectedSlot = config.postPaintSaveHotbarSlot();
        waitTicks = config.postPaintSaveSelectDelayTicks();
        phase = Phase.WAIT_SAVE_SELECT;
    }

    private void startOverflowBlockerClear(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!PostPaintOverflowPolicy.canTryClear(overflowClearAttempts)) {
            fail(sink, "Post-paint finished-item slot " + (config.postPaintFinishedHotbarSlot() + 1)
                    + " kept refilling after " + PostPaintOverflowPolicy.MAX_CLEAR_ATTEMPTS
                    + " blocker clear attempt(s). Move nearby dropped items or clear the slot manually.");
            return;
        }
        pendingVaultTransferStack = hotbarStack(config.postPaintFinishedHotbarSlot()).copy();
        overflowClearAttempts++;
        sink.info("Post-paint finished-item slot " + (config.postPaintFinishedHotbarSlot() + 1)
                + " is blocked by \"" + pendingVaultTransferStack.getName().getString()
                + "\". Storing blocker in /pv 1 before retrying rename/save (attempt "
                + overflowClearAttempts + "/" + PostPaintOverflowPolicy.MAX_CLEAR_ATTEMPTS + ").");
        waitTicks = overflowDelayTicks();
        phase = Phase.OPEN_OVERFLOW_PLAYER_VAULT;
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
            phase = Phase.OPEN_PLAYER_VAULT;
            waitTicks = SHORT_WAIT_TICKS;
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, "Saved canvas did not appear in hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1) + ".");
        }
    }

    private void openOverflowPlayerVault(SessionController.MessageSink sink) {
        if (!hasPlayer(sink)) {
            fail();
            return;
        }
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        String command = PostPaintOverflowPolicy.VAULT_COMMAND.startsWith("/")
                ? PostPaintOverflowPolicy.VAULT_COMMAND.substring(1)
                : PostPaintOverflowPolicy.VAULT_COMMAND;
        client.player.networkHandler.sendChatCommand(command);
        timeoutTicks = SCREEN_TIMEOUT_TICKS;
        phase = Phase.WAIT_OVERFLOW_PLAYER_VAULT_SCREEN;
    }

    private void waitOverflowPlayerVaultScreen(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (client.currentScreen instanceof HandledScreen<?>) {
            phase = Phase.QUICK_MOVE_BLOCKER_ITEM;
            waitTicks = overflowDelayTicks();
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, PostPaintOverflowPolicy.VAULT_COMMAND + " did not open for temporary blocker storage. Clear hotbar slot "
                    + (config.postPaintFinishedHotbarSlot() + 1) + " manually.");
        }
    }

    private void quickMoveBlockerItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!(client.currentScreen instanceof HandledScreen<?>) || client.interactionManager == null || client.player == null) {
            fail(sink, PostPaintOverflowPolicy.VAULT_COMMAND + " is not open for temporary blocker transfer.");
            return;
        }
        ItemStack sourceStack = hotbarStack(config.postPaintFinishedHotbarSlot());
        if (sourceStack.isEmpty()) {
            phase = Phase.CLOSE_OVERFLOW_PLAYER_VAULT;
            waitTicks = overflowDelayTicks();
            return;
        }
        Slot slot = playerHotbarScreenSlot(config.postPaintFinishedHotbarSlot());
        if (slot == null) {
            fail(sink, "Could not find hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1)
                    + " in the open /pv 1 screen handler.");
            return;
        }
        pendingVaultTransferStack = sourceStack.copy();
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
        waitTicks = overflowDelayTicks();
        timeoutTicks = 1;
        phase = Phase.WAIT_BLOCKER_ITEM_REMOVED;
    }

    private void waitBlockerItemRemoved(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hotbarNonEmpty(config.postPaintFinishedHotbarSlot())) {
            phase = Phase.CLOSE_OVERFLOW_PLAYER_VAULT;
            waitTicks = overflowDelayTicks();
            return;
        }
        if (--timeoutTicks <= 0) {
            String itemName = pendingVaultTransferStack.isEmpty() ? "item" : pendingVaultTransferStack.getName().getString();
            fail(sink, "Temporary blocker \"" + itemName + "\" stayed in hotbar slot "
                    + (config.postPaintFinishedHotbarSlot() + 1)
                    + " after /pv 1 transfer. /pv 1 may be full; clear the slot manually.");
        }
    }

    private void closeOverflowPlayerVault() {
        if (client.player != null) {
            client.player.closeHandledScreen();
        }
        client.setScreen(null);
        pendingVaultTransferStack = ItemStack.EMPTY;
        phase = Phase.SELECT_SAVE_ITEM;
        waitTicks = overflowDelayTicks();
    }

    private void openPlayerVault(ConfigManager.Config config, SessionController.MessageSink sink) {
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
        phase = Phase.WAIT_PLAYER_VAULT_SCREEN;
    }

    private void waitPlayerVaultScreen(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (client.currentScreen instanceof HandledScreen<?>) {
            phase = Phase.QUICK_MOVE_FINISHED_ITEM;
            waitTicks = PLAYER_VAULT_SLOT_ACTION_DELAY_TICKS;
            return;
        }
        if (--timeoutTicks <= 0) {
            fail(sink, config.postPaintVaultCommand() + " did not open "
                    + PlayerVaultSelection.displayName(config.postPaintVaultCommand())
                    + ". Store the canvas manually.");
        }
    }

    private void quickMoveFinishedItem(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!(client.currentScreen instanceof HandledScreen<?>) || client.interactionManager == null || client.player == null) {
            fail(sink, PlayerVaultSelection.displayName(config.postPaintVaultCommand())
                    + " is not open for automatic slot transfer.");
            return;
        }
        ItemStack sourceStack = hotbarStack(config.postPaintFinishedHotbarSlot());
        if (sourceStack.isEmpty()) {
            fail(sink, "Finished canvas is missing from hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1)
                    + " before Player Vault transfer.");
            return;
        }
        Slot slot = playerHotbarScreenSlot(config.postPaintFinishedHotbarSlot());
        if (slot == null) {
            fail(sink, "Could not find hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1)
                    + " in the open Player Vault screen handler.");
            return;
        }
        pendingVaultTransferStack = sourceStack.copy();
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
        waitTicks = PLAYER_VAULT_SLOT_ACTION_DELAY_TICKS;
        timeoutTicks = 1;
        phase = Phase.WAIT_FINISHED_ITEM_REMOVED;
    }

    private void waitFinishedItemRemoved(ConfigManager.Config config, SessionController.MessageSink sink) {
        if (!hotbarNonEmpty(config.postPaintFinishedHotbarSlot())) {
            pendingVaultTransferStack = ItemStack.EMPTY;
            phase = Phase.CLOSE_PLAYER_VAULT;
            return;
        }
        if (--timeoutTicks <= 0) {
            String itemName = pendingVaultTransferStack.isEmpty() ? "item" : pendingVaultTransferStack.getName().getString();
            fail(sink, "Finished canvas stayed in hotbar slot " + (config.postPaintFinishedHotbarSlot() + 1)
                    + " after automatic Player Vault transfer. "
                    + PlayerVaultSelection.displayName(config.postPaintVaultCommand())
                    + " may be full. Store \"" + itemName
                    + "\" manually, then continue the batch.");
        }
    }

    private void closePlayerVault(SessionController.MessageSink sink) {
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
        return !hotbarStack(slot).isEmpty();
    }

    private ItemStack hotbarStack(int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null || slot < 0 || slot > 8 || slot >= player.getInventory().main.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().main.get(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private Slot playerHotbarScreenSlot(int hotbarSlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || hotbarSlot < 0 || hotbarSlot > 8) {
            return null;
        }
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory == player.getInventory() && slot.getIndex() == hotbarSlot) {
                return slot;
            }
        }
        return null;
    }

    private int overflowDelayTicks() {
        return PostPaintOverflowPolicy.randomDelayTicks();
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
        OPEN_OVERFLOW_PLAYER_VAULT,
        WAIT_OVERFLOW_PLAYER_VAULT_SCREEN,
        QUICK_MOVE_BLOCKER_ITEM,
        WAIT_BLOCKER_ITEM_REMOVED,
        CLOSE_OVERFLOW_PLAYER_VAULT,
        WAIT_SAVE_SELECT,
        AIM_SAVE_TARGET,
        RIGHT_CLICK_SAVE,
        WAIT_RENAME_SCREEN,
        TYPE_RENAME,
        CLICK_RENAME_POINT,
        WAIT_FINISHED_ITEM,
        OPEN_PLAYER_VAULT,
        WAIT_PLAYER_VAULT_SCREEN,
        QUICK_MOVE_FINISHED_ITEM,
        WAIT_FINISHED_ITEM_REMOVED,
        CLOSE_PLAYER_VAULT,
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

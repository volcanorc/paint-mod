package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class InventoryHelper {
    private final MinecraftClient client;
    private PendingSwap pendingSwap;

    public InventoryHelper(MinecraftClient client) {
        this.client = client;
    }

    public InventorySnapshot scan() {
        ClientPlayerEntity player = client.player;
        Map<Identifier, Integer> counts = new HashMap<>();
        if (player == null) {
            return new InventorySnapshot(counts);
        }
        for (int i = 0; i <= 35 && i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            Identifier id = Registries.ITEM.getId(stack.getItem());
            counts.put(id, counts.getOrDefault(id, 0) + stack.getCount());
        }
        return new InventorySnapshot(counts);
    }

    public OptionalInt findHotbar(ArtMapColor color) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return OptionalInt.empty();
        }
        for (int i = 0; i <= 8 && i < player.getInventory().main.size(); i++) {
            if (matches(player.getInventory().main.get(i), color)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public OptionalInt findMainInventory(ArtMapColor color) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return OptionalInt.empty();
        }
        for (int i = 9; i <= 35 && i < player.getInventory().main.size(); i++) {
            if (matches(player.getInventory().main.get(i), color)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public SwitchResult selectOrSwap(PaintStep step, ConfigManager.Config config, Consumer<Text> messageSink) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return SwitchResult.failed("No client player is available.");
        }
        if (step == null || step.skip()) {
            return SwitchResult.ok("Transparent SKIP step.");
        }
        ArtMapColor color = step.matchedColor();
        OptionalInt hotbarSlot = findHotbar(color);
        if (hotbarSlot.isPresent()) {
            player.getInventory().selectedSlot = hotbarSlot.getAsInt();
            return SwitchResult.ok("Selected hotbar slot " + hotbarSlot.getAsInt() + ".");
        }
        OptionalInt mainSlot = findMainInventory(color);
        if (mainSlot.isEmpty()) {
            return SwitchResult.failed(missingMessage(step));
        }
        if (!config.autoSwapFromInventory()) {
            return SwitchResult.failed("Required item " + color.item() + " is in main inventory, but autoSwapFromInventory is disabled. Put it in the hotbar.");
        }
        if (!canSwapNow()) {
            return SwitchResult.failed("Required item " + color.item() + " is not in the hotbar and a GUI/container is open. Put it in the hotbar.");
        }
        int reserved = config.reservedHotbarSlot();
        int screenSlot = mainSlot.getAsInt();
        if (client.interactionManager == null) {
            return SwitchResult.failed("Cannot swap inventory item: no interaction manager.");
        }
        client.interactionManager.clickSlot(player.currentScreenHandler.syncId, screenSlot, reserved, SlotActionType.SWAP, player);
        pendingSwap = new PendingSwap(color, reserved, step, 3);
        if (messageSink != null) {
            messageSink.accept(Text.literal("[ArtMap] Swapping " + color.item() + " into hotbar slot " + reserved + "."));
        }
        return SwitchResult.pending("Waiting for inventory sync.");
    }

    public SwitchResult tickPendingSwap() {
        if (pendingSwap == null) {
            return SwitchResult.ok("No pending swap.");
        }
        ClientPlayerEntity player = client.player;
        PendingSwap swap = pendingSwap;
        if (player == null) {
            pendingSwap = null;
            return SwitchResult.failed("Swap failed: no client player.");
        }
        if (swap.ticksRemaining-- > 0 && !matches(player.getInventory().main.get(swap.reservedHotbarSlot), swap.color)) {
            return SwitchResult.pending("Waiting for inventory sync.");
        }
        if (matches(player.getInventory().main.get(swap.reservedHotbarSlot), swap.color)) {
            player.getInventory().selectedSlot = swap.reservedHotbarSlot;
            pendingSwap = null;
            return SwitchResult.ok("Swap verified.");
        }
        pendingSwap = null;
        return SwitchResult.failed("Inventory swap failed. Put " + swap.color.item() + " in the hotbar for pixel "
                + swap.step.index() + " x=" + swap.step.x() + " y=" + swap.step.y() + ".");
    }

    public boolean hasPendingSwap() {
        return pendingSwap != null;
    }

    public boolean itemExists(ArtMapColor color) {
        InventorySnapshot snapshot = scan();
        return snapshot.availableItemIds().contains(color.item())
                || (color.legacyItem() != null && snapshot.availableItemIds().contains(color.legacyItem()));
    }

    private boolean canSwapNow() {
        return client.currentScreen == null || client.currentScreen instanceof ChatScreen;
    }

    private boolean matches(ItemStack stack, ArtMapColor color) {
        if (stack == null || stack.isEmpty() || color == null) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return color.matches(id);
    }

    public static String missingMessage(PaintStep step) {
        ArtMapColor color = step.matchedColor();
        return "Missing item " + color.item() + " (" + color.name() + ") for pixel "
                + step.index() + " x=" + step.x() + " y=" + step.y() + ". Put it in the hotbar.";
    }

    public record InventorySnapshot(Map<Identifier, Integer> counts) {
        public Set<Identifier> availableItemIds() {
            return new HashSet<>(counts.keySet());
        }
    }

    public record SwitchResult(boolean success, boolean pending, String message) {
        public static SwitchResult ok(String message) {
            return new SwitchResult(true, false, message);
        }

        public static SwitchResult pending(String message) {
            return new SwitchResult(false, true, message);
        }

        public static SwitchResult failed(String message) {
            return new SwitchResult(false, false, message);
        }
    }

    private static final class PendingSwap {
        private final ArtMapColor color;
        private final int reservedHotbarSlot;
        private final PaintStep step;
        private int ticksRemaining;

        private PendingSwap(ArtMapColor color, int reservedHotbarSlot, PaintStep step, int ticksRemaining) {
            this.color = color;
            this.reservedHotbarSlot = reservedHotbarSlot;
            this.step = step;
            this.ticksRemaining = ticksRemaining;
        }
    }
}

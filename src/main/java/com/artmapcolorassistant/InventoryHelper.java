package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

public final class InventoryHelper {
    private static final int OFFHAND_INVENTORY_INDEX = 40;

    private final MinecraftClient client;
    private PendingSwap pendingSwap;
    private PendingBucketSetup pendingBucketSetup;

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
        int reserved = safeReservedHotbarSlot(config.reservedHotbarSlot());
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
        if (pendingBucketSetup != null) {
            return tickPendingBucketSetup();
        }
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
        return pendingSwap != null || pendingBucketSetup != null;
    }

    public boolean itemExists(ArtMapColor color) {
        InventorySnapshot snapshot = scan();
        return snapshot.availableItemIds().contains(color.item())
                || (color.legacyItem() != null && snapshot.availableItemIds().contains(color.legacyItem()));
    }

    public boolean exactEmptyBucketInOffhand() {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getOffHandStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return Identifier.of("minecraft", "bucket").equals(Registries.ITEM.getId(stack.getItem()));
    }

    public boolean exactEmptyBucketAvailableForOffhand() {
        return exactEmptyBucketInOffhand() || findExactBucketSlot() != null;
    }

    public boolean exactEmptyBucketInMainHand() {
        ClientPlayerEntity player = client.player;
        return player != null && exactBucket(player.getMainHandStack());
    }

    public SwitchResult prepareExactEmptyBucketInOffhand(Consumer<Text> messageSink) {
        if (exactEmptyBucketInOffhand()) {
            return SwitchResult.ok("Exact empty bucket already in offhand.");
        }
        if (pendingBucketSetup != null) {
            return SwitchResult.pending("Waiting for bucket/offhand inventory sync.");
        }
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return SwitchResult.failed("No client player is available for bucket setup.");
        }
        if (!canSwapNow()) {
            return SwitchResult.failed("Cannot move bucket to offhand while a GUI/container is open.");
        }
        if (client.interactionManager == null) {
            return SwitchResult.failed("Cannot move bucket to offhand: no interaction manager.");
        }
        if (!cursorEmpty()) {
            return SwitchResult.failed("Cannot move bucket to offhand while the cursor is holding an item.");
        }
        SlotRef offhand = findPlayerInventorySlot(OFFHAND_INVENTORY_INDEX);
        if (offhand == null) {
            return SwitchResult.failed("Could not find the player offhand slot for bucket setup.");
        }
        SlotRef bucket = findExactBucketSlot();
        if (bucket == null) {
            return SwitchResult.failed("Exact minecraft:bucket is missing. Put an empty bucket in inventory or offhand.");
        }
        boolean offhandOccupied = !player.getOffHandStack().isEmpty();
        SlotRef parking = null;
        if (!player.getOffHandStack().isEmpty()) {
            parking = findEmptyParkingSlot();
            if (parking == null) {
                return SwitchResult.failed("Offhand is occupied and no empty inventory slot is available to park it.");
            }
            if (messageSink != null) {
                messageSink.accept(Text.literal("[ArtMap] Will park occupied offhand item before delayed bucket setup."));
            }
        }
        pendingBucketSetup = new PendingBucketSetup(BucketOffhandSetupPlan.steps(offhandOccupied),
                offhand, parking, bucket, BucketOffhandSetupPlan.randomDelayTicks());
        if (messageSink != null) {
            messageSink.accept(Text.literal("[ArtMap] Starting delayed minecraft:bucket offhand setup for Smart bucket painting."));
        }
        return SwitchResult.pending("Delayed bucket/offhand setup started.");
    }

    public boolean mainHandMatches(ArtMapColor color) {
        ClientPlayerEntity player = client.player;
        return player != null && matches(player.getMainHandStack(), color);
    }

    public boolean offHandMatches(ArtMapColor color) {
        ClientPlayerEntity player = client.player;
        return player != null && matches(player.getOffHandStack(), color);
    }

    public boolean bucketPairReady(ArtMapColor color) {
        return mainHandMatches(color) && exactEmptyBucketInOffhand();
    }

    public boolean bucketPairSwapped(ArtMapColor color) {
        return exactEmptyBucketInMainHand() && offHandMatches(color);
    }

    public boolean requestSwapHands() {
        ClientPlayerEntity player = client.player;
        if (player == null || player.networkHandler == null) {
            return false;
        }
        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
        ));
        return true;
    }

    public String bucketHandStatus(ArtMapColor color) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return "main=unavailable offhand=unavailable";
        }
        Identifier main = itemId(player.getMainHandStack());
        Identifier off = itemId(player.getOffHandStack());
        return "main=" + (main == null ? "empty" : main)
                + " offhand=" + (off == null ? "empty" : off)
                + " ready=" + bucketPairReady(color)
                + " swapped=" + bucketPairSwapped(color);
    }

    private boolean canSwapNow() {
        return client.currentScreen == null || client.currentScreen instanceof ChatScreen;
    }

    private boolean cursorEmpty() {
        ClientPlayerEntity player = client.player;
        return player != null && player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private SwitchResult tickPendingBucketSetup() {
        PendingBucketSetup setup = pendingBucketSetup;
        ClientPlayerEntity player = client.player;
        if (player == null) {
            pendingBucketSetup = null;
            return SwitchResult.failed("Bucket setup failed: no client player.");
        }
        if (!canSwapNow()) {
            pendingBucketSetup = null;
            return SwitchResult.failed("Bucket setup stopped because a GUI/container opened.");
        }
        if (client.interactionManager == null) {
            pendingBucketSetup = null;
            return SwitchResult.failed("Bucket setup failed: no interaction manager.");
        }
        if (setup.delayTicks-- > 0) {
            return SwitchResult.pending("Waiting before next bucket/offhand setup action.");
        }
        if (setup.stepCursor >= setup.steps.size()) {
            pendingBucketSetup = null;
            if (exactEmptyBucketInOffhand() && cursorEmpty()) {
                return SwitchResult.ok("Empty bucket moved to offhand.");
            }
            return SwitchResult.failed("Could not verify an exact empty bucket in offhand. Put minecraft:bucket in offhand manually.");
        }

        BucketOffhandSetupPlan.Step step = setup.steps.get(setup.stepCursor);
        String validation = validateBucketSetupStep(setup, step);
        if (validation != null) {
            pendingBucketSetup = null;
            return SwitchResult.failed(validation);
        }
        clickPickup(slotForBucketSetupStep(setup, step).screenSlotId);
        setup.stepCursor++;
        setup.delayTicks = BucketOffhandSetupPlan.randomDelayTicks();
        return SwitchResult.pending("Performed delayed bucket/offhand setup action " + setup.stepCursor
                + "/" + setup.steps.size() + ".");
    }

    private String validateBucketSetupStep(PendingBucketSetup setup, BucketOffhandSetupPlan.Step step) {
        return switch (step) {
            case PICK_OFFHAND -> {
                if (!cursorEmpty()) {
                    yield "Bucket setup stopped because the cursor is no longer empty before parking offhand.";
                }
                if (slotStack(setup.offhandSlot).isEmpty()) {
                    yield "Bucket setup stopped because offhand became empty before parking.";
                }
                yield null;
            }
            case PLACE_PARKED_OFFHAND -> {
                if (setup.parkingSlot == null) {
                    yield "Bucket setup stopped because no parking slot is available.";
                }
                if (cursorEmpty()) {
                    yield "Bucket setup stopped because the cursor is empty before placing the parked offhand item.";
                }
                if (!slotStack(setup.parkingSlot).isEmpty()) {
                    yield "Bucket setup stopped because the parking slot is no longer empty.";
                }
                yield null;
            }
            case PICK_BUCKET -> {
                if (!cursorEmpty()) {
                    yield "Bucket setup stopped because the cursor is not empty before picking up the bucket.";
                }
                if (!exactBucket(slotStack(setup.bucketSlot))) {
                    yield "Bucket setup stopped because the exact minecraft:bucket moved before pickup.";
                }
                yield null;
            }
            case PLACE_BUCKET_OFFHAND -> {
                if (!exactBucket(cursorStack())) {
                    yield "Bucket setup stopped because the cursor is not holding exact minecraft:bucket.";
                }
                if (!slotStack(setup.offhandSlot).isEmpty()) {
                    yield "Bucket setup stopped because offhand is no longer empty before placing bucket.";
                }
                yield null;
            }
        };
    }

    private SlotRef slotForBucketSetupStep(PendingBucketSetup setup, BucketOffhandSetupPlan.Step step) {
        return switch (step) {
            case PICK_OFFHAND, PLACE_BUCKET_OFFHAND -> setup.offhandSlot;
            case PLACE_PARKED_OFFHAND -> setup.parkingSlot;
            case PICK_BUCKET -> setup.bucketSlot;
        };
    }

    private ItemStack cursorStack() {
        ClientPlayerEntity player = client.player;
        return player == null ? ItemStack.EMPTY : player.currentScreenHandler.getCursorStack();
    }

    private ItemStack slotStack(SlotRef ref) {
        ClientPlayerEntity player = client.player;
        if (player == null || ref == null || ref.inventoryIndex < 0) {
            return ItemStack.EMPTY;
        }
        if (ref.inventoryIndex == OFFHAND_INVENTORY_INDEX) {
            return player.getOffHandStack();
        }
        if (ref.inventoryIndex >= player.getInventory().main.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().main.get(ref.inventoryIndex);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private void clickPickup(int screenSlotId) {
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, screenSlotId, 0,
                SlotActionType.PICKUP, client.player);
    }

    private SlotRef findExactBucketSlot() {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return null;
        }
        for (int i = 0; i <= 35 && i < player.getInventory().main.size(); i++) {
            if (exactBucket(player.getInventory().main.get(i))) {
                SlotRef ref = findPlayerInventorySlot(i);
                if (ref != null) {
                    return ref;
                }
            }
        }
        return null;
    }

    private SlotRef findEmptyParkingSlot() {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return null;
        }
        for (int i = 9; i <= 35 && i < player.getInventory().main.size(); i++) {
            if (player.getInventory().main.get(i).isEmpty()) {
                SlotRef ref = findPlayerInventorySlot(i);
                if (ref != null) {
                    return ref;
                }
            }
        }
        int selected = player.getInventory().selectedSlot;
        for (int i = 0; i <= 8 && i < player.getInventory().main.size(); i++) {
            if (i != selected && player.getInventory().main.get(i).isEmpty()) {
                SlotRef ref = findPlayerInventorySlot(i);
                if (ref != null) {
                    return ref;
                }
            }
        }
        return null;
    }

    private SlotRef findPlayerInventorySlot(int inventoryIndex) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return null;
        }
        for (int i = 0; i < player.currentScreenHandler.slots.size(); i++) {
            var slot = player.currentScreenHandler.slots.get(i);
            if (slot.inventory == player.getInventory() && slot.getIndex() == inventoryIndex) {
                return new SlotRef(slot.id, inventoryIndex);
            }
        }
        return null;
    }

    private int safeReservedHotbarSlot(int configured) {
        if (configured >= 3 && configured <= 8) {
            return configured;
        }
        return 8;
    }

    private boolean matches(ItemStack stack, ArtMapColor color) {
        if (stack == null || stack.isEmpty() || color == null) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return color.matches(id);
    }

    private boolean exactBucket(ItemStack stack) {
        return Identifier.of("minecraft", "bucket").equals(itemId(stack));
    }

    private Identifier itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return Registries.ITEM.getId(stack.getItem());
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

    private static final class PendingBucketSetup {
        private final List<BucketOffhandSetupPlan.Step> steps;
        private final SlotRef offhandSlot;
        private final SlotRef parkingSlot;
        private final SlotRef bucketSlot;
        private int stepCursor;
        private int delayTicks;

        private PendingBucketSetup(List<BucketOffhandSetupPlan.Step> steps, SlotRef offhandSlot,
                                   SlotRef parkingSlot, SlotRef bucketSlot, int delayTicks) {
            this.steps = steps;
            this.offhandSlot = offhandSlot;
            this.parkingSlot = parkingSlot;
            this.bucketSlot = bucketSlot;
            this.delayTicks = delayTicks;
        }
    }

    private record SlotRef(int screenSlotId, int inventoryIndex) {
    }
}

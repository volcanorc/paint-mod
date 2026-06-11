package com.artmapcolorassistant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtMapColorAssistantClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.MOD_ID);

    private static ArtMapColorAssistantClient instance;

    private ConfigManager configManager;
    private SessionController controller;
    private HashCommandHandler commandHandler;
    private ClickTracker clickTracker;
    private KeybindHandler keybindHandler;

    @Override
    public void onInitializeClient() {
        instance = this;
        MinecraftClient client = MinecraftClient.getInstance();
        configManager = new ConfigManager();
        configManager.load(this::sendError);

        InventoryHelper inventoryHelper = new InventoryHelper(client);
        controller = new SessionController(configManager, new ImageLoader(), inventoryHelper, new ColorMatcher());
        commandHandler = new HashCommandHandler(configManager, controller);
        clickTracker = new ClickTracker(client);
        keybindHandler = new KeybindHandler();
        keybindHandler.register();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (HashCommandHandler.isPaintingCommand(message)) {
                handleLocalPaintingCommand(message);
                return false;
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        new HudOverlay(client, controller).register();
        LOGGER.info("ArtMapColorAssistant initialized");
    }

    public static boolean handleLocalPaintingCommand(String message) {
        if (!HashCommandHandler.isPaintingCommand(message)) {
            return false;
        }
        if (instance == null || instance.commandHandler == null) {
            LOGGER.warn("Canceled local painting command before ArtMapColorAssistant was fully initialized.");
            return true;
        }
        instance.commandHandler.handle(message, instance.sink());
        return true;
    }

    private void tick(MinecraftClient client) {
        SessionController.MessageSink sink = sink();
        controller.tick(sink);
        keybindHandler.tick(controller, sink);
        clickTracker.tick(configManager.config(), commandHandler.confirmMode(), controller, sink);
    }

    private SessionController.MessageSink sink() {
        return new SessionController.MessageSink() {
            @Override
            public void info(String message) {
                sendInfo(message);
            }

            @Override
            public void error(String message) {
                sendError(Text.literal(message));
            }
        };
    }

    private void sendInfo(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[ArtMap] " + message), false);
        } else {
            LOGGER.info(message);
        }
    }

    private void sendError(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = message.getString();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[ArtMap] " + text), false);
        } else {
            LOGGER.warn(text);
        }
    }
}

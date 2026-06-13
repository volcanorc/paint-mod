package com.artmapcolorassistant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtMapColorAssistantClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.MOD_ID);

    private static ArtMapColorAssistantClient instance;

    private ConfigManager configManager;
    private SessionController controller;
    private CalibrationManager calibrationManager;
    private CalibrationMarkerRenderer calibrationMarkerRenderer;
    private AutoPainter autoPainter;
    private BatchManager batchManager;
    private PostPaintWorkflow postPaintWorkflow;
    private GuiClickRecorder guiClickRecorder;
    private HashCommandHandler commandHandler;
    private ClickTracker clickTracker;
    private KeybindHandler keybindHandler;
    private boolean openGuiPending;

    @Override
    public void onInitializeClient() {
        instance = this;
        MinecraftClient client = MinecraftClient.getInstance();
        configManager = new ConfigManager();
        configManager.load(this::sendError);

        InventoryHelper inventoryHelper = new InventoryHelper(client);
        controller = new SessionController(configManager, new ImageLoader(), inventoryHelper, new ColorMatcher());
        calibrationManager = new CalibrationManager(client, configManager.calibrationsPath());
        calibrationManager.setLastCalibrationName(configManager.config().selectedCalibrationName());
        calibrationMarkerRenderer = new CalibrationMarkerRenderer(client, calibrationManager);
        calibrationMarkerRenderer.register();
        autoPainter = new AutoPainter(client, controller, calibrationManager, configManager.config());
        postPaintWorkflow = new PostPaintWorkflow(client, calibrationManager);
        batchManager = new BatchManager(configManager, controller, autoPainter, postPaintWorkflow);
        guiClickRecorder = new GuiClickRecorder(client, configManager);
        commandHandler = new HashCommandHandler(configManager, controller, autoPainter, calibrationManager, batchManager, guiClickRecorder);
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
        new HudOverlay(client, controller, autoPainter, calibrationManager, calibrationMarkerRenderer).register();
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

    public static void requestGuiOpen() {
        if (instance != null) {
            instance.requestOpenGui();
        }
    }

    private void tick(MinecraftClient client) {
        SessionController.MessageSink sink = sink();
        if (openGuiPending && !(client.currentScreen instanceof ChatScreen)) {
            openGuiPending = false;
            client.setScreen(new PaintingControlScreen(commandHandler, autoPainter, calibrationManager, configManager, batchManager));
        }
        controller.tick(sink);
        autoPainter.tick(configManager.config(), sink);
        batchManager.tick(sink);
        keybindHandler.tick(controller, autoPainter, calibrationManager, calibrationMarkerRenderer, sink);
        clickTracker.tick(configManager.config(), commandHandler.confirmMode(), controller, calibrationManager, guiClickRecorder, sink);
    }

    void requestOpenGui() {
        openGuiPending = true;
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

            @Override
            public void info(Text message) {
                sendInfo(message);
            }

            @Override
            public void error(Text message) {
                sendError(message);
            }
        };
    }

    private void sendInfo(String message) {
        sendInfo(Text.literal(message));
    }

    private void sendInfo(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(prefix().append(message.copy().formatted(Formatting.YELLOW)), false);
        } else {
            LOGGER.info(message.getString());
        }
    }

    private void sendError(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = message.getString();
        if (client.player != null) {
            client.player.sendMessage(prefix().append(message.copy().formatted(Formatting.RED)), false);
        } else {
            LOGGER.warn(text);
        }
    }

    private MutableText prefix() {
        return Text.literal("[ArtMap] ").formatted(Formatting.GOLD);
    }
}

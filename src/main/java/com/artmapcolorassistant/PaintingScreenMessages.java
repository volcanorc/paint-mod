package com.artmapcolorassistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class PaintingScreenMessages {
    private PaintingScreenMessages() {
    }

    static SessionController.MessageSink chatSink() {
        return new SessionController.MessageSink() {
            @Override
            public void info(String message) {
                info(Text.literal(message));
            }

            @Override
            public void error(String message) {
                error(Text.literal(message));
            }

            @Override
            public void info(Text message) {
                send(message, Formatting.YELLOW);
            }

            @Override
            public void error(Text message) {
                send(message, Formatting.RED);
            }

            private void send(Text message, Formatting color) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player == null) {
                    return;
                }
                MutableText prefix = Text.literal("[ArtMap] ").formatted(Formatting.GOLD);
                client.player.sendMessage(prefix.append(message.copy().formatted(color)), false);
            }
        };
    }
}

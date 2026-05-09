package com.pinterestmod.client;

import com.pinterestmod.config.ModConfig;
import com.pinterestmod.gui.PinterestConfigScreen;
import com.pinterestmod.gui.PinterestOverlayScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class PinterestModClient implements ClientModInitializer {

    private boolean overlayKeyWasDown = false;
    private boolean configKeyWasDown  = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) {
                overlayKeyWasDown = false;
                configKeyWasDown  = false;
                return;
            }

            ModConfig cfg = ModConfig.get();
            long window = client.getWindow().getHandle();

            boolean shiftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT)  == GLFW.GLFW_PRESS
                             || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            boolean overlayKeyDown = GLFW.glfwGetKey(window, cfg.overlayKey) == GLFW.GLFW_PRESS;
            boolean overlayTrigger = overlayKeyDown && (!cfg.overlayShift || shiftDown);
            if (overlayTrigger && !overlayKeyWasDown) {
                client.execute(() -> client.setScreen(new PinterestOverlayScreen(null)));
            }
            overlayKeyWasDown = overlayTrigger;

            boolean configKeyDown = GLFW.glfwGetKey(window, cfg.configKey) == GLFW.GLFW_PRESS;
            boolean configTrigger = configKeyDown && (!cfg.configShift || shiftDown);
            if (configTrigger && !configKeyWasDown) {
                client.execute(() -> client.setScreen(new PinterestConfigScreen(null)));
            }
            configKeyWasDown = configTrigger;
        });

        System.out.println("[AspireMod] Loaded! Shift+V = Pinterest overlay, Shift+B = config.");
    }
}

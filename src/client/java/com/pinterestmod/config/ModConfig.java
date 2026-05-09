package com.pinterestmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("pinterestmod.json");
    private static ModConfig INSTANCE;

    public int overlayKey = 86;      // V
    public boolean overlayShift = true;
    public int configKey = 66;       // B
    public boolean configShift = true;

    public String pinterestEmail = "";
    public String pinterestLinked = "";
    public boolean isLinked = false;

    public int overlayX = -1;
    public int overlayY = -1;
    public int overlayWidth = 480;
    public int overlayHeight = 640;
    public double browserScale = 1.0; // 0.5 = half resolution (faster), 1.0 = full

    // Reference image pinned to screen
    public String refImageUrl = "";
    public int refImageX = -1;
    public int refImageY = 10;
    public int refImageW = 150;
    public int refImageH = 150;
    public boolean refImageVisible = false;

    public static ModConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader r = new FileReader(CONFIG_PATH.toFile())) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
                if (INSTANCE == null) INSTANCE = new ModConfig();
            } catch (Exception e) {
                System.err.println("[PinterestMod] Failed to load config, using defaults: " + e.getMessage());
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
        }
    }

    public void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            System.err.println("[PinterestMod] Failed to save config: " + e.getMessage());
        }
    }

    public String getOverlayKeyName() {
        return (overlayShift ? "SHIFT+" : "") + keyName(overlayKey);
    }

    public String getConfigKeyName() {
        return (configShift ? "SHIFT+" : "") + keyName(configKey);
    }

    public static String keyName(int key) {
        return switch (key) {
            case 32  -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BACKSPACE";
            default  -> {
                if (key >= 65 && key <= 90)  yield String.valueOf((char) key);
                if (key >= 48 && key <= 57)  yield String.valueOf((char) key);
                if (key >= 290 && key <= 301) yield "F" + (key - 289);
                yield "KEY_" + key;
            }
        };
    }
}

package com.pinterestmod.gui;

import com.pinterestmod.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

public class ReferenceImageHud {

    private static Identifier textureId = null;
    private static String loadedUrl = "";
    private static boolean loading = false;
    private static String errorMessage = "";

    private static final int MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_DIM = 1024;
    private static final Path CACHE_DIR = FabricLoader.getInstance().getGameDir()
            .resolve("pinterestmod_cache");

    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {}
    }

    public static void render(DrawContext ctx) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.refImageVisible || cfg.refImageUrl.isEmpty()) return;

        if (!cfg.refImageUrl.equals(loadedUrl) && !loading) {
            downloadImage(cfg.refImageUrl);
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int screenW = mc.getWindow().getScaledWidth();

        int x = cfg.refImageX < 0 ? screenW - cfg.refImageW - 10 : cfg.refImageX;
        int y = cfg.refImageY;
        int w = cfg.refImageW;
        int h = cfg.refImageH;

        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xAA000000);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA444444);

        if (textureId != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                    x, y, 0.0F, 0.0F, w, h, w, h);
        } else if (loading) {
            ctx.fill(x, y, x + w, y + h, 0xFF1E1E1E);
            String msg = "Loading...";
            ctx.drawText(mc.textRenderer, msg,
                    x + w / 2 - mc.textRenderer.getWidth(msg) / 2,
                    y + h / 2 - 4, 0xFFFFFFFF, false);
        } else if (!errorMessage.isEmpty()) {
            ctx.fill(x, y, x + w, y + h, 0xFF1E1E1E);
            ctx.drawText(mc.textRenderer, "Error",
                    x + w / 2 - mc.textRenderer.getWidth("Error") / 2,
                    y + h / 2 - 10, 0xFFFF6666, false);
            ctx.drawText(mc.textRenderer, errorMessage,
                    x + 4, y + h / 2 + 2, 0xFFAAAAAA, false);
        }
    }

    private static String urlHash(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    public static void downloadImage(String url) {
        loading = true;
        loadedUrl = url;
        errorMessage = "";

        Path cacheFile = CACHE_DIR.resolve(urlHash(url) + ".png");

        CompletableFuture.runAsync(() -> {
            try {
                byte[] data;
                if (Files.exists(cacheFile)) {
                    data = Files.readAllBytes(cacheFile);
                    System.out.println("[AspireMod] Loaded reference image from cache");
                } else {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);

                    int contentLength = conn.getContentLength();
                    if (contentLength > MAX_DOWNLOAD_BYTES) {
                        conn.disconnect();
                        loading = false;
                        loadedUrl = "";
                        errorMessage = "Image too large (>5MB)";
                        return;
                    }

                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int totalRead = 0;
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        totalRead += n;
                        if (totalRead > MAX_DOWNLOAD_BYTES) {
                            is.close();
                            conn.disconnect();
                            loading = false;
                            loadedUrl = "";
                            errorMessage = "Image too large (>5MB)";
                            return;
                        }
                        baos.write(buf, 0, n);
                    }
                    is.close();
                    conn.disconnect();
                    data = baos.toByteArray();

                    try {
                        Files.write(cacheFile, data);
                    } catch (IOException ignored) {}
                }

                MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (textureId != null) {
                            try {
                                MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
                            } catch (Exception ignored) {}
                            textureId = null;
                        }

                        NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                        int origW = img.getWidth();
                        int origH = img.getHeight();

                        if (origW > MAX_IMAGE_DIM || origH > MAX_IMAGE_DIM) {
                            double scale = Math.min((double) MAX_IMAGE_DIM / origW,
                                    (double) MAX_IMAGE_DIM / origH);
                            int newW = Math.max(1, (int) (origW * scale));
                            int newH = Math.max(1, (int) (origH * scale));
                            NativeImage scaled = new NativeImage(newW, newH, false);
                            for (int sy = 0; sy < newH; sy++) {
                                for (int sx = 0; sx < newW; sx++) {
                                    int srcX = Math.min((int) (sx / scale), origW - 1);
                                    int srcY = Math.min((int) (sy / scale), origH - 1);
                                    scaled.setColorArgb(sx, sy, img.getColorArgb(srcX, srcY));
                                }
                            }
                            img.close();
                            img = scaled;
                            System.out.println("[AspireMod] Scaled reference image from "
                                    + origW + "x" + origH + " to " + newW + "x" + newH);
                        }

                        Identifier id = Identifier.of("pinterestmod", "ref_image");
                        MinecraftClient.getInstance().getTextureManager()
                                .registerTexture(id, new NativeImageBackedTexture(() -> "pinterestmod:ref_image", img));
                        textureId = id;
                        loading = false;
                        System.out.println("[AspireMod] Reference image loaded: "
                                + img.getWidth() + "x" + img.getHeight());
                    } catch (Exception e) {
                        System.err.println("[AspireMod] Failed to load reference image: " + e.getMessage());
                        loading = false;
                        loadedUrl = "";
                        errorMessage = "Bad image";
                    }
                });
            } catch (Exception e) {
                System.err.println("[AspireMod] Failed to download reference image: " + e.getMessage());
                loading = false;
                loadedUrl = "";
                errorMessage = "Download failed";
            }
        });
    }

    public static void setImage(String url) {
        ModConfig cfg = ModConfig.get();
        cfg.refImageUrl = url;
        cfg.refImageVisible = true;
        cfg.save();
        downloadImage(url);
    }

    public static void clearImage() {
        if (textureId != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
            } catch (Exception ignored) {}
            textureId = null;
        }
        loadedUrl = "";
        loading = false;
        errorMessage = "";
        ModConfig cfg = ModConfig.get();
        cfg.refImageUrl = "";
        cfg.refImageVisible = false;
        cfg.save();
    }

    public static boolean isLoaded() { return textureId != null; }
    public static boolean isLoading() { return loading; }
    public static Identifier getTextureId() { return textureId; }
}

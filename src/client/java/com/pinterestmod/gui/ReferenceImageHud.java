package com.pinterestmod.gui;

import com.pinterestmod.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ReferenceImageHud {

    private static Identifier textureId = null;
    private static String loadedUrl = "";
    private static boolean loading = false;
    private static String errorMessage = "";

    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
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

        // Frame
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

    public static void downloadImage(String url) {
        loading = true;
        loadedUrl = url;
        errorMessage = "";
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                InputStream is = conn.getInputStream();
                byte[] data = is.readAllBytes();
                is.close();
                conn.disconnect();

                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
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

package com.pinterestmod.gui;

import com.pinterestmod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

public class PinterestOverlayScreen extends Screen {

    private int panelX, panelY, panelW, panelH;
    private static final int TITLE_BAR_H  = 24;
    private static final int MIN_W        = 280;
    private static final int MIN_H        = 200;
    private static final int RESIZE_HANDLE = 12;

    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private boolean resizing = false;
    private int resizeStartMouseX, resizeStartMouseY, resizeStartW, resizeStartH;

    private final Screen parentScreen;

    private static final int COLOR_BG        = 0xFF1E1E1E;
    private static final int COLOR_TITLE_BAR = 0xFFE60023;
    private static final int COLOR_BORDER    = 0xFF333333;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SUBTEXT   = 0xFFAAAAAA;
    private static final int COLOR_LINKED    = 0xFF4CAF50;
    private static final int COLOR_GUEST     = 0xFFFF9800;
    private static final int COLOR_RESIZE    = 0xFF555555;

    public PinterestOverlayScreen(Screen parent) {
        super(Text.literal("Pinterest"));
        this.parentScreen = parent;
        ModConfig cfg = ModConfig.get();
        panelW = cfg.overlayWidth;
        panelH = cfg.overlayHeight;
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        if (cfg.overlayX == -1 || cfg.overlayY == -1) {
            panelX = (this.width  - panelW) / 2;
            panelY = (this.height - panelH) / 2;
        } else {
            panelX = Math.max(0, Math.min(cfg.overlayX, this.width  - panelW));
            panelY = Math.max(0, Math.min(cfg.overlayY, this.height - panelH));
        }
        clampPanel();
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.clearChildren();
        ModConfig cfg = ModConfig.get();

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Open Pinterest in Browser"),
                btn -> openBrowser(cfg)
        ).dimensions(panelX + (panelW - 200) / 2, panelY + panelH - 100, 200, 20).build());

        if (!cfg.isLinked) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Link Pinterest Account"),
                    btn -> MinecraftClient.getInstance().setScreen(new PinterestConfigScreen(this))
            ).dimensions(panelX + (panelW - 180) / 2, panelY + panelH - 72, 180, 20).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Manage Account"),
                    btn -> MinecraftClient.getInstance().setScreen(new PinterestConfigScreen(this))
            ).dimensions(panelX + (panelW - 180) / 2, panelY + panelH - 72, 180, 20).build());
        }
    }

    private void openBrowser(ModConfig cfg) {
        String url = cfg.isLinked ? "https://www.pinterest.com/" : "https://www.pinterest.com/ideas/";
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            System.err.println("[AspireMod] Could not open browser: " + e.getMessage());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Shadow
        ctx.fill(panelX + 4, panelY + 4, panelX + panelW + 4, panelY + panelH + 4, 0x66000000);
        // Background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_BG);
        // Border
        ctx.fill(panelX,              panelY,               panelX + panelW,     panelY + 1,           COLOR_BORDER);
        ctx.fill(panelX,              panelY + panelH - 1,  panelX + panelW,     panelY + panelH,      COLOR_BORDER);
        ctx.fill(panelX,              panelY,               panelX + 1,          panelY + panelH,      COLOR_BORDER);
        ctx.fill(panelX + panelW - 1, panelY,               panelX + panelW,     panelY + panelH,      COLOR_BORDER);
        // Title bar
        ctx.fill(panelX, panelY, panelX + panelW, panelY + TITLE_BAR_H, COLOR_TITLE_BAR);

        ctx.drawText(this.textRenderer, "P  Pinterest",         panelX + 8,            panelY + 7,  COLOR_TEXT, false);
        ctx.drawText(this.textRenderer, "[cfg]",                panelX + panelW - 42,  panelY + 7,  COLOR_TEXT, false);

        // Close button
        int closeBg = (mouseX >= panelX + panelW - 22 && mouseX <= panelX + panelW - 2
                    && mouseY >= panelY + 2 && mouseY <= panelY + TITLE_BAR_H - 2)
                    ? 0xFFCC0000 : 0xFFAA0000;
        ctx.fill(panelX + panelW - 22, panelY + 2, panelX + panelW - 2, panelY + TITLE_BAR_H - 2, closeBg);
        ctx.drawText(this.textRenderer, "X", panelX + panelW - 15, panelY + 7, COLOR_TEXT, false);

        // Account status bar
        int statusY = panelY + TITLE_BAR_H + 4;
        ModConfig cfg = ModConfig.get();
        if (cfg.isLinked) {
            ctx.fill(panelX, statusY, panelX + panelW, statusY + 18, 0xFF1A3A1A);
            ctx.drawText(this.textRenderer, "* Linked: " + cfg.pinterestEmail, panelX + 8, statusY + 4, COLOR_LINKED, false);
        } else {
            ctx.fill(panelX, statusY, panelX + panelW, statusY + 18, 0xFF3A2A00);
            ctx.drawText(this.textRenderer, "* Viewing as Guest", panelX + 8, statusY + 4, COLOR_GUEST, false);
        }

        // Content area
        int contentY = statusY + 22;
        ctx.fill(panelX + 4, contentY, panelX + panelW - 4, panelY + panelH - 110, 0xFF161616);

        // Pinterest P logo block
        int logoSize = 36;
        int logoX = panelX + panelW / 2 - logoSize / 2;
        int logoY = contentY + 14;
        ctx.fill(logoX, logoY, logoX + logoSize, logoY + logoSize, COLOR_TITLE_BAR);
        ctx.drawText(this.textRenderer, "P", logoX + logoSize / 2 - 3, logoY + logoSize / 2 - 4, COLOR_TEXT, false);

        String mainText = cfg.isLinked ? "Your Pinterest Feed" : "Pinterest (Guest)";
        int tw = this.textRenderer.getWidth(mainText);
        ctx.drawText(this.textRenderer, mainText, panelX + panelW / 2 - tw / 2, logoY + logoSize + 8, COLOR_TEXT, false);

        String subText = cfg.isLinked ? "Click below to open in browser" : "Browse without an account";
        int sw = this.textRenderer.getWidth(subText);
        ctx.drawText(this.textRenderer, subText, panelX + panelW / 2 - sw / 2, logoY + logoSize + 20, COLOR_SUBTEXT, false);

        // Resize handle
        ctx.fill(panelX + panelW - RESIZE_HANDLE, panelY + panelH - RESIZE_HANDLE, panelX + panelW, panelY + panelH, COLOR_RESIZE);
        ctx.drawText(this.textRenderer, "//", panelX + panelW - RESIZE_HANDLE + 1, panelY + panelH - RESIZE_HANDLE + 2, 0xFFAAAAAA, false);

        // Keybind hint
        ctx.drawText(this.textRenderer, cfg.getConfigKeyName() + " = settings | " + cfg.getOverlayKeyName() + " = toggle",
                panelX + 6, panelY + panelH - 11, 0xFF666666, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();

        // Close button
        if (mx >= panelX + panelW - 22 && mx <= panelX + panelW - 2
                && my >= panelY + 2 && my <= panelY + TITLE_BAR_H - 2) {
            saveState();
            MinecraftClient.getInstance().setScreen(parentScreen);
            return true;
        }
        // Config button
        if (mx >= panelX + panelW - 48 && mx <= panelX + panelW - 24
                && my >= panelY + 2 && my <= panelY + TITLE_BAR_H - 2) {
            MinecraftClient.getInstance().setScreen(new PinterestConfigScreen(this));
            return true;
        }
        // Resize handle
        if (mx >= panelX + panelW - RESIZE_HANDLE && mx <= panelX + panelW
                && my >= panelY + panelH - RESIZE_HANDLE && my <= panelY + panelH) {
            resizing = true;
            resizeStartMouseX = mx; resizeStartMouseY = my;
            resizeStartW = panelW;  resizeStartH = panelH;
            return true;
        }
        // Drag title bar
        if (mx >= panelX && mx <= panelX + panelW - 22
                && my >= panelY && my <= panelY + TITLE_BAR_H) {
            dragging = true;
            dragOffX = mx - panelX;
            dragOffY = my - panelY;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        int mx = (int) click.x(), my = (int) click.y();
        if (dragging) {
            panelX = mx - dragOffX;
            panelY = my - dragOffY;
            clampPanel();
            rebuildButtons();
            return true;
        }
        if (resizing) {
            panelW = Math.max(MIN_W, resizeStartW + (mx - resizeStartMouseX));
            panelH = Math.max(MIN_H, resizeStartH + (my - resizeStartMouseY));
            clampPanel();
            rebuildButtons();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();
        ModConfig cfg = ModConfig.get();
        boolean shiftDown = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == cfg.overlayKey && shiftDown == cfg.overlayShift) {
            saveState();
            MinecraftClient.getInstance().setScreen(parentScreen);
            return true;
        }
        if (keyCode == cfg.configKey && shiftDown == cfg.configShift) {
            MinecraftClient.getInstance().setScreen(new PinterestConfigScreen(this));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveState();
            MinecraftClient.getInstance().setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void saveState() {
        ModConfig cfg = ModConfig.get();
        cfg.overlayX = panelX; cfg.overlayY = panelY;
        cfg.overlayWidth = panelW; cfg.overlayHeight = panelH;
        cfg.save();
    }

    private void clampPanel() {
        if (this.client == null) return;
        panelX = Math.max(0, Math.min(panelX, this.width  - panelW));
        panelY = Math.max(0, Math.min(panelY, this.height - panelH));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

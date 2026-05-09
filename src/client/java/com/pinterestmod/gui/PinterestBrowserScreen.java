package com.pinterestmod.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.pinterestmod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public class PinterestBrowserScreen extends Screen {

    private MCEFBrowser browser;
    private final Screen parentScreen;

    private int panelX, panelY, panelW, panelH;
    private static final int TITLE_BAR_H = 24;
    private static final int MIN_W = 280;
    private static final int MIN_H = 200;
    private static final int RESIZE_HANDLE = 12;

    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private boolean resizing = false;
    private int resizeStartMouseX, resizeStartMouseY, resizeStartW, resizeStartH;
    private boolean mcefFailed = false;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_TITLE_BAR = 0xFFE60023;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_RESIZE = 0xFF555555;
    private static final int COLOR_BTN = 0xFFAA0000;
    private static final int COLOR_BTN_HOVER = 0xFFCC0000;
    private static final int COLOR_NAV = 0xFF2A2A2A;
    private static final int COLOR_NAV_HOVER = 0xFF3A3A3A;

    public PinterestBrowserScreen(Screen parent) {
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
            panelX = (this.width - panelW) / 2;
            panelY = (this.height - panelH) / 2;
        } else {
            panelX = Math.max(0, Math.min(cfg.overlayX, this.width - panelW));
            panelY = Math.max(0, Math.min(cfg.overlayY, this.height - panelH));
        }
        clampPanel();

        if (browser == null && !mcefFailed) {
            try {
                if (!MCEF.isInitialized()) {
                    System.err.println("[AspireMod] MCEF is not initialized. Chromium may have failed to download or load.");
                    mcefFailed = true;
                } else {
                    browser = MCEF.createBrowser("https://www.pinterest.com/ideas/", true);
                }
            } catch (Exception e) {
                System.err.println("[AspireMod] Failed to create MCEF browser: " + e.getMessage());
                mcefFailed = true;
            }
        }
        resizeBrowser();
    }

    private int contentX() { return panelX + 1; }
    private int contentY() { return panelY + TITLE_BAR_H; }
    private int contentW() { return panelW - 2; }
    private int contentH() { return panelH - TITLE_BAR_H - 1; }

    private double guiScale() {
        return this.client != null ? this.client.getWindow().getScaleFactor() : 1.0;
    }

    private int browserMouseX(double mouseX) {
        return (int) ((mouseX - contentX()) * guiScale());
    }

    private int browserMouseY(double mouseY) {
        return (int) ((mouseY - contentY()) * guiScale());
    }

    private void resizeBrowser() {
        if (browser != null) {
            int bw = (int) (contentW() * guiScale());
            int bh = (int) (contentH() * guiScale());
            if (bw > 0 && bh > 0) {
                browser.resize(bw, bh);
            }
        }
    }

    private boolean isInContentArea(double mouseX, double mouseY) {
        return mouseX >= contentX() && mouseX < contentX() + contentW()
                && mouseY >= contentY() && mouseY < contentY() + contentH();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        resizeBrowser();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Shadow
        ctx.fill(panelX + 4, panelY + 4, panelX + panelW + 4, panelY + panelH + 4, 0x66000000);
        // Background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_BG);
        // Border
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, COLOR_BORDER);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COLOR_BORDER);
        ctx.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_BORDER);
        ctx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COLOR_BORDER);
        // Title bar
        ctx.fill(panelX, panelY, panelX + panelW, panelY + TITLE_BAR_H, COLOR_TITLE_BAR);

        ctx.drawText(this.textRenderer, "P", panelX + 8, panelY + 7, COLOR_TEXT, false);

        // Navigation buttons: back, forward, home
        int navX = panelX + 22;
        int navY = panelY + 3;
        int navSize = 18;
        int navGap = 2;

        // Back button
        boolean backHover = mouseX >= navX && mouseX <= navX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(navX, navY, navX + navSize, navY + navSize, backHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "<", navX + 5, navY + 4, COLOR_TEXT, false);

        // Forward button
        int fwdX = navX + navSize + navGap;
        boolean fwdHover = mouseX >= fwdX && mouseX <= fwdX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(fwdX, navY, fwdX + navSize, navY + navSize, fwdHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, ">", fwdX + 5, navY + 4, COLOR_TEXT, false);

        // Home button
        int homeX = fwdX + navSize + navGap;
        boolean homeHover = mouseX >= homeX && mouseX <= homeX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(homeX, navY, homeX + navSize, navY + navSize, homeHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "H", homeX + 5, navY + 4, COLOR_TEXT, false);

        // Login button
        int loginW = this.textRenderer.getWidth("Login") + 8;
        int loginX = homeX + navSize + navGap + 4;
        boolean loginHover = mouseX >= loginX && mouseX <= loginX + loginW
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(loginX, navY, loginX + loginW, navY + navSize, loginHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "Login", loginX + 4, navY + 4, COLOR_TEXT, false);

        // Close button
        int closeX = panelX + panelW - 22;
        boolean closeHover = mouseX >= closeX && mouseX <= panelX + panelW - 2
                && mouseY >= panelY + 2 && mouseY <= panelY + TITLE_BAR_H - 2;
        ctx.fill(closeX, panelY + 2, panelX + panelW - 2, panelY + TITLE_BAR_H - 2,
                closeHover ? COLOR_BTN_HOVER : COLOR_BTN);
        ctx.drawText(this.textRenderer, "X", closeX + 7, panelY + 7, COLOR_TEXT, false);

        // Browser content
        if (mcefFailed) {
            int centerX = panelX + panelW / 2;
            int msgY = contentY() + 20;
            String errTitle = "Browser Failed to Initialize";
            ctx.drawText(this.textRenderer, errTitle,
                    centerX - this.textRenderer.getWidth(errTitle) / 2, msgY, 0xFFFF6666, false);
            String errMsg1 = "MCEF could not load the Chromium engine.";
            ctx.drawText(this.textRenderer, errMsg1,
                    centerX - this.textRenderer.getWidth(errMsg1) / 2, msgY + 16, COLOR_TEXT, false);
            String errMsg2 = "Try: delete mcef-libraries folder in .minecraft/mods";
            ctx.drawText(this.textRenderer, errMsg2,
                    centerX - this.textRenderer.getWidth(errMsg2) / 2, msgY + 30, 0xFFAAAAAA, false);
            String errMsg3 = "and install Visual C++ Redistributable, then relaunch.";
            ctx.drawText(this.textRenderer, errMsg3,
                    centerX - this.textRenderer.getWidth(errMsg3) / 2, msgY + 42, 0xFFAAAAAA, false);
        } else {
            Identifier texLoc = getBrowserTextureLocation();
            if (texLoc != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texLoc,
                        contentX(), contentY(), 0.0F, 0.0F,
                        contentW(), contentH(), contentW(), contentH());
            } else {
                String loadMsg = "Loading Pinterest...";
                int tw = this.textRenderer.getWidth(loadMsg);
                ctx.drawText(this.textRenderer, loadMsg,
                        panelX + panelW / 2 - tw / 2, panelY + panelH / 2, COLOR_TEXT, false);
            }
        }

        // Resize handle
        ctx.fill(panelX + panelW - RESIZE_HANDLE, panelY + panelH - RESIZE_HANDLE,
                panelX + panelW, panelY + panelH, COLOR_RESIZE);
        ctx.drawText(this.textRenderer, "//",
                panelX + panelW - RESIZE_HANDLE + 1, panelY + panelH - RESIZE_HANDLE + 2,
                0xFFAAAAAA, false);

        // Status bar hint
        ModConfig cfg = ModConfig.get();
        ctx.drawText(this.textRenderer,
                cfg.getOverlayKeyName() + " = close | drag to move | corner to resize",
                panelX + 6, panelY + panelH - 11, 0xFF666666, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();

        // Close button
        if (mx >= panelX + panelW - 22 && mx <= panelX + panelW - 2
                && my >= panelY + 2 && my <= panelY + TITLE_BAR_H - 2) {
            saveAndClose();
            return true;
        }

        // Navigation buttons
        int navX = panelX + 22;
        int navY = panelY + 3;
        int navSize = 18;
        int navGap = 2;

        if (mx >= navX && mx <= navX + navSize && my >= navY && my <= navY + navSize) {
            if (browser != null && browser.canGoBack()) browser.goBack();
            return true;
        }
        int fwdX = navX + navSize + navGap;
        if (mx >= fwdX && mx <= fwdX + navSize && my >= navY && my <= navY + navSize) {
            if (browser != null && browser.canGoForward()) browser.goForward();
            return true;
        }
        int homeX = fwdX + navSize + navGap;
        if (mx >= homeX && mx <= homeX + navSize && my >= navY && my <= navY + navSize) {
            if (browser != null) {
                browser.loadURL("https://www.pinterest.com/ideas/");
            }
            return true;
        }
        // Login button
        int loginW = this.textRenderer.getWidth("Login") + 8;
        int loginX = homeX + navSize + navGap + 4;
        if (mx >= loginX && mx <= loginX + loginW && my >= navY && my <= navY + navSize) {
            if (browser != null) {
                browser.loadURL("https://www.pinterest.com/");
            }
            return true;
        }

        // Resize handle
        if (mx >= panelX + panelW - RESIZE_HANDLE && mx <= panelX + panelW
                && my >= panelY + panelH - RESIZE_HANDLE && my <= panelY + panelH) {
            resizing = true;
            resizeStartMouseX = mx;
            resizeStartMouseY = my;
            resizeStartW = panelW;
            resizeStartH = panelH;
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

        // Browser content area
        if (isInContentArea(mx, my) && browser != null) {
            browser.sendMousePress(browserMouseX(mx), browserMouseY(my), click.button());
            browser.setFocus(true);
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
            return true;
        }
        if (resizing) {
            panelW = Math.max(MIN_W, resizeStartW + (mx - resizeStartMouseX));
            panelH = Math.max(MIN_H, resizeStartH + (my - resizeStartMouseY));
            clampPanel();
            resizeBrowser();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            return true;
        }
        int mx = (int) click.x(), my = (int) click.y();
        if (isInContentArea(mx, my) && browser != null) {
            browser.sendMouseRelease(browserMouseX(mx), browserMouseY(my), click.button());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isInContentArea(mouseX, mouseY) && browser != null) {
            browser.sendMouseMove(browserMouseX(mouseX), browserMouseY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInContentArea(mouseX, mouseY) && browser != null) {
            browser.sendMouseWheel(browserMouseX(mouseX), browserMouseY(mouseY), scrollY * 120, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int modifiers = keyInput.modifiers();
        ModConfig cfg = ModConfig.get();
        boolean shiftDown = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (keyCode == cfg.overlayKey && shiftDown == cfg.overlayShift) {
            saveAndClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveAndClose();
            return true;
        }

        if (browser != null) {
            browser.sendKeyPress(keyCode, keyInput.scancode(), modifiers);
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        if (browser != null) {
            browser.sendKeyRelease(keyInput.key(), keyInput.scancode(), keyInput.modifiers());
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (browser != null && charInput.codepoint() != 0) {
            browser.sendKeyTyped((char) charInput.codepoint(), charInput.modifiers());
        }
        return true;
    }

    private void saveAndClose() {
        ModConfig cfg = ModConfig.get();
        cfg.overlayX = panelX;
        cfg.overlayY = panelY;
        cfg.overlayWidth = panelW;
        cfg.overlayHeight = panelH;
        cfg.save();
        closeBrowser();
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    @Override
    public void close() {
        closeBrowser();
        super.close();
    }

    private void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    private Identifier getBrowserTextureLocation() {
        if (browser == null || !browser.isTextureReady()) return null;
        try {
            Method m = browser.getClass().getMethod("getTextureLocation");
            return (Identifier) m.invoke(browser);
        } catch (Exception e) {
            return null;
        }
    }

    private void clampPanel() {
        if (this.client == null) return;
        panelX = Math.max(0, Math.min(panelX, this.width - panelW));
        panelY = Math.max(0, Math.min(panelY, this.height - panelH));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}

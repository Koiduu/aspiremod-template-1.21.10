package com.pinterestmod.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.pinterestmod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private static final int PIN_BAR_H = 22;
    private static final int MIN_W = 280;
    private static final int MIN_H = 200;
    private static final int RESIZE_HANDLE = 12;

    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private boolean resizing = false;
    private int resizeStartMouseX, resizeStartMouseY, resizeStartW, resizeStartH;
    private boolean mcefFailed = false;

    private boolean showPinBar = false;
    private TextFieldWidget urlField;

    private boolean draggingRef = false;
    private int refDragOffX, refDragOffY;
    private boolean resizingRef = false;
    private int refResizeStartMouseX, refResizeStartMouseY, refResizeStartW, refResizeStartH;

    private String statusMsg = "";
    private long statusMsgTime = 0;

    private static final int COLOR_BG = 0xFF1E1E1E;
    private static final int COLOR_TITLE_BAR = 0xFFE60023;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_RESIZE = 0xFF555555;
    private static final int COLOR_BTN = 0xFFAA0000;
    private static final int COLOR_BTN_HOVER = 0xFFCC0000;
    private static final int COLOR_NAV = 0xFF2A2A2A;
    private static final int COLOR_NAV_HOVER = 0xFF3A3A3A;
    private static final int COLOR_PIN = 0xFF388E3C;
    private static final int COLOR_PIN_HOVER = 0xFF4CAF50;

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
                    System.err.println("[AspireMod] MCEF is not initialized.");
                    mcefFailed = true;
                } else {
                    browser = MCEF.createBrowser("https://www.pinterest.com/ideas/", true);
                }
            } catch (Exception e) {
                System.err.println("[AspireMod] Failed to create MCEF browser: " + e.getMessage());
                mcefFailed = true;
            }
        }

        urlField = new TextFieldWidget(this.textRenderer, panelX + 4, panelY + TITLE_BAR_H + 3,
                panelW - 50, 16, Text.literal("URL"));
        urlField.setMaxLength(500);
        urlField.setPlaceholder(Text.literal("Paste image URL here..."));
        urlField.visible = showPinBar;
        urlField.active = showPinBar;
        addDrawableChild(urlField);

        resizeBrowser();
    }

    private int contentX() { return panelX + 1; }
    private int contentY() { return panelY + TITLE_BAR_H + (showPinBar ? PIN_BAR_H : 0); }
    private int contentW() { return panelW - 2; }
    private int contentH() { return panelH - TITLE_BAR_H - 1 - (showPinBar ? PIN_BAR_H : 0); }

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
        renderReferenceImage(ctx, mouseX, mouseY);

        ctx.fill(panelX + 4, panelY + 4, panelX + panelW + 4, panelY + panelH + 4, 0x66000000);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_BG);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, COLOR_BORDER);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COLOR_BORDER);
        ctx.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_BORDER);
        ctx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COLOR_BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + TITLE_BAR_H, COLOR_TITLE_BAR);

        ctx.drawText(this.textRenderer, "P", panelX + 8, panelY + 7, COLOR_TEXT, false);

        int navX = panelX + 22;
        int navY = panelY + 3;
        int navSize = 18;
        int navGap = 2;

        boolean backHover = mouseX >= navX && mouseX <= navX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(navX, navY, navX + navSize, navY + navSize, backHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "<", navX + 5, navY + 4, COLOR_TEXT, false);

        int fwdX = navX + navSize + navGap;
        boolean fwdHover = mouseX >= fwdX && mouseX <= fwdX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(fwdX, navY, fwdX + navSize, navY + navSize, fwdHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, ">", fwdX + 5, navY + 4, COLOR_TEXT, false);

        int homeX = fwdX + navSize + navGap;
        boolean homeHover = mouseX >= homeX && mouseX <= homeX + navSize
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(homeX, navY, homeX + navSize, navY + navSize, homeHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "H", homeX + 5, navY + 4, COLOR_TEXT, false);

        int loginW = this.textRenderer.getWidth("Login") + 8;
        int loginX = homeX + navSize + navGap + 4;
        boolean loginHover = mouseX >= loginX && mouseX <= loginX + loginW
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(loginX, navY, loginX + loginW, navY + navSize, loginHover ? COLOR_NAV_HOVER : COLOR_NAV);
        ctx.drawText(this.textRenderer, "Login", loginX + 4, navY + 4, COLOR_TEXT, false);

        ModConfig cfgRef = ModConfig.get();
        String pinText = cfgRef.refImageVisible ? "Unpin" : "Pin";
        int pinBtnW = this.textRenderer.getWidth(pinText) + 8;
        int pinX = loginX + loginW + 4;
        boolean pinHover = mouseX >= pinX && mouseX <= pinX + pinBtnW
                && mouseY >= navY && mouseY <= navY + navSize;
        ctx.fill(pinX, navY, pinX + pinBtnW, navY + navSize,
                pinHover ? COLOR_PIN_HOVER : COLOR_PIN);
        ctx.drawText(this.textRenderer, pinText, pinX + 4, navY + 4, COLOR_TEXT, false);

        int closeX = panelX + panelW - 22;
        boolean closeHover = mouseX >= closeX && mouseX <= panelX + panelW - 2
                && mouseY >= panelY + 2 && mouseY <= panelY + TITLE_BAR_H - 2;
        ctx.fill(closeX, panelY + 2, panelX + panelW - 2, panelY + TITLE_BAR_H - 2,
                closeHover ? COLOR_BTN_HOVER : COLOR_BTN);
        ctx.drawText(this.textRenderer, "X", closeX + 7, panelY + 7, COLOR_TEXT, false);

        if (showPinBar) {
            int barY = panelY + TITLE_BAR_H;
            ctx.fill(panelX, barY, panelX + panelW, barY + PIN_BAR_H, 0xFF2A2A2A);
            ctx.fill(panelX, barY + PIN_BAR_H - 1, panelX + panelW, barY + PIN_BAR_H, COLOR_BORDER);

            if (urlField != null) {
                urlField.setX(panelX + 4);
                urlField.setY(barY + 3);
                urlField.setWidth(panelW - 50);
            }

            int setW = 36;
            int setBtnX = panelX + panelW - setW - 6;
            int setBtnY = barY + 3;
            boolean setHover = mouseX >= setBtnX && mouseX <= setBtnX + setW
                    && mouseY >= setBtnY && mouseY <= setBtnY + 16;
            ctx.fill(setBtnX, setBtnY, setBtnX + setW, setBtnY + 16,
                    setHover ? COLOR_PIN_HOVER : COLOR_PIN);
            ctx.drawText(this.textRenderer, "Set", setBtnX + 10, setBtnY + 4, COLOR_TEXT, false);
        }

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

        ctx.fill(panelX + panelW - RESIZE_HANDLE, panelY + panelH - RESIZE_HANDLE,
                panelX + panelW, panelY + panelH, COLOR_RESIZE);
        ctx.drawText(this.textRenderer, "//",
                panelX + panelW - RESIZE_HANDLE + 1, panelY + panelH - RESIZE_HANDLE + 2,
                0xFFAAAAAA, false);

        ModConfig cfg = ModConfig.get();
        String hint = cfg.getOverlayKeyName() + " = close | drag title = move | corner = resize";
        ctx.drawText(this.textRenderer, hint, panelX + 6, panelY + panelH - 11, 0xFF666666, false);

        if (!statusMsg.isEmpty() && System.currentTimeMillis() - statusMsgTime < 3000) {
            int smW = this.textRenderer.getWidth(statusMsg) + 12;
            int smX = panelX + panelW / 2 - smW / 2;
            int smY = panelY + panelH / 2 - 10;
            ctx.fill(smX, smY, smX + smW, smY + 16, 0xDD000000);
            ctx.drawText(this.textRenderer, statusMsg,
                    smX + 6, smY + 4, 0xFFFFFF66, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderReferenceImage(DrawContext ctx, int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.refImageVisible) return;

        int rx = cfg.refImageX < 0 ? this.width - cfg.refImageW - 10 : cfg.refImageX;
        int ry = cfg.refImageY;
        int rw = cfg.refImageW;
        int rh = cfg.refImageH;

        ctx.fill(rx - 2, ry - 2, rx + rw + 2, ry + rh + 2, 0xCC000000);
        ctx.fill(rx - 1, ry - 1, rx + rw + 1, ry + rh + 1, 0xCC555555);

        Identifier refTex = ReferenceImageHud.getTextureId();
        if (refTex != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, refTex,
                    rx, ry, 0.0F, 0.0F, rw, rh, rw, rh);
        } else if (ReferenceImageHud.isLoading()) {
            ctx.fill(rx, ry, rx + rw, ry + rh, 0xFF1E1E1E);
            String msg = "Loading...";
            ctx.drawText(this.textRenderer, msg,
                    rx + rw / 2 - this.textRenderer.getWidth(msg) / 2,
                    ry + rh / 2 - 4, COLOR_TEXT, false);
        }

        ctx.drawText(this.textRenderer, "Drag to move",
                rx + 2, ry + rh + 3, 0x88FFFFFF, false);

        int rhs = 10;
        boolean rhHover = mouseX >= rx + rw - rhs && mouseX <= rx + rw
                && mouseY >= ry + rh - rhs && mouseY <= ry + rh;
        ctx.fill(rx + rw - rhs, ry + rh - rhs, rx + rw, ry + rh,
                rhHover ? 0xCCFFFFFF : 0xCC888888);
        ctx.drawText(this.textRenderer, "/", rx + rw - rhs + 2, ry + rh - rhs + 1, 0xFF000000, false);

        int ux = rx + rw - 10;
        int uy = ry;
        boolean uxHover = mouseX >= ux && mouseX <= ux + 10
                && mouseY >= uy && mouseY <= uy + 10;
        ctx.fill(ux, uy, ux + 10, uy + 10, uxHover ? COLOR_BTN_HOVER : COLOR_BTN);
        ctx.drawText(this.textRenderer, "x", ux + 2, uy + 1, COLOR_TEXT, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();

        ModConfig cfgRef = ModConfig.get();
        if (cfgRef.refImageVisible) {
            int rx = cfgRef.refImageX < 0 ? this.width - cfgRef.refImageW - 10 : cfgRef.refImageX;
            int ry = cfgRef.refImageY;
            int rw = cfgRef.refImageW;
            int rh = cfgRef.refImageH;

            if (mx >= rx + rw - 10 && mx <= rx + rw && my >= ry && my <= ry + 10) {
                ReferenceImageHud.clearImage();
                showStatus("Reference image unpinned");
                return true;
            }

            int rhs = 10;
            if (mx >= rx + rw - rhs && mx <= rx + rw && my >= ry + rh - rhs && my <= ry + rh) {
                resizingRef = true;
                refResizeStartMouseX = mx;
                refResizeStartMouseY = my;
                refResizeStartW = rw;
                refResizeStartH = rh;
                return true;
            }

            if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
                draggingRef = true;
                refDragOffX = mx - rx;
                refDragOffY = my - ry;
                return true;
            }
        }

        if (mx >= panelX + panelW - 22 && mx <= panelX + panelW - 2
                && my >= panelY + 2 && my <= panelY + TITLE_BAR_H - 2) {
            saveAndClose();
            return true;
        }

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

        int loginW = this.textRenderer.getWidth("Login") + 8;
        int loginX = homeX + navSize + navGap + 4;
        if (mx >= loginX && mx <= loginX + loginW && my >= navY && my <= navY + navSize) {
            if (browser != null) {
                browser.loadURL("https://www.pinterest.com/");
            }
            return true;
        }

        String pinText = cfgRef.refImageVisible ? "Unpin" : "Pin";
        int pinBtnW = this.textRenderer.getWidth(pinText) + 8;
        int pinX = loginX + loginW + 4;
        if (mx >= pinX && mx <= pinX + pinBtnW && my >= navY && my <= navY + navSize) {
            if (cfgRef.refImageVisible) {
                ReferenceImageHud.clearImage();
                showStatus("Reference image unpinned");
            } else {
                showPinBar = !showPinBar;
                if (urlField != null) {
                    urlField.visible = showPinBar;
                    urlField.active = showPinBar;
                    if (showPinBar) {
                        String clip = getClipboard();
                        if (clip != null && (clip.startsWith("http://") || clip.startsWith("https://"))) {
                            urlField.setText(clip);
                        }
                        urlField.setFocused(true);
                    }
                }
                resizeBrowser();
            }
            return true;
        }

        if (showPinBar) {
            int barY = panelY + TITLE_BAR_H;
            int setW = 36;
            int setBtnX = panelX + panelW - setW - 6;
            int setBtnY = barY + 3;
            if (mx >= setBtnX && mx <= setBtnX + setW && my >= setBtnY && my <= setBtnY + 16) {
                pinImageFromField();
                return true;
            }
        }

        if (mx >= panelX + panelW - RESIZE_HANDLE && mx <= panelX + panelW
                && my >= panelY + panelH - RESIZE_HANDLE && my <= panelY + panelH) {
            resizing = true;
            resizeStartMouseX = mx;
            resizeStartMouseY = my;
            resizeStartW = panelW;
            resizeStartH = panelH;
            return true;
        }

        if (mx >= panelX && mx <= panelX + panelW - 22
                && my >= panelY && my <= panelY + TITLE_BAR_H) {
            dragging = true;
            dragOffX = mx - panelX;
            dragOffY = my - panelY;
            return true;
        }

        if (isInContentArea(mx, my) && browser != null) {
            if (urlField != null) urlField.setFocused(false);
            browser.sendMousePress(browserMouseX(mx), browserMouseY(my), click.button());
            browser.setFocus(true);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        int mx = (int) click.x(), my = (int) click.y();

        if (draggingRef) {
            ModConfig cfg = ModConfig.get();
            cfg.refImageX = Math.max(0, Math.min(mx - refDragOffX, this.width - cfg.refImageW));
            cfg.refImageY = Math.max(0, Math.min(my - refDragOffY, this.height - cfg.refImageH));
            return true;
        }
        if (resizingRef) {
            ModConfig cfg = ModConfig.get();
            cfg.refImageW = Math.max(50, refResizeStartW + (mx - refResizeStartMouseX));
            cfg.refImageH = Math.max(50, refResizeStartH + (my - refResizeStartMouseY));
            return true;
        }

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
        if (draggingRef || resizingRef) {
            draggingRef = false;
            resizingRef = false;
            ModConfig.get().save();
            return true;
        }
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
            if (showPinBar) {
                showPinBar = false;
                if (urlField != null) {
                    urlField.setFocused(false);
                    urlField.visible = false;
                    urlField.active = false;
                }
                resizeBrowser();
                return true;
            }
            saveAndClose();
            return true;
        }

        if (showPinBar && urlField != null && urlField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                pinImageFromField();
                return true;
            }
            return super.keyPressed(keyInput);
        }

        if (browser != null) {
            browser.sendKeyPress(keyCode, keyInput.scancode(), modifiers);
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        if (showPinBar && urlField != null && urlField.isFocused()) {
            return super.keyReleased(keyInput);
        }
        if (browser != null) {
            browser.sendKeyRelease(keyInput.key(), keyInput.scancode(), keyInput.modifiers());
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (showPinBar && urlField != null && urlField.isFocused()) {
            return super.charTyped(charInput);
        }
        if (browser != null && charInput.codepoint() != 0) {
            browser.sendKeyTyped((char) charInput.codepoint(), charInput.modifiers());
        }
        return true;
    }

    private void pinImageFromField() {
        if (urlField == null) return;
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            showStatus("Paste an image URL first!");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showStatus("URL must start with http:// or https://");
            return;
        }
        ReferenceImageHud.setImage(url);
        showPinBar = false;
        urlField.visible = false;
        urlField.active = false;
        urlField.setFocused(false);
        resizeBrowser();
        showStatus("Reference image pinned!");
    }

    private String getClipboard() {
        try {
            if (this.client != null) {
                return GLFW.glfwGetClipboardString(this.client.getWindow().getHandle());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showStatus(String msg) {
        statusMsg = msg;
        statusMsgTime = System.currentTimeMillis();
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

package com.pinterestmod.gui;

import com.pinterestmod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

public class PinterestConfigScreen extends Screen {

    private static final int COLOR_BG        = 0xFF1A1A1A;
    private static final int COLOR_HEADER    = 0xFFE60023;
    private static final int COLOR_PANEL     = 0xFF242424;
    private static final int COLOR_BORDER    = 0xFF444444;
    private static final int COLOR_TEXT      = 0xFFFFFFFF;
    private static final int COLOR_SUBTEXT   = 0xFFAAAAAA;
    private static final int COLOR_LINKED    = 0xFF4CAF50;
    private static final int COLOR_WARN      = 0xFFFF9800;

    private final Screen returnScreen;
    private int tab = 0;

    private boolean capturingOverlay = false;
    private boolean capturingConfig  = false;
    private int pendingOverlayKey;
    private boolean pendingOverlayShift;
    private int pendingConfigKey;
    private boolean pendingConfigShift;

    private TextFieldWidget emailField;
    private String statusMessage = "";
    private boolean statusIsError = false;

    public PinterestConfigScreen(Screen returnScreen) {
        super(Text.literal("AspireMod - Config"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.get();
        pendingOverlayKey   = cfg.overlayKey;
        pendingOverlayShift = cfg.overlayShift;
        pendingConfigKey    = cfg.configKey;
        pendingConfigShift  = cfg.configShift;
        buildWidgets();
    }

    private void buildWidgets() {
        this.clearChildren();
        int cx = this.width / 2;
        int panelX = cx - 180;
        int tabY = 60;

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("Keybinds"), btn -> { tab = 0; buildWidgets(); })
                .dimensions(panelX, tabY, 174, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Account"),  btn -> { tab = 1; buildWidgets(); })
                .dimensions(panelX + 178, tabY, 174, 22).build());

        if (tab == 0) buildKeybindTab(panelX, tabY + 30);
        else          buildAccountTab(panelX, tabY + 30);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), btn -> saveAndClose())
                .dimensions(cx - 100, this.height - 36, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> cancel())
                .dimensions(cx + 28, this.height - 36, 72, 20).build());
    }

    private void buildKeybindTab(int x, int y) {
        String overlayLabel = capturingOverlay ? "[ Press a key... ]"
                : "Change: " + (pendingOverlayShift ? "SHIFT+" : "") + ModConfig.keyName(pendingOverlayKey);
        addDrawableChild(ButtonWidget.builder(Text.literal(overlayLabel),
                btn -> { capturingOverlay = true; capturingConfig = false; buildWidgets(); }
        ).dimensions(x + 190, y + 28, 160, 20).build());

        String configLabel = capturingConfig ? "[ Press a key... ]"
                : "Change: " + (pendingConfigShift ? "SHIFT+" : "") + ModConfig.keyName(pendingConfigKey);
        addDrawableChild(ButtonWidget.builder(Text.literal(configLabel),
                btn -> { capturingConfig = true; capturingOverlay = false; buildWidgets(); }
        ).dimensions(x + 190, y + 68, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults"), btn -> {
            pendingOverlayKey = 86; pendingOverlayShift = true;
            pendingConfigKey  = 66; pendingConfigShift  = true;
            capturingOverlay = false; capturingConfig = false;
            buildWidgets();
        }).dimensions(x + 100, y + 110, 160, 20).build());
    }

    private void buildAccountTab(int x, int y) {
        ModConfig cfg = ModConfig.get();

        emailField = new TextFieldWidget(this.textRenderer, x + 4, y + 58, 348, 20, Text.literal("Email"));
        emailField.setMaxLength(200);
        emailField.setText(cfg.pinterestEmail);
        emailField.setPlaceholder(Text.literal("your@email.com"));
        addDrawableChild(emailField);

        if (cfg.isLinked) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Unlink Account"), btn -> {
                ModConfig c = ModConfig.get();
                c.isLinked = false; c.pinterestEmail = ""; c.pinterestLinked = "";
                c.save();
                statusMessage = "Account unlinked."; statusIsError = false;
                buildWidgets();
            }).dimensions(x + 80, y + 86, 200, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Link Account"), btn -> linkAccount())
                    .dimensions(x + 80, y + 86, 200, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Open Pinterest Login in Browser"), btn -> {
                try { java.awt.Desktop.getDesktop().browse(new URI("https://www.pinterest.com/login/")); }
                catch (Exception e) { statusMessage = "Could not open browser."; statusIsError = true; }
            }).dimensions(x + 30, y + 114, 300, 20).build());
        }
    }

    private void linkAccount() {
        if (emailField == null) return;
        String email = emailField.getText().trim();
        if (email.isEmpty() || !email.contains("@")) {
            statusMessage = "Please enter a valid email address.";
            statusIsError = true;
            return;
        }
        ModConfig cfg = ModConfig.get();
        cfg.pinterestEmail = email; cfg.pinterestLinked = email; cfg.isLinked = true;
        cfg.save();
        statusMessage = "Linked: " + email; statusIsError = false;
        buildWidgets();
    }

    private void saveAndClose() {
        ModConfig cfg = ModConfig.get();
        cfg.overlayKey   = pendingOverlayKey;   cfg.overlayShift = pendingOverlayShift;
        cfg.configKey    = pendingConfigKey;    cfg.configShift  = pendingConfigShift;
        cfg.save();
        MinecraftClient.getInstance().setScreen(returnScreen);
    }

    private void cancel() {
        MinecraftClient.getInstance().setScreen(returnScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturingOverlay) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                pendingOverlayKey   = keyCode;
                pendingOverlayShift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            }
            capturingOverlay = false;
            buildWidgets();
            return true;
        }
        if (capturingConfig) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                pendingConfigKey   = keyCode;
                pendingConfigShift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            }
            capturingConfig = false;
            buildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int panelX = cx - 180;
        int tabY = 60;

        // Header
        ctx.fill(0, 0, this.width, 52, COLOR_HEADER);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("AspireMod  -  Settings"), cx, 10, COLOR_TEXT);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Configure keybinds and your Pinterest account"), cx, 26, 0xFFFFCCCC);

        // Active tab highlight
        if (tab == 0) ctx.fill(panelX,       tabY, panelX + 174, tabY + 22, COLOR_HEADER);
        else          ctx.fill(panelX + 178, tabY, panelX + 352, tabY + 22, COLOR_HEADER);

        // Panel body
        ctx.fill(panelX, tabY + 22, panelX + 356, this.height - 46, COLOR_PANEL);
        ctx.fill(panelX, tabY + 22, panelX + 356, tabY + 23, COLOR_BORDER);

        if (tab == 0) renderKeybindTab(ctx, panelX, tabY + 30);
        else          renderAccountTab(ctx, panelX, tabY + 30);

        if (!statusMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(statusMessage), cx, this.height - 52, statusIsError ? 0xFFFF4444 : COLOR_LINKED);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderKeybindTab(DrawContext ctx, int x, int y) {
        int cx = this.width / 2;
        ctx.drawText(this.textRenderer, "Open Pinterest Overlay:", x + 8, y + 8,  COLOR_TEXT,    false);
        ctx.drawText(this.textRenderer, "Current: " + (pendingOverlayShift ? "SHIFT+" : "") + ModConfig.keyName(pendingOverlayKey),
                x + 8, y + 20, COLOR_SUBTEXT, false);
        ctx.drawText(this.textRenderer, "Open Config Screen:",    x + 8, y + 48, COLOR_TEXT,    false);
        ctx.drawText(this.textRenderer, "Current: " + (pendingConfigShift ? "SHIFT+" : "") + ModConfig.keyName(pendingConfigKey),
                x + 8, y + 60, COLOR_SUBTEXT, false);
        ctx.drawText(this.textRenderer, "Click 'Change' then press any key to rebind.",
                x + 8, y + 96, 0xFF888888, false);
        if (capturingOverlay || capturingConfig) {
            ctx.fill(x, y + 84, x + 356, y + 98, 0xFF2A1A00);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Waiting for key press... (ESC to cancel)"), cx, y + 87, COLOR_WARN);
        }
    }

    private void renderAccountTab(DrawContext ctx, int x, int y) {
        ModConfig cfg = ModConfig.get();
        int cx = this.width / 2;
        if (cfg.isLinked) {
            ctx.fill(x + 4, y + 4, x + 352, y + 44, 0xFF1A3A1A);
            ctx.drawText(this.textRenderer, "Account Linked",    x + 12, y + 10, COLOR_LINKED, false);
            ctx.drawText(this.textRenderer, cfg.pinterestEmail,  x + 12, y + 24, COLOR_TEXT,   false);
        } else {
            ctx.fill(x + 4, y + 4, x + 352, y + 44, 0xFF3A2A00);
            ctx.drawText(this.textRenderer, "No Account Linked - Viewing as Guest", x + 12, y + 10, COLOR_WARN, false);
            ctx.drawText(this.textRenderer, "Enter your Pinterest email below:",    x + 12, y + 24, COLOR_SUBTEXT, false);
        }
        ctx.drawText(this.textRenderer, "Email:", x + 8, y + 48, COLOR_TEXT, false);
        ctx.drawText(this.textRenderer, "Your email is saved locally only.", x + 8, y + 112, 0xFF666666, false);
    }
}

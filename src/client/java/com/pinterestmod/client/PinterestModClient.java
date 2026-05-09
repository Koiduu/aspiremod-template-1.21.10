package com.pinterestmod.client;

import com.pinterestmod.config.ModConfig;
import com.pinterestmod.gui.PinterestConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.reflect.Method;

public class PinterestModClient implements ClientModInitializer {

    private boolean overlayKeyWasDown = false;
    private boolean configKeyWasDown  = false;
    private boolean mcefAvailable     = false;
    private boolean mcefInitialized   = false;

    private static Process externalBrowserProcess = null;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        try {
            Class.forName("com.cinemamod.mcef.MCEF");
            mcefAvailable = true;
        } catch (ClassNotFoundException e) {
            mcefAvailable = false;
        }

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

            // Check MCEF initialization once it becomes available
            if (mcefAvailable && !mcefInitialized) {
                try {
                    Class<?> mcefClass = Class.forName("com.cinemamod.mcef.MCEF");
                    Method isInit = mcefClass.getMethod("isInitialized");
                    mcefInitialized = (boolean) isInit.invoke(null);
                } catch (Exception ignored) {}
            }

            boolean overlayKeyDown = GLFW.glfwGetKey(window, cfg.overlayKey) == GLFW.GLFW_PRESS;
            boolean overlayTrigger = overlayKeyDown && (!cfg.overlayShift || shiftDown);
            if (overlayTrigger && !overlayKeyWasDown) {
                if (mcefAvailable && mcefInitialized) {
                    client.execute(() -> client.setScreen(createMcefScreen()));
                } else {
                    launchExternalBrowser();
                }
            }
            overlayKeyWasDown = overlayTrigger;

            boolean configKeyDown = GLFW.glfwGetKey(window, cfg.configKey) == GLFW.GLFW_PRESS;
            boolean configTrigger = configKeyDown && (!cfg.configShift || shiftDown);
            if (configTrigger && !configKeyWasDown) {
                client.execute(() -> client.setScreen(new PinterestConfigScreen(null)));
            }
            configKeyWasDown = configTrigger;
        });

        String mode = mcefAvailable ? "embedded browser (MCEF detected)" : "external browser";
        System.out.println("[AspireMod] Loaded! Shift+V = Pinterest overlay (" + mode + "), Shift+B = config.");
    }

    private Screen createMcefScreen() {
        try {
            return (Screen) Class.forName("com.pinterestmod.gui.PinterestBrowserScreen")
                    .getConstructor(Screen.class)
                    .newInstance((Screen) null);
        } catch (Exception e) {
            System.err.println("[AspireMod] Failed to create browser screen, launching external browser: " + e.getMessage());
            launchExternalBrowser();
            return null;
        }
    }

    private void launchExternalBrowser() {
        if (externalBrowserProcess != null && externalBrowserProcess.isAlive()) {
            System.out.println("[AspireMod] Pinterest browser is already open.");
            return;
        }

        ModConfig cfg = ModConfig.get();
        String url = cfg.isLinked ? "https://www.pinterest.com/" : "https://www.pinterest.com/ideas/";
        String os = System.getProperty("os.name", "").toLowerCase();

        try {
            if (os.contains("win")) {
                launchWindowsBrowser(url);
            } else if (os.contains("mac")) {
                launchMacBrowser(url);
            } else {
                launchLinuxBrowser(url);
            }
            System.out.println("[AspireMod] Pinterest opened in external browser.");
        } catch (Exception e) {
            System.err.println("[AspireMod] Failed to launch browser: " + e.getMessage());
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
                System.err.println("[AspireMod] Desktop.browse also failed: " + ex.getMessage());
            }
        }
    }

    private void launchWindowsBrowser(String url) throws Exception {
        String browserPath = findWindowsBrowser(
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
        );
        if (browserPath == null) {
            browserPath = findWindowsBrowser(
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
            );
        }

        if (browserPath != null) {
            externalBrowserProcess = new ProcessBuilder(
                browserPath, "--app=" + url, "--window-size=520,750", "--new-window"
            ).start();
            setWindowAlwaysOnTop();
        } else {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
        }
    }

    private void launchMacBrowser(String url) throws Exception {
        String chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
        if (new File(chromePath).exists()) {
            externalBrowserProcess = new ProcessBuilder(
                chromePath, "--app=" + url, "--window-size=520,750", "--new-window"
            ).start();
        } else {
            Runtime.getRuntime().exec(new String[]{"open", url});
        }
    }

    private void launchLinuxBrowser(String url) throws Exception {
        String[] browsers = {"google-chrome", "chromium-browser", "chromium", "firefox"};
        for (String browser : browsers) {
            try {
                if (browser.contains("chrome") || browser.contains("chromium")) {
                    externalBrowserProcess = new ProcessBuilder(
                        browser, "--app=" + url, "--window-size=520,750", "--new-window"
                    ).start();
                } else {
                    externalBrowserProcess = new ProcessBuilder(browser, url).start();
                }
                return;
            } catch (Exception ignored) {}
        }
        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
    }

    private String findWindowsBrowser(String... paths) {
        for (String path : paths) {
            if (path != null && new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    private void setWindowAlwaysOnTop() {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                String psCommand = String.join("; ",
                    "Add-Type -TypeDefinition '"
                        + "using System; using System.Runtime.InteropServices; "
                        + "public class WinAPI { "
                        + "[DllImport(\\\"user32.dll\\\")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags); "
                        + "[DllImport(\\\"user32.dll\\\")] public static extern IntPtr GetForegroundWindow(); "
                        + "}'",
                    "$hwnd = [WinAPI]::GetForegroundWindow()",
                    "[WinAPI]::SetWindowPos($hwnd, [IntPtr]::new(-1), 0, 0, 0, 0, 0x0043)"
                );
                new ProcessBuilder("powershell", "-Command", psCommand)
                    .redirectErrorStream(true)
                    .start();
            } catch (Exception e) {
                System.err.println("[AspireMod] Could not set always-on-top: " + e.getMessage());
            }
        }, "AspireMod-AlwaysOnTop").start();
    }
}

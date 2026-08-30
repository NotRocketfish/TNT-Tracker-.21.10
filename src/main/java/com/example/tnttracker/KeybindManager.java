package com.example.tnttracker;

import com.example.tnttracker.config.ModConfig;
import com.example.tnttracker.gui.SettingsOverlay;
import com.example.tnttracker.gui.ReplayPanel;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class KeybindManager {
    // Existing bound keys
    public static KeyBinding openConfigKey;          // R - Open Settings/Replay Panel

    // New unbound keys (default: unassigned)
    public static KeyBinding toggleKey;              // TNT Tracker (showTntPositions)
    public static KeyBinding clearTrailsKey;         // Clear Trails
    public static KeyBinding toggleDotsKey;          // Trail Mode (dots/lines)
    public static KeyBinding toggleSafeKey;          // Safe Mode
    public static KeyBinding replayKey;              // Toggle Replay
    public static KeyBinding toggleShowTrailsKey;    // Toggle showTrails
    public static KeyBinding toggleRespawnTntKey;    // Toggle respawnTnt
    public static KeyBinding toggleShotSummaryKey;   // Toggle enableShotSummary
    public static KeyBinding toggleReplayLoopKey;    // Toggle replayLoop
    public static KeyBinding exportCsvKey;           // Export CSV

    public static void register() {
        // Main Menu (bound)
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.open_config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.tnttracker"));

        // New (unbound)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        clearTrailsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.clear_trails", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleDotsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_dots", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleSafeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_safe", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        replayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.replay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleShowTrailsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_show_trails", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleRespawnTntKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_respawn_tnt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleShotSummaryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_shot_summary", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        toggleReplayLoopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.toggle_replay_loop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
        exportCsvKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tnttracker.export_csv", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.tnttracker"));
    }

    public static void handlePresses(MinecraftClient client) {
        // Reusable colored states.
        Text stateOn  = Text.translatable("tnttracker.state.on").formatted(Formatting.GREEN);
        Text stateOff = Text.translatable("tnttracker.state.off").formatted(Formatting.RED);

        // Safe Mode
        while (toggleSafeKey.wasPressed()) {
            ModConfig.safeMode = !ModConfig.safeMode;
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.safe_mode",
                                ModConfig.safeMode ? stateOn : stateOff), true);
        }
        // TNT Tracker
        while (toggleKey.wasPressed()) {
            ModConfig.showTntPositions = !ModConfig.showTntPositions;
            if (client.player != null) {
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.tnt_tracker",
                                ModConfig.showTntPositions ? stateOn : stateOff), true);
            }
            if (!ModConfig.showTntPositions) {
                TntTracker.finalizeAllRecordings();
            }
        }
        // Trail Mode (Dots/Lines)
        while (toggleDotsKey.wasPressed()) {
            ModConfig.renderTrailAsDots = !ModConfig.renderTrailAsDots;
            if (client.player != null) {
                Text modeText = ModConfig.renderTrailAsDots
                        ? Text.translatable("tnttracker.state.dots").formatted(Formatting.YELLOW)
                        : Text.translatable("tnttracker.state.lines").formatted(Formatting.AQUA);
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.trail_mode", modeText), true);
            }
        }
        // Clear Trails
        while (clearTrailsKey.wasPressed()) {
            TntTracker.clearTrails();
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.trails_cleared"), true);
        }
        // Open Settings
        while (openConfigKey.wasPressed()) {
            if (client.currentScreen instanceof SettingsOverlay ||
                    client.currentScreen instanceof ReplayPanel) {
                client.currentScreen.close();
            } else {
                client.setScreen(new SettingsOverlay());
            }
        }
        // Replay
        while (replayKey.wasPressed()) {
            ReplayEngine.toggleReplay(client);
        }

        // Extra unbound toggles.
        while (toggleShowTrailsKey.wasPressed()) {
            ModConfig.showTrails = !ModConfig.showTrails;
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.show_trails",
                                ModConfig.showTrails ? stateOn : stateOff), true);
        }
        while (toggleRespawnTntKey.wasPressed()) {
            ModConfig.respawnTnt = !ModConfig.respawnTnt;
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.respawn_tnt",
                                ModConfig.respawnTnt ? stateOn : stateOff), true);
        }
        while (toggleShotSummaryKey.wasPressed()) {
            ModConfig.enableShotSummary = !ModConfig.enableShotSummary;
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.shot_summary_toggle",
                                ModConfig.enableShotSummary ? stateOn : stateOff), true);
        }
        while (toggleReplayLoopKey.wasPressed()) {
            ModConfig.replayLoop = !ModConfig.replayLoop;
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.replay_loop",
                                ModConfig.replayLoop ? stateOn : stateOff), true);
        }
        while (exportCsvKey.wasPressed()) {
            TntTrackerMod.exportTrailsToFile();
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.message.export_csv")
                                .formatted(Formatting.GREEN), true);
        }
    }
}

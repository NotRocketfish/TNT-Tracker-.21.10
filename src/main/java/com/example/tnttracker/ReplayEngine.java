package com.example.tnttracker;

import com.example.tnttracker.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ReplayEngine {
    public static int replayTick = 0;
    public static long replayStartTime = 0;
    public static int maxReplayTicks = 0;

    public static void startReplay() {
        var trails = TntTracker.savedTrails;
        int start = Math.max(0, ModConfig.replayTrailStart);
        int end = Math.min(trails.size() - 1, ModConfig.replayTrailEnd);
        if (start > end) return;

        maxReplayTicks = 0;
        for (int i = start; i <= end; i++) {
            if (trails.get(i).size() > maxReplayTicks) maxReplayTicks = trails.get(i).size();
        }
        if (maxReplayTicks == 0) return;

        ModConfig.replayActive = true;
        ModConfig.replayPaused = false;
        replayTick = 0;
        replayStartTime = System.currentTimeMillis();
    }

    public static void stopReplay() {
        ModConfig.replayActive = false;
        ModConfig.replayPaused = false;
    }

    public static void toggleReplay(MinecraftClient client) {
        if (ModConfig.replayActive) {
            stopReplay();
            if (client.player != null)
                client.player.sendMessage(
                        Text.translatable("tnttracker.replay.message.stopped"), true);
        } else if (!TntTracker.savedTrails.isEmpty()) {
            int start = Math.max(0, ModConfig.replayTrailStart);
            int end = Math.min(TntTracker.savedTrails.size() - 1, ModConfig.replayTrailEnd);
            if (start > end) {
                if (client.player != null)
                    client.player.sendMessage(
                            Text.translatable("tnttracker.replay.message.invalid_range"), true);
            } else {
                startReplay();
                if (client.player != null)
                    client.player.sendMessage(
                            Text.translatable("tnttracker.replay.message.playing_range", start, end), true);
            }
        }
    }

    public static void advanceTick() {
        if (!ModConfig.replayActive || ModConfig.replayPaused) return;
        long elapsed = System.currentTimeMillis() - replayStartTime;
        int targetTick = (int)(elapsed * ModConfig.replaySpeed / 50.0);
        if (targetTick >= maxReplayTicks) {
            if (ModConfig.replayLoop) {
                replayStartTime = System.currentTimeMillis();
                replayTick = 0;
            } else {
                replayTick = maxReplayTicks - 1;
                ModConfig.replayPaused = true;
            }
        } else {
            replayTick = targetTick;
        }
    }
}
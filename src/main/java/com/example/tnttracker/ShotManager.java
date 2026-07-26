package com.example.tnttracker;

import com.example.tnttracker.config.ModConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ShotManager {
    private static int currentShotTotal = 0;
    private static int ticksSinceLastExplosion = -1;
    private static final int COOLDOWN_TICKS = 40;   // 2 seconds

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ShotManager::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (ticksSinceLastExplosion < 0) return;

        ticksSinceLastExplosion++;

        if (ticksSinceLastExplosion >= COOLDOWN_TICKS) {
            int finalCount = currentShotTotal;
            server.getPlayerManager().broadcast(
                    Text.translatable("tnttracker.message.shot_broadcast", finalCount)
                            .formatted(Formatting.GOLD),
                    false
            );

            // Reset
            currentShotTotal = 0;
            ticksSinceLastExplosion = -1;
        }
    }

    public static void addDestroyedBlocks(int count) {
        if (!ModConfig.enableShotSummary) {
            currentShotTotal = 0;
            ticksSinceLastExplosion = -1;
            return;
        }
        if (count <= 0) return;

        currentShotTotal += count;
        ticksSinceLastExplosion = 0;
    }

    // --- HUD helpers ---
    public static boolean isShotActive() {
        return ticksSinceLastExplosion >= 0;
    }

    public static int getCurrentTotal() {
        return currentShotTotal;
    }
}
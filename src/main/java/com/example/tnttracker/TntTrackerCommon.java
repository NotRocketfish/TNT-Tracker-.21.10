package com.example.tnttracker;

import com.example.tnttracker.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.util.Identifier;

public class TntTrackerCommon implements ModInitializer {
    public static final String MOD_ID = "tnttracker";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModItems.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE)
                .register(content -> content.add(ModItems.REMOTE_TRIGGER));

        ServerTickEvents.END_WORLD_TICK.register(TntRespawnManager::tick);
        ServerTaskScheduler.register();
        ShotManager.register();
    }
}

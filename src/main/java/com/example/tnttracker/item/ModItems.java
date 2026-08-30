package com.example.tnttracker.item;

import com.example.tnttracker.TntTrackerCommon;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModItems {
    public static final Item REMOTE_TRIGGER = new RemoteTriggerItem(
            new FabricItemSettings().maxCount(1)
    );

    public static void register() {
        Registry.register(Registries.ITEM, TntTrackerCommon.id("remote_trigger"), REMOTE_TRIGGER);
    }
}

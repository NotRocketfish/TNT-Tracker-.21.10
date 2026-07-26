package com.example.tnttracker.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item REMOTE_TRIGGER = new RemoteTriggerItem(
            new FabricItemSettings().maxCount(1)
    );

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("tnttracker", "remote_trigger"), REMOTE_TRIGGER);
    }
}
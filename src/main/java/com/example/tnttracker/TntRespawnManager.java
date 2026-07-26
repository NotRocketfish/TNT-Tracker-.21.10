package com.example.tnttracker;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class TntRespawnManager {
    public static final Map<BlockPos, Long> scheduledRespawns = new HashMap<>();

    public static void schedule(BlockPos pos, World world, int cooldownTicks) {
        scheduledRespawns.put(pos.toImmutable(), world.getTime() + cooldownTicks);
    }

    public static void tick(World world) {
        var it = scheduledRespawns.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (world.getTime() >= entry.getValue()) {
                world.setBlockState(entry.getKey(), Blocks.TNT.getDefaultState(), 3);
                it.remove();
            }
        }
    }
}
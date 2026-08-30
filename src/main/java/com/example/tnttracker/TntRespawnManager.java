package com.example.tnttracker;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TntRespawnManager {
    private static final Map<RegistryKey<World>, Map<BlockPos, Long>> scheduledRespawns = new HashMap<>();

    public static void schedule(BlockPos pos, World world, int cooldownTicks) {
        scheduledRespawns
                .computeIfAbsent(world.getRegistryKey(), ignored -> new HashMap<>())
                .put(pos.toImmutable(), world.getTime() + Math.max(0, cooldownTicks));
    }

    public static void tick(World world) {
        Map<BlockPos, Long> worldRespawns = scheduledRespawns.get(world.getRegistryKey());
        if (worldRespawns == null || worldRespawns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<BlockPos, Long>> iterator = worldRespawns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (world.getTime() < entry.getValue() || !isChunkLoaded(world, entry.getKey())) {
                continue;
            }
            world.setBlockState(entry.getKey(), Blocks.TNT.getDefaultState(), 3);
            iterator.remove();
        }

        if (worldRespawns.isEmpty()) {
            scheduledRespawns.remove(world.getRegistryKey());
        }
    }

    private static boolean isChunkLoaded(World world, BlockPos pos) {
        return world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }
}

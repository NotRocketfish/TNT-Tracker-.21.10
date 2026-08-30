package com.example.tnttracker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ServerTaskScheduler {
    private static final List<DelayedTask> tasks = new ArrayList<>();

    public static void schedule(World world, int delayTicks, Runnable task) {
        long executeAtTick = world.getTime() + Math.max(0, delayTicks);
        tasks.add(new DelayedTask(world.getRegistryKey(), executeAtTick, task));
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            List<Runnable> dueTasks = new ArrayList<>();
            Iterator<DelayedTask> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                DelayedTask delayedTask = iterator.next();
                if (!delayedTask.worldKey.equals(world.getRegistryKey())) {
                    continue;
                }
                if (world.getTime() >= delayedTask.executeAtTick) {
                    dueTasks.add(delayedTask.task);
                    iterator.remove();
                }
            }
            dueTasks.forEach(Runnable::run);
        });
    }

    private record DelayedTask(RegistryKey<World> worldKey, long executeAtTick, Runnable task) {
    }
}

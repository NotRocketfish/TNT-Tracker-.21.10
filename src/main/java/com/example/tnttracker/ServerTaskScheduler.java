package com.example.tnttracker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerTaskScheduler {
    private static final Map<String, DelayedTask> tasks = new ConcurrentHashMap<>();

    public static void schedule(World world, int delayTicks, Runnable task) {
        String key = world.getRegistryKey().getValue().toString() + ":" + System.nanoTime();
        tasks.put(key, new DelayedTask(delayTicks, task));
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> tasks.entrySet().removeIf(entry -> {
            DelayedTask dt = entry.getValue();
            dt.remaining.decrementAndGet();
            if (dt.remaining.get() <= 0) {
                dt.task.run();
                return true;
            }
            return false;
        }));
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static class DelayedTask {
        final AtomicInteger remaining;
        final Runnable task;

        DelayedTask(int ticks, Runnable task) {
            this.remaining = new AtomicInteger(ticks);
            this.task = task;
        }
    }
}
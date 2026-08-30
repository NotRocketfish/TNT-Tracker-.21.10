package com.example.tnttracker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TntTracker {
    private static final int MAX_SAVED_TRAILS = 256;

    public static final Map<Integer, List<TntTrackerMod.TimedPosition>> activeRecordings = new LinkedHashMap<>();
    private static final Map<Integer, TntEntity> activeEntities = new LinkedHashMap<>();
    public static final List<List<TntTrackerMod.TimedPosition>> savedTrails = new ArrayList<>();
    private static int trailsVersion = 0;

    public static void clearTrails() {
        savedTrails.clear();
        trailsVersion++;
    }

    public static int getTrailsVersion() {
        return trailsVersion;
    }

    public static Collection<TntEntity> getActiveTntEntities() {
        return activeEntities.values();
    }

    public static void tick(Iterable<Entity> entities, long worldTime) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : entities) {
            if (entity instanceof TntEntity tnt && !tnt.isRemoved()) {
                int id = tnt.getId();
                seen.add(id);
                activeEntities.put(id, tnt);
                activeRecordings
                        .computeIfAbsent(id, ignored -> new ArrayList<>())
                        .add(new TntTrackerMod.TimedPosition(tnt.getPos(), worldTime));
            }
        }

        var it = activeRecordings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<TntTrackerMod.TimedPosition>> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                saveTrail(entry.getValue());
                activeEntities.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public static void finalizeAllRecordings() {
        for (List<TntTrackerMod.TimedPosition> path : activeRecordings.values()) {
            saveTrail(path);
        }
        activeRecordings.clear();
        activeEntities.clear();
    }

    private static void saveTrail(List<TntTrackerMod.TimedPosition> path) {
        if (path.size() <= 1) return;

        savedTrails.add(new ArrayList<>(path));
        while (savedTrails.size() > MAX_SAVED_TRAILS) {
            savedTrails.remove(0);
        }
        trailsVersion++;
    }
}

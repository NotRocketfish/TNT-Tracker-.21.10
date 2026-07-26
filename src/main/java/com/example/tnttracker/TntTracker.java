package com.example.tnttracker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;

import java.util.*;

public class TntTracker {
    public static final Map<TntEntity, List<TntTrackerMod.TimedPosition>> activeRecordings =
            Collections.synchronizedMap(new HashMap<>());
    public static final List<List<TntTrackerMod.TimedPosition>> savedTrails = new ArrayList<>();

    public static void clearTrails() {
        savedTrails.clear();
    }

    public static void tick(Iterable<Entity> entities) {
        // Neue TNT-Entitäten erfassen (Trail starten)
        for (Entity entity : entities) {
            if (entity instanceof TntEntity tnt && !tnt.isRemoved()) {
                activeRecordings.computeIfAbsent(tnt, k -> Collections.synchronizedList(new ArrayList<>()));
            }
        }

        // Entfernte TNT-Entitäten erkennen und deren Pfad speichern
        synchronized (activeRecordings) {
            activeRecordings.entrySet().removeIf(entry -> {
                if (entry.getKey().isRemoved()) {
                    List<TntTrackerMod.TimedPosition> path = new ArrayList<>(entry.getValue());
                    if (path.size() > 1) savedTrails.add(path);
                    return true;
                }
                return false;
            });
        }
    }

    public static void finalizeAllRecordings() {
        synchronized (activeRecordings) {
            for (List<TntTrackerMod.TimedPosition> path : activeRecordings.values()) {
                if (path.size() > 1) savedTrails.add(new ArrayList<>(path));
            }
            activeRecordings.clear();
        }
    }
}
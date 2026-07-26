package com.example.tnttracker.config;

public class ModConfig {
    // --- General toggles ---
    public static boolean showTntPositions = false;
    public static boolean showTrails = true;
    public static boolean renderTrailAsDots = true;
    public static boolean safeMode = false;
    public static boolean enableShotSummary = false;

    // --- TNT respawning ---
    public static boolean respawnTnt = false;
    public static int respawnTntCooldownTicks = 40;

    // --- Values ---
    public static double groupingDistance = 0.5;
    public static double explosionXSize = 0.2;
    public static int salveGroupingTicks = 40;

    // --- Draw order ---
    public enum DrawOrder {
        YZX, XYZ, ZYX, YXZ, ZXY, XZY
    }
    public static DrawOrder drawOrder = DrawOrder.YZX;

    // --- Replay ---
    public static int replayTrailStart = 0;
    public static int replayTrailEnd = 0;
    public static boolean replayActive = false;
    public static boolean replayPaused = false;
    public static double replaySpeed = 1.0;
    public static boolean replayLoop = false;
}
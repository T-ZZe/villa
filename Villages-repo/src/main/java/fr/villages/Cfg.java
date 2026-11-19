package fr.villages;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;

/** Réglages lus dans config.yml (champs statiques, lus par les générateurs). */
final class Cfg {
    private Cfg() {}

    static volatile Set<String> worlds = new HashSet<>();
    static volatile boolean existing = false;

    static volatile boolean villages = true;
    static volatile boolean lamps = true;
    static volatile double lampDensity = 0.05;
    static volatile boolean doorLanterns = true;
    static volatile boolean yards = true;
    static volatile double yardDensity = 1.0;
    static volatile boolean pathWear = true;
    static volatile boolean foundations = true;
    static volatile boolean chimneys = true;
    static volatile double chimneyChance = 0.5;
    static volatile boolean vines = true;
    static volatile double vineChance = 0.35;

    static volatile boolean pyramid = true;
    static volatile boolean witchHut = true;
    static volatile boolean igloo = true;
    static volatile boolean jungleTemple = true;

    static volatile boolean tower = true;
    static volatile double towerChance = 0.30;
    static volatile boolean towerChest = true;

    static volatile boolean house = true;
    static volatile double houseChance = 0.35;
    static volatile boolean houseChest = true;

    static void load(FileConfiguration c) {
        worlds = new HashSet<>(c.getStringList("worlds"));
        existing = c.getBoolean("existing-chunks", false);
        villages = c.getBoolean("villages.enabled", true);
        lamps = c.getBoolean("villages.lamps", true);
        lampDensity = c.getDouble("villages.lamp-density", 0.05);
        doorLanterns = c.getBoolean("villages.door-lanterns", true);
        yards = c.getBoolean("villages.yards", true);
        yardDensity = c.getDouble("villages.yard-density", 1.0);
        pathWear = c.getBoolean("villages.path-wear", true);
        foundations = c.getBoolean("villages.foundations", true);
        chimneys = c.getBoolean("villages.chimneys", true);
        chimneyChance = c.getDouble("villages.chimney-chance", 0.5);
        vines = c.getBoolean("villages.vines", true);
        vineChance = c.getDouble("villages.vine-chance", 0.35);
        pyramid = c.getBoolean("pyramid.enabled", true);
        witchHut = c.getBoolean("witch-hut.enabled", true);
        igloo = c.getBoolean("igloo.enabled", true);
        jungleTemple = c.getBoolean("jungle-temple.enabled", true);
        tower = c.getBoolean("wooden-tower.enabled", true);
        towerChance = c.getDouble("wooden-tower.chance", 0.30);
        towerChest = c.getBoolean("wooden-tower.chest", true);
        house = c.getBoolean("mushroom-house.enabled", true);
        houseChance = c.getDouble("mushroom-house.chance", 0.35);
        houseChest = c.getBoolean("mushroom-house.chest", true);
    }
}

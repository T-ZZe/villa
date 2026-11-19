package fr.villages;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/** Petits tests sur les matériaux. */
final class Blocks {
    private Blocks() {}

    private static final Set<Material> GROUND = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD, Material.MOSS_BLOCK, Material.MYCELIUM);

    private static final Material PALE_MOSS = Material.getMaterial("PALE_MOSS_BLOCK");

    static boolean ground(Material m) {
        return GROUND.contains(m) || (PALE_MOSS != null && m == PALE_MOSS);
    }

    static boolean leaf(Material m) {
        return m.name().endsWith("_LEAVES");
    }

    /** Tronc naturel (les troncs écorcés sont ignorés). */
    static boolean log(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") && !n.startsWith("STRIPPED_");
    }

    /** Tout bois (troncs, écorcés ou non, et bois "wood") : ce que le jeu compte pour garder les feuilles. */
    static boolean anyLog(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.endsWith("_WOOD");
    }

    static boolean liquid(Material m) {
        return m == Material.WATER || m == Material.LAVA;
    }

    /** Bloc sur lequel une liane, une mousse pendante... peut s'accrocher. */
    static boolean support(Material m) {
        if (m == null || m.isAir() || liquid(m)) return false;
        if (leaf(m)) return true;
        return !replaceable(m);
    }

    /** Blocs qu'un arbre ou un rocher a le droit de remplacer. */
    static boolean replaceable(Material m) {
        if (m.isAir()) return true;
        String n = m.name();
        if (n.endsWith("_LEAVES") || n.endsWith("_SAPLING")) return true;
        return n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("FERN") || n.equals("LARGE_FERN")
                || n.equals("DEAD_BUSH") || n.equals("SNOW") || n.equals("VINE") || n.equals("SWEET_BERRY_BUSH")
                || n.equals("LEAF_LITTER") || n.equals("BUSH") || n.equals("FIREFLY_BUSH")
                || n.endsWith("_MUSHROOM") || n.equals("DANDELION") || n.equals("POPPY") || n.equals("BLUE_ORCHID")
                || n.equals("ALLIUM") || n.equals("AZURE_BLUET") || n.endsWith("_TULIP") || n.equals("OXEYE_DAISY")
                || n.equals("CORNFLOWER") || n.equals("LILY_OF_THE_VALLEY") || n.equals("LILAC") || n.equals("ROSE_BUSH")
                || n.equals("PEONY") || n.equals("SUNFLOWER") || n.equals("WILDFLOWERS") || n.equals("PINK_PETALS")
                || n.equals("PALE_HANGING_MOSS") || n.equals("PALE_MOSS_CARPET") || n.equals("MOSS_CARPET")
                || n.equals("CRIMSON_ROOTS") || n.equals("WARPED_ROOTS") || n.equals("NETHER_SPROUTS")
                || n.equals("CRIMSON_FUNGUS") || n.equals("WARPED_FUNGUS") || n.equals("WEEPING_VINES")
                || n.equals("WEEPING_VINES_PLANT") || n.equals("TWISTING_VINES") || n.equals("TWISTING_VINES_PLANT")
                || n.equals("CHORUS_PLANT") || n.equals("CHORUS_FLOWER") || n.equals("RED_SHRUB")
                || n.equals("SHELF_MUSHROOM") || n.equals("COBWEB");
    }
}

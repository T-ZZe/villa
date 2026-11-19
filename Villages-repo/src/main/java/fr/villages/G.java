package fr.villages;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Petits outils partagés par tous les générateurs. */
final class G {
    private G() {}

    static final int NONE = Integer.MIN_VALUE;
    static final BlockFace[] H = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    static final int[][] D4 = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // même ordre que H

    static double hash(long seed, int x, int y, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 27) * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Matériau par son nom (pour les blocs récents), avec un repli. */
    static Material mat(String name, Material fallback) {
        Material m = Material.getMaterial(name);
        return m != null ? m : fallback;
    }

    static int lo(double v) {
        return (int) Math.floor(v);
    }

    static int hi(double v) {
        return (int) Math.ceil(v) - 1;
    }

    static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // ------------------------------------------------------------------ sol

    private static final Set<Material> LAND = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL,
            Material.COARSE_DIRT, Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.SNOW_BLOCK,
            Material.MYCELIUM, Material.DIRT_PATH, Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA,
            Material.MUD, Material.MOSS_BLOCK, Material.STONE, Material.COBBLESTONE, Material.ICE,
            Material.PACKED_ICE);

    /** Sol naturel (ou chemin) sur lequel on peut poser un décor. */
    static boolean land(Material m) {
        return m != null && LAND.contains(m);
    }

    static boolean solid(Material m) {
        return m != null && !m.isAir() && !Blocks.replaceable(m) && !Blocks.liquid(m) && !Blocks.leaf(m);
    }

    /** Y du premier bloc « plein » en descendant (on traverse air, feuilles et plantes, pas les troncs). */
    static int surface(Canvas cv, int x, int z) {
        int y = cv.topY(x, z);
        while (y > cv.minY()) {
            Material m = cv.type(x, y, z);
            if (m == null) return NONE;
            if (m.isAir() || Blocks.leaf(m) || Blocks.replaceable(m)) {
                y--;
                continue;
            }
            return y;
        }
        return NONE;
    }

    /** Pareil, mais on traverse aussi les troncs (pour trouver le sol sous les arbres). */
    static int ground(Canvas cv, int x, int z) {
        return ground(cv, x, z, false);
    }

    /** Idem ; avec shrooms=true on traverse aussi les champignons géants. */
    static int ground(Canvas cv, int x, int z, boolean shrooms) {
        int y = cv.topY(x, z);
        while (y > cv.minY()) {
            Material m = cv.type(x, y, z);
            if (m == null) return NONE;
            boolean shroom = shrooms && (m == Material.RED_MUSHROOM_BLOCK || m == Material.BROWN_MUSHROOM_BLOCK
                    || m == Material.MUSHROOM_STEM);
            if (m.isAir() || Blocks.leaf(m) || Blocks.anyLog(m) || Blocks.replaceable(m) || shroom) {
                y--;
                continue;
            }
            return y;
        }
        return NONE;
    }

    // ------------------------------------------------------------------ pose

    static boolean free(Canvas cv, int x, int y, int z) {
        Material t = cv.type(x, y, z);
        return t != null && (t.isAir() || Blocks.replaceable(t));
    }

    /** Pose un bloc seulement dans l'air ou une plante. */
    static boolean put(Canvas cv, int x, int y, int z, BlockData d) {
        if (!free(cv, x, y, z)) return false;
        cv.set(x, y, z, d);
        return true;
    }

    static boolean put(Canvas cv, int x, int y, int z, Material m) {
        return put(cv, x, y, z, m.createBlockData());
    }

    /** Pose un bloc en remplaçant ce qui s'y trouve (sauf hors zone). */
    static void force(Canvas cv, int x, int y, int z, BlockData d) {
        if (cv.type(x, y, z) == null) return;
        cv.set(x, y, z, d);
    }

    static void force(Canvas cv, int x, int y, int z, Material m) {
        force(cv, x, y, z, m.createBlockData());
    }

    static void air(Canvas cv, int x, int y, int z) {
        force(cv, x, y, z, Material.AIR);
    }

    // ------------------------------------------------------------------ données de blocs

    static BlockData axis(Material m, Axis a) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(a);
        return d;
    }

    static BlockData facing(Material m, BlockFace f) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir) {
            try {
                dir.setFacing(f);
            } catch (IllegalArgumentException ignored) {
                // face non permise : on laisse la valeur par défaut
            }
        }
        return d;
    }

    static BlockData stairs(Material m, BlockFace f, boolean top) {
        BlockData d = m.createBlockData();
        if (d instanceof Stairs s) {
            s.setFacing(f);
            s.setHalf(top ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        }
        return d;
    }

    static BlockData slab(Material m, boolean top) {
        BlockData d = m.createBlockData();
        if (d instanceof Slab s) s.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return d;
    }

    static BlockData snow(int layers) {
        BlockData d = Material.SNOW.createBlockData();
        if (d instanceof Snow s) s.setLayers(Math.max(s.getMinimumLayers(), Math.min(s.getMaximumLayers(), layers)));
        return d;
    }

    /** Bloc de champignon géant avec les faces voulues (par défaut toutes visibles). */
    static BlockData shroom(Material m, boolean down) {
        BlockData d = m.createBlockData();
        if (d instanceof MultipleFacing mf) {
            for (BlockFace f : mf.getAllowedFaces()) mf.setFace(f, true);
            if (!down) mf.setFace(BlockFace.DOWN, false);
        }
        return d;
    }

    static BlockFace opposite(BlockFace f) {
        return f.getOppositeFace();
    }

    // ------------------------------------------------------------------ petites constructions

    /** Lampadaire : poteau de h blocs posé sur le sol (y = premier bloc au-dessus du sol), lanterne au sommet. */
    static boolean lamp(Canvas cv, int x, int y, int z, Material post, int h) {
        for (int k = 0; k <= h; k++) {
            if (!free(cv, x, y + k, z)) return false;
        }
        Material below = cv.type(x, y - 1, z);
        if (!solid(below) && !land(below)) return false;
        for (int k = 0; k < h; k++) cv.set(x, y + k, z, post.createBlockData());
        cv.set(x, y + h, z, Material.LANTERN.createBlockData());
        return true;
    }

    /** Pilier : base, fût, tête ; « broken » raccourcit et sème des gravats au pied. */
    static void pillar(Canvas cv, Random r, int x, int y, int z, Material base, Material shaft, Material cap,
                       Material slab, int h, boolean broken) {
        int top = broken ? Math.max(2, h - 1 - r.nextInt(3)) : h;
        for (int k = 0; k < top; k++) {
            Material m = k == 0 ? base : (k == top - 1 && !broken ? cap : shaft);
            force(cv, x, y + k, z, m);
        }
        if (!broken && slab != null) force(cv, x, y + top, z, slab.createBlockData());
        if (broken) {
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                if (r.nextInt(100) < 45) put(cv, x + d[0], y, z + d[1], r.nextBoolean() ? shaft : base);
            }
        }
    }

    /** Supprime les blocs connectés qui répondent au test (arbres, champignons géants) à partir de points de départ. */
    static int removeConnected(Canvas cv, int[][] starts, java.util.function.Predicate<Material> test, int limit) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (int[] s : starts) {
            q.add(s);
            seen.add(key(s[0], s[1], s[2]));
        }
        int removed = 0;
        while (!q.isEmpty() && removed < limit) {
            int[] p = q.poll();
            Material m = cv.type(p[0], p[1], p[2]);
            if (m == null || !test.test(m)) continue;
            cv.set(p[0], p[1], p[2], Material.AIR.createBlockData());
            removed++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nx = p[0] + dx, ny = p[1] + dy, nz = p[2] + dz;
                        if (seen.add(key(nx, ny, nz))) q.add(new int[] {nx, ny, nz});
                    }
                }
            }
        }
        return removed;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}

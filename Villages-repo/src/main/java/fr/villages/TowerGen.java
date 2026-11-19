package fr.villages;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tour de guet en bois d'épicéa, très rare dans les forêts de taïga.
 * Quatre pieds de 7x7, deux étages fermés 5x5, une plate-forme avec rambarde et un toit en pyramide.
 */
final class TowerGen {
    private TowerGen() {}

    private static final int CELL = 12; // taille d'une case, en chunks

    /** Ce chunk est-il celui choisi pour sa case ? Retourne le centre du chunk (x, z) ou null. */
    static int[] chosen(long worldSeed, int cx, int cz, double chance) {
        int gx = Math.floorDiv(cx, CELL), gz = Math.floorDiv(cz, CELL);
        long h = G.mix(worldSeed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL) ^ 0x70E4L);
        Random r = new Random(h);
        if (r.nextDouble() >= chance) return null;
        int tx = gx * CELL + 1 + r.nextInt(CELL - 2); // on reste loin du bord de la case
        int tz = gz * CELL + 1 + r.nextInt(CELL - 2);
        if (tx != cx || tz != cz) return null;
        return new int[] {cx * 16 + 8, cz * 16 + 8};
    }

    /** Construit la tour si le terrain s'y prête. Retourne true si elle est posée. */
    static boolean build(World w, int px, int pz, long seed, boolean chest) {
        Canvas cv = new WorldCanvas(w);
        // biome : taïga uniquement
        int gy0 = G.ground(cv, px, pz);
        if (gy0 == G.NONE) return false;
        if (!cv.biome(px, gy0, pz).contains("taiga")) return false;

        // relief : 9 points d'échantillonnage sur l'emprise
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                int y = G.ground(cv, px + dx, pz + dz);
                if (y == G.NONE) return false;
                Material g = cv.type(px + dx, y, pz + dz);
                if (!G.land(g) || Blocks.liquid(g)) return false;
                if (cv.type(px + dx, y + 1, pz + dz) == Material.WATER) return false;
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        if (max - min > 4) return false;
        int base = max; // sol de référence (la case y = base est le niveau du sol)
        Random r = new Random(G.mix(seed ^ (px * 31L) ^ (pz * 17L)));

        clearTrees(cv, px, base, pz);
        // terrassement : on comble les colonnes plus basses
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = px + dx, z = pz + dz;
                int y = G.ground(cv, x, z);
                if (y == G.NONE) continue;
                for (int yy = y + 1; yy <= base; yy++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    Material f = yy == base ? (edge ? Material.COARSE_DIRT : Material.PODZOL) : Material.DIRT;
                    G.force(cv, x, yy, z, f);
                }
                // on dégage tout ce qui dépasse du niveau de référence
                for (int yy = base + 1; yy <= base + 24; yy++) {
                    Material t = cv.type(x, yy, z);
                    if (t != null && (t.isAir())) continue;
                    if (t != null && (Blocks.replaceable(t) || Blocks.leaf(t) || Blocks.anyLog(t))) G.air(cv, x, yy, z);
                }
            }
        }

        int y0 = base; // niveau du sol ; le premier plancher est à y0
        Material log = Material.SPRUCE_LOG, planks = Material.SPRUCE_PLANKS, strip = Material.STRIPPED_SPRUCE_LOG;
        Material fence = Material.SPRUCE_FENCE, slab = Material.SPRUCE_SLAB, stairs = Material.SPRUCE_STAIRS;

        // sol de la salle du bas
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) G.force(cv, px + dx, y0, pz + dz, planks);
        }
        // pieds extérieurs 7x7 et poutres
        int[][] legs = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] l : legs) {
            for (int k = 1; k <= 8; k++) G.force(cv, px + l[0], y0 + k, pz + l[1], G.axis(log, org.bukkit.Axis.Y));
            // pied enfoncé dans le sol
            for (int k = 0; k >= -2; k--) {
                Material t = cv.type(px + l[0], y0 + k, pz + l[1]);
                if (t != null && (t.isAir() || Blocks.replaceable(t))) G.force(cv, px + l[0], y0 + k, pz + l[1], log);
            }
        }
        // pieds posés sur des socles de pierre (un peu moussus)
        for (int[] l : legs) {
            G.force(cv, px + l[0], y0, pz + l[1], r.nextInt(100) < 40 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
        }
        for (int[] beamY : new int[][] {{5}, {8}}) {
            int yy = y0 + beamY[0];
            for (int i = -2; i <= 2; i++) {
                G.force(cv, px + i, yy, pz - 3, G.axis(log, org.bukkit.Axis.X));
                G.force(cv, px + i, yy, pz + 3, G.axis(log, org.bukkit.Axis.X));
                G.force(cv, px - 3, yy, pz + i, G.axis(log, org.bukkit.Axis.Z));
                G.force(cv, px + 3, yy, pz + i, G.axis(log, org.bukkit.Axis.Z));
            }
        }
        // jambes de force diagonales sous la plate-forme
        for (int[] l : legs) {
            int sx = Integer.signum(-l[0]), sz = Integer.signum(-l[1]);
            G.put(cv, px + l[0] + sx, y0 + 4, pz + l[1], G.stairs(stairs, sx > 0 ? BlockFace.WEST : BlockFace.EAST, true));
            G.put(cv, px + l[0], y0 + 4, pz + l[1] + sz, G.stairs(stairs, sz > 0 ? BlockFace.NORTH : BlockFace.SOUTH, true));
        }

        // deux étages fermés de 5x5 : y0+1..y0+4 puis y0+6..y0+8
        for (int story = 0; story < 2; story++) {
            int ys = y0 + 1 + story * 5;
            int hgt = story == 0 ? 4 : 3;
            for (int k = 0; k < hgt; k++) {
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        boolean wall = Math.abs(i) == 2 || Math.abs(j) == 2;
                        boolean corner = Math.abs(i) == 2 && Math.abs(j) == 2;
                        if (!wall) {
                            G.air(cv, px + i, ys + k, pz + j);
                        } else if (corner) {
                            G.force(cv, px + i, ys + k, pz + j, G.axis(strip, org.bukkit.Axis.Y));
                        } else {
                            G.force(cv, px + i, ys + k, pz + j, planks);
                        }
                    }
                }
            }
            // fenêtres à l'est et à l'ouest, et au nord
            int wy = ys + 1;
            G.force(cv, px + 2, wy, pz, Material.GLASS_PANE);
            G.force(cv, px - 2, wy, pz, Material.GLASS_PANE);
            G.force(cv, px, wy, pz - 2, Material.GLASS_PANE);
        }
        // plancher de l'étage et plate-forme (7x7)
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (i == 0 && j == -1) continue; // trou de l'échelle
                G.force(cv, px + i, y0 + 5, pz + j, planks);
            }
        }
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                if (i == 0 && j == -1) continue;
                boolean edge = Math.abs(i) == 3 || Math.abs(j) == 3;
                G.force(cv, px + i, y0 + 9, pz + j, edge ? strip : planks);
            }
        }
        // rambarde et poteaux du toit
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                if (Math.max(Math.abs(i), Math.abs(j)) != 3) continue;
                boolean corner = Math.abs(i) == 3 && Math.abs(j) == 3;
                if (corner) {
                    for (int k = 1; k <= 4; k++) G.force(cv, px + i, y0 + 9 + k, pz + j, G.axis(log, org.bukkit.Axis.Y));
                } else {
                    G.force(cv, px + i, y0 + 10, pz + j, fence);
                }
            }
        }
        // toit en pyramide
        for (int layer = 0; layer < 4; layer++) {
            int rad = 3 - layer;
            int yy = y0 + 14 + layer;
            for (int i = -rad; i <= rad; i++) {
                for (int j = -rad; j <= rad; j++) {
                    int m = Math.max(Math.abs(i), Math.abs(j));
                    if (m < rad) {
                        G.force(cv, px + i, yy, pz + j, planks);
                    } else if (Math.abs(i) == rad && Math.abs(j) == rad) {
                        G.force(cv, px + i, yy, pz + j, G.slab(slab, false));
                    } else if (Math.abs(j) == rad) {
                        G.force(cv, px + i, yy, pz + j, G.stairs(stairs, j < 0 ? BlockFace.SOUTH : BlockFace.NORTH, false));
                    } else {
                        G.force(cv, px + i, yy, pz + j, G.stairs(stairs, i < 0 ? BlockFace.EAST : BlockFace.WEST, false));
                    }
                }
            }
        }
        G.force(cv, px, y0 + 18, pz, G.slab(slab, false));
        G.force(cv, px, y0 + 19, pz, Material.GREEN_BANNER); // fanion au sommet

        // échelle intérieure, collée au mur nord (x=0, z=-1), du sol jusqu'à la plate-forme
        for (int k = 1; k <= 9; k++) G.force(cv, px, y0 + k, pz - 1, G.facing(Material.LADDER, BlockFace.SOUTH));
        // le mur nord derrière l'échelle ne doit pas être une fenêtre
        for (int k = 1; k <= 9; k++) {
            Material t = cv.type(px, y0 + k, pz - 2);
            if (t == Material.GLASS_PANE) G.force(cv, px, y0 + k, pz - 2, planks);
        }
        // porte au sud avec marche
        G.air(cv, px, y0 + 1, pz + 2);
        G.air(cv, px, y0 + 2, pz + 2);
        BlockData lower = Material.SPRUCE_DOOR.createBlockData();
        BlockData upper = Material.SPRUCE_DOOR.createBlockData();
        if (lower instanceof Door d1 && upper instanceof Door d2) {
            d1.setFacing(BlockFace.SOUTH);
            d2.setFacing(BlockFace.SOUTH);
            d1.setHalf(Bisected.Half.BOTTOM);
            d2.setHalf(Bisected.Half.TOP);
        }
        G.force(cv, px, y0 + 1, pz + 2, lower);
        G.force(cv, px, y0 + 2, pz + 2, upper);
        G.force(cv, px, y0 + 1, pz + 3, G.stairs(Material.COBBLESTONE_STAIRS, BlockFace.NORTH, false));
        // lanternes à la porte
        G.lamp(cv, px - 2, y0 + 1, pz + 3, fence, 2);
        G.lamp(cv, px + 2, y0 + 1, pz + 3, fence, 2);
        // lanternes suspendues dans les deux salles
        hang(cv, px, y0 + 4, pz + 1);
        hang(cv, px, y0 + 8, pz + 1);
        // plate-forme : une lanterne sur la rambarde
        G.force(cv, px + 2, y0 + 11, pz + 3, Material.LANTERN);

        // mobilier : établi, tonneau, coffre
        G.force(cv, px - 1, y0 + 1, pz + 1, Material.CRAFTING_TABLE);
        G.force(cv, px + 1, y0 + 1, pz + 1, G.facing(Material.BARREL, BlockFace.UP));
        G.force(cv, px - 1, y0 + 1, pz - 1, Material.BOOKSHELF);
        G.force(cv, px + 1, y0 + 1, pz - 1, Material.BOOKSHELF);
        if (chest) {
            placeChest(w, px + 1, y0 + 6, pz + 1, BlockFace.WEST, r);
        }
        G.force(cv, px - 1, y0 + 6, pz + 1, Material.LOOM);
        // toiles d'araignée dans les coins du haut (la tour est ancienne)
        for (int[] l : new int[][] {{-1, -1}, {1, -1}}) {
            if (r.nextInt(100) < 70) G.put(cv, px + l[0], y0 + 8, pz + l[1], Material.COBWEB);
        }

        // abords : clôture cassée, bûches, tonneaux, baies
        for (int i = -5; i <= 5; i++) {
            for (int j = -5; j <= 5; j++) {
                int m = Math.max(Math.abs(i), Math.abs(j));
                if (m < 4 || m > 5) continue;
                double u = G.hash(seed, px + i, 5, pz + j);
                int gy = G.ground(cv, px + i, pz + j);
                if (gy == G.NONE || !G.land(cv.type(px + i, gy, pz + j))) continue;
                if (m == 4 && Math.abs(i) != 0 && u < 0.22 && !(j > 0 && Math.abs(i) <= 1)) {
                    G.put(cv, px + i, gy + 1, pz + j, fence);
                } else if (u > 0.93) {
                    G.put(cv, px + i, gy + 1, pz + j, Material.SWEET_BERRY_BUSH);
                } else if (u > 0.89) {
                    G.put(cv, px + i, gy + 1, pz + j, G.axis(log, r.nextBoolean() ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
                } else if (u > 0.86) {
                    G.put(cv, px + i, gy + 1, pz + j, Material.FERN);
                }
            }
        }
        return true;
    }

    private static void hang(Canvas cv, int x, int y, int z) {
        BlockData d = Material.LANTERN.createBlockData();
        if (d instanceof Lantern l) l.setHanging(true);
        G.force(cv, x, y, z, d);
    }

    /** Retire les arbres qui gênent : troncs et feuilles connectés à la zone. */
    private static void clearTrees(Canvas cv, int px, int base, int pz) {
        List<int[]> starts = new ArrayList<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 22; dy++) {
                    Material t = cv.type(px + dx, base + dy, pz + dz);
                    if (t != null && Blocks.anyLog(t)) starts.add(new int[] {px + dx, base + dy, pz + dz});
                }
            }
        }
        if (starts.isEmpty()) return;
        G.removeConnected(cv, starts.toArray(new int[0][]),
                m -> Blocks.anyLog(m) || Blocks.leaf(m), 2500);
    }

    // ------------------------------------------------------------------ coffre

    private static void placeChest(World w, int x, int y, int z, BlockFace face, Random r) {
        Block b = w.getBlockAt(x, y, z);
        BlockData d = Material.CHEST.createBlockData();
        if (d instanceof Directional dir) dir.setFacing(face);
        b.setBlockData(d, false);
        if (b.getState() instanceof org.bukkit.block.Chest ch) fill(ch.getBlockInventory(), r);
    }

    private static void add(List<ItemStack> out, String mat, int n) {
        Material m = Material.getMaterial(mat);
        if (m != null && n > 0) out.add(new ItemStack(m, n));
    }

    /** Ravitaillement du guetteur : nourriture, flèches, torches, un peu de métal, parfois une longue-vue. */
    private static void fill(Inventory inv, Random r) {
        List<ItemStack> items = new ArrayList<>();
        add(items, "BREAD", 2 + r.nextInt(4));
        add(items, "COOKED_SALMON", 1 + r.nextInt(3));
        add(items, "SWEET_BERRIES", 3 + r.nextInt(6));
        add(items, "ARROW", 8 + r.nextInt(17));
        add(items, "TORCH", 6 + r.nextInt(9));
        add(items, "SPRUCE_LOG", 4 + r.nextInt(9));
        add(items, "COAL", 3 + r.nextInt(6));
        if (r.nextInt(100) < 55) add(items, "IRON_INGOT", 1 + r.nextInt(4));
        if (r.nextInt(100) < 35) add(items, "BOW", 1);
        if (r.nextInt(100) < 30) add(items, "EMERALD", 1 + r.nextInt(3));
        if (r.nextInt(100) < 25) add(items, "SPYGLASS", 1);
        if (r.nextInt(100) < 20) add(items, "GOLD_INGOT", 1 + r.nextInt(3));
        if (r.nextInt(100) < 15) add(items, "SPRUCE_SAPLING", 1 + r.nextInt(3));
        if (r.nextInt(100) < 7) add(items, "DIAMOND", 1);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, r);
        for (int i = 0; i < items.size() && i < slots.size(); i++) inv.setItem(slots.get(i), items.get(i));
    }
}

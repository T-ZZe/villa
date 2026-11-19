package fr.villages;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Maison champignon : un pied de champignon habitable sous un grand chapeau rouge (ou brun), sur l'île champignon.
 * Rez-de-chaussée avec établi et coffre, mezzanine avec lit, échelle, fenêtres jaunes et lanternes.
 */
final class HouseGen {
    private HouseGen() {}

    private static final int CELL = 4; // taille d'une case, en chunks

    /** Centre (x, z) du chunk s'il est celui choisi pour sa case, sinon null. */
    static int[] chosen(long worldSeed, int cx, int cz, double chance) {
        int gx = Math.floorDiv(cx, CELL), gz = Math.floorDiv(cz, CELL);
        long h = G.mix(worldSeed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL) ^ 0x4055L);
        Random r = new Random(h);
        if (r.nextDouble() >= chance) return null;
        int tx = gx * CELL + r.nextInt(CELL);
        int tz = gz * CELL + r.nextInt(CELL);
        if (tx != cx || tz != cz) return null;
        return new int[] {cx * 16 + 8, cz * 16 + 8};
    }

    private static boolean shroomBlock(Material m) {
        return m == Material.RED_MUSHROOM_BLOCK || m == Material.BROWN_MUSHROOM_BLOCK || m == Material.MUSHROOM_STEM;
    }

    static boolean build(World w, int px, int pz, long seed, boolean chest) {
        Canvas cv = new WorldCanvas(w);
        int gy0 = G.ground(cv, px, pz, true);
        if (gy0 == G.NONE) return false;
        if (!cv.biome(px, gy0, pz).contains("mushroom")) return false;

        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                int y = G.ground(cv, px + dx, pz + dz, true);
                if (y == G.NONE) return false;
                Material g = cv.type(px + dx, y, pz + dz);
                if (g != Material.MYCELIUM && g != Material.DIRT && g != Material.PODZOL && g != Material.GRASS_BLOCK) {
                    return false;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        if (max - min > 3) return false;
        int base = max;
        Random r = new Random(G.mix(seed ^ (px * 31L) ^ (pz * 17L)));

        // on retire les champignons géants qui gênent (en entier, pour ne rien laisser flotter)
        List<int[]> starts = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = 0; dy <= 14; dy++) {
                    Material t = cv.type(px + dx, base + dy, pz + dz);
                    if (t != null && shroomBlock(t)) starts.add(new int[] {px + dx, base + dy, pz + dz});
                }
            }
        }
        if (!starts.isEmpty()) G.removeConnected(cv, starts.toArray(new int[0][]), HouseGen::shroomBlock, 1500);

        boolean brown = r.nextInt(100) < 30;
        Material capMat = brown ? Material.BROWN_MUSHROOM_BLOCK : Material.RED_MUSHROOM_BLOCK;
        BlockData cap = G.shroom(capMat, false);       // dessous ouvert : on voit les lamelles
        BlockData capTop = G.shroom(capMat, true);
        BlockData stem = G.shroom(Material.MUSHROOM_STEM, true);

        // dégagement de la zone et terrassement du pied (rayon 3)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int x = px + dx, z = pz + dz;
                for (int yy = base + 1; yy <= base + 14; yy++) {
                    Material t = cv.type(x, yy, z);
                    if (t != null && !t.isAir() && (Blocks.replaceable(t) || Blocks.leaf(t))) G.air(cv, x, yy, z);
                }
                if (dx * dx + dz * dz <= 12) {
                    int gy = G.ground(cv, x, z, true);
                    if (gy == G.NONE) continue;
                    for (int yy = gy + 1; yy < base; yy++) G.force(cv, x, yy, z, Material.DIRT);
                    G.force(cv, x, base, z, Material.PODZOL);
                }
            }
        }

        int y0 = base; // plancher du rez-de-chaussée
        // pied : cylindre creux (murs entre 2.3 et 3.4 de rayon), hauteur 6
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.4) continue;
                boolean wall = d >= 2.4;
                for (int k = 1; k <= 6; k++) {
                    if (wall) G.force(cv, px + dx, y0 + k, pz + dz, stem);
                    else G.air(cv, px + dx, y0 + k, pz + dz);
                }
                if (!wall) G.force(cv, px + dx, y0, pz + dz, Material.SPRUCE_PLANKS);
            }
        }
        // mezzanine (plancher à y0+4) sauf le trou de l'échelle
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) >= 2.4) continue;
                if (dx == 0 && dz == -2) continue;
                G.force(cv, px + dx, y0 + 4, pz + dz, Material.SPRUCE_PLANKS);
            }
        }

        // chapeau : ellipsoïde posé à y0+7, rayon 6.6 ; dessous ouvert
        for (int t = 0; t <= 5; t++) {
            double rad = 6.6 * Math.sqrt(1.0 - (t / 5.4) * (t / 5.4));
            int yy = y0 + 7 + t;
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rad) continue;
                    // le toit de la pièce haute est plein ; ailleurs le chapeau est plein aussi (dôme massif)
                    boolean outer = d > rad - 1.2 || t == 5;
                    BlockData bd = t == 0 ? cap : (outer ? capTop : cap);
                    // taches claires sur un chapeau rouge
                    if (!brown && outer && t >= 1 && G.hash(seed, px + dx, 30 + t, pz + dz) < 0.10) bd = stem;
                    G.force(cv, px + dx, yy, pz + dz, bd);
                }
            }
        }
        // bord retombant du chapeau, irrégulier
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 5.6 || d > 6.6) continue;
                double u = G.hash(seed, px + dx, 21, pz + dz);
                if (u < 0.72) G.force(cv, px + dx, y0 + 6, pz + dz, cap);
                if (u < 0.22) G.force(cv, px + dx, y0 + 5, pz + dz, cap);
            }
        }

        // cheminée : un tuyau de pied qui dépasse du chapeau, avec un feu de camp qui fume
        if (r.nextInt(100) < 75) {
            Material domeTop = cv.type(px + 1, y0 + 12, pz + 1);
            if (domeTop != null && shroomBlock(domeTop)) {
                G.force(cv, px + 1, y0 + 13, pz + 1, stem);
                G.force(cv, px + 1, y0 + 14, pz + 1, stem);
                G.force(cv, px + 1, y0 + 15, pz + 1, Material.CAMPFIRE);
            }
        }

        // porte au sud (rayon 3), fenêtres jaunes, échelle
        G.air(cv, px, y0 + 1, pz + 3);
        G.air(cv, px, y0 + 2, pz + 3);
        BlockData lower = Material.SPRUCE_DOOR.createBlockData();
        BlockData upper = Material.SPRUCE_DOOR.createBlockData();
        if (lower instanceof Door d1 && upper instanceof Door d2) {
            d1.setFacing(BlockFace.SOUTH);
            d2.setFacing(BlockFace.SOUTH);
            d1.setHalf(Bisected.Half.BOTTOM);
            d2.setHalf(Bisected.Half.TOP);
        }
        G.force(cv, px, y0 + 1, pz + 3, lower);
        G.force(cv, px, y0 + 2, pz + 3, upper);
        Material pane = Material.YELLOW_STAINED_GLASS_PANE;
        G.force(cv, px + 3, y0 + 2, pz, pane);
        G.force(cv, px - 3, y0 + 2, pz, pane);
        G.force(cv, px + 2, y0 + 2, pz - 2, pane);
        G.force(cv, px - 2, y0 + 2, pz - 2, pane);
        G.force(cv, px + 3, y0 + 5, pz, pane);
        G.force(cv, px - 3, y0 + 5, pz, pane);
        G.force(cv, px + 2, y0 + 5, pz - 2, pane);
        G.force(cv, px - 2, y0 + 5, pz - 2, pane);
        G.force(cv, px + 2, y0 + 5, pz + 2, pane);
        G.force(cv, px - 2, y0 + 5, pz + 2, pane);
        // échelle contre le mur nord, de y0+1 à y0+5
        for (int k = 1; k <= 5; k++) G.force(cv, px, y0 + k, pz - 2, G.facing(Material.LADDER, BlockFace.SOUTH));

        // mobilier du rez-de-chaussée
        G.force(cv, px - 1, y0 + 1, pz + 1, Material.CRAFTING_TABLE);
        G.force(cv, px + 1, y0 + 1, pz + 1, Material.FURNACE.createBlockData());
        G.force(cv, px + 2, y0 + 1, pz, G.facing(Material.BARREL, BlockFace.UP));
        G.force(cv, px - 2, y0 + 1, pz, Material.COMPOSTER);
        if (chest) placeChest(w, px + 1, y0 + 1, pz - 1, BlockFace.SOUTH, r);
        else G.force(cv, px + 1, y0 + 1, pz - 1, Material.BARREL);
        // mezzanine : lit (pied au sud-ouest, tête à l'est), coffre-table, lanternes
        placeBed(cv, px - 1, y0 + 5, pz);
        G.force(cv, px + 1, y0 + 5, pz - 1, G.facing(Material.BARREL, BlockFace.UP));
        G.force(cv, px + 1, y0 + 6, pz - 1, Material.POTTED_RED_MUSHROOM);
        hang(cv, px, y0 + 3, pz);
        hang(cv, px, y0 + 6, pz + 1);

        // chemin devant la porte et lanternes sous le chapeau
        for (int k = 4; k <= 8; k++) {
            int gy = G.surface(cv, px, pz + k);
            if (gy != G.NONE && G.land(cv.type(px, gy, pz + k)) && G.free(cv, px, gy + 1, pz + k)) {
                G.force(cv, px, gy, pz + k, Material.DIRT_PATH);
            }
        }
        for (int[] lp : new int[][] {{-1, 5}, {1, 7}}) { // deux lampadaires le long du chemin
            int lgy = G.surface(cv, px + lp[0], pz + lp[1]);
            if (lgy == G.NONE || !G.land(cv.type(px + lp[0], lgy, pz + lp[1]))) continue;
            G.lamp(cv, px + lp[0], lgy + 1, pz + lp[1], Material.SPRUCE_FENCE, 2);
        }
        hangUnder(cv, px - 2, y0 + 5, pz + 4);
        hangUnder(cv, px + 2, y0 + 5, pz + 4);

        // petits champignons et pot autour
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 3.6 || d > 7.4) continue;
                double u = G.hash(seed, px + dx, 22, pz + dz);
                if (u > 0.07) continue;
                int gy = G.surface(cv, px + dx, pz + dz);
                if (gy == G.NONE) continue;
                Material g = cv.type(px + dx, gy, pz + dz);
                if (g != Material.MYCELIUM && g != Material.PODZOL) continue;
                G.put(cv, px + dx, gy + 1, pz + dz, u < 0.035 ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM);
            }
        }
        return true;
    }

    private static void hang(Canvas cv, int x, int y, int z) {
        BlockData d = Material.LANTERN.createBlockData();
        if (d instanceof Lantern l) l.setHanging(true);
        G.force(cv, x, y, z, d);
    }

    /** Lanterne suspendue sous le bord du chapeau, seulement s'il y a un bloc de chapeau au-dessus. */
    private static void hangUnder(Canvas cv, int x, int y, int z) {
        Material above = cv.type(x, y + 1, z);
        if (above == null || !shroomBlock(above)) return;
        if (!G.free(cv, x, y, z)) return;
        hang(cv, x, y, z);
    }

    private static void placeBed(Canvas cv, int x, int y, int z) {
        BlockData foot = Material.RED_BED.createBlockData();
        BlockData head = Material.RED_BED.createBlockData();
        if (foot instanceof Bed f && head instanceof Bed h) {
            f.setPart(Bed.Part.FOOT);
            f.setFacing(BlockFace.EAST);
            h.setPart(Bed.Part.HEAD);
            h.setFacing(BlockFace.EAST);
        }
        G.force(cv, x, y, z, foot);
        G.force(cv, x + 1, y, z, head);
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

    /** Provisions du champignonniste : champignons, soupe, pain, un peu de bois, parfois un objet rare. */
    private static void fill(Inventory inv, Random r) {
        List<ItemStack> items = new ArrayList<>();
        add(items, "RED_MUSHROOM", 3 + r.nextInt(6));
        add(items, "BROWN_MUSHROOM", 3 + r.nextInt(6));
        add(items, "MUSHROOM_STEW", 1 + r.nextInt(3));
        add(items, "BREAD", 2 + r.nextInt(4));
        add(items, "BONE_MEAL", 3 + r.nextInt(8));
        add(items, "WHEAT_SEEDS", 4 + r.nextInt(9));
        add(items, "BOWL", 2 + r.nextInt(4));
        if (r.nextInt(100) < 45) add(items, "MYCELIUM", 2 + r.nextInt(5));
        if (r.nextInt(100) < 40) add(items, "GLOW_BERRIES", 3 + r.nextInt(5));
        if (r.nextInt(100) < 30) add(items, "EMERALD", 1 + r.nextInt(3));
        if (r.nextInt(100) < 20) add(items, "GOLDEN_CARROT", 1 + r.nextInt(2));
        if (r.nextInt(100) < 12) add(items, "NAME_TAG", 1);
        if (r.nextInt(100) < 8) add(items, "DIAMOND", 1);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, r);
        for (int i = 0; i < items.size() && i < slots.size(); i++) inv.setItem(slots.get(i), items.get(i));
    }
}

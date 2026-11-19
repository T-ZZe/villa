package fr.villages;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Améliore un village déjà généré : lampadaires, lanternes aux portes, jardinets, chemins moins réguliers,
 * fondations sous les maisons suspendues. Les matériaux suivent le biome du village.
 */
final class VillageGen {
    private VillageGen() {}

    /** Palette d'un type de village. */
    private record Style(String kind, Material post, Material log, Material stairs, Material planks,
                         Material foundation, Material foundationAlt, Material leaves) {}

    private static Style styleOf(String key) {
        String k = key.startsWith("village_") ? key.substring(8) : key;
        return switch (k) {
            case "desert" -> new Style(k, Material.SANDSTONE_WALL, Material.CUT_SANDSTONE, Material.SANDSTONE_STAIRS,
                    Material.SMOOTH_SANDSTONE, Material.SANDSTONE, Material.CUT_SANDSTONE, Material.JUNGLE_LEAVES);
            case "savanna" -> new Style(k, Material.ACACIA_FENCE, Material.ACACIA_LOG, Material.ACACIA_STAIRS,
                    Material.ACACIA_PLANKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ACACIA_LEAVES);
            case "snowy" -> new Style(k, Material.SPRUCE_FENCE, Material.SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_PLANKS, Material.COBBLESTONE, Material.STONE_BRICKS, Material.SPRUCE_LEAVES);
            case "taiga" -> new Style(k, Material.SPRUCE_FENCE, Material.SPRUCE_LOG, Material.SPRUCE_STAIRS,
                    Material.SPRUCE_PLANKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.SPRUCE_LEAVES);
            default -> new Style("plains", Material.OAK_FENCE, Material.OAK_LOG, Material.OAK_STAIRS,
                    Material.OAK_PLANKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.OAK_LEAVES);
        };
    }

    /** Contexte d'un village pendant son amélioration. */
    private static final class Ctx {
        Style st;
        long seed;
        int minX, maxX, minZ, maxZ;
        List<int[]> buildings = new ArrayList<>(); // {minX, maxX, minY, maxY, minZ, maxZ}
        Set<Long> doorZone = new HashSet<>();
        List<int[]> lamps = new ArrayList<>();
        int decor = 0;
    }

    /** Liste des étapes à exécuter (une par maison, puis une par chunk pour les chemins). */
    static List<Runnable> plan(Canvas cv, Struct.Info info, long worldSeed) {
        Ctx c = new Ctx();
        c.st = styleOf(info.key());
        BoundingBox b = info.box();
        c.minX = G.lo(b.getMinX());
        c.maxX = G.hi(b.getMaxX());
        c.minZ = G.lo(b.getMinZ());
        c.maxZ = G.hi(b.getMaxZ());
        c.seed = G.mix(worldSeed ^ (c.minX * 341873128712L) ^ (c.minZ * 132897987541L) ^ 0x51A6EL);
        for (BoundingBox p : info.pieces()) {
            int x0 = G.lo(p.getMinX()), x1 = G.hi(p.getMaxX());
            int y0 = G.lo(p.getMinY()), y1 = G.hi(p.getMaxY());
            int z0 = G.lo(p.getMinZ()), z1 = G.hi(p.getMaxZ());
            // une maison : assez large et assez haute (les rues sont plates)
            if (x1 - x0 >= 3 && z1 - z0 >= 3 && y1 - y0 >= 3) c.buildings.add(new int[] {x0, x1, y0, y1, z0, z1});
        }

        List<Runnable> steps = new ArrayList<>();
        for (int[] bd : c.buildings) steps.add(() -> building(cv, c, bd));
        for (int cx = c.minX >> 4; cx <= c.maxX >> 4; cx++) {
            for (int cz = c.minZ >> 4; cz <= c.maxZ >> 4; cz++) {
                final int fx = cx, fz = cz;
                steps.add(() -> roads(cv, c, fx, fz));
            }
        }
        return steps;
    }

    private static boolean inBuilding(Ctx c, int x, int z, int margin) {
        for (int[] b : c.buildings) {
            if (x >= b[0] - margin && x <= b[1] + margin && z >= b[4] - margin && z <= b[5] + margin) return true;
        }
        return false;
    }

    // ================================================================== une maison

    private static void building(Canvas cv, Ctx c, int[] b) {
        Random r = new Random(G.mix(c.seed ^ (b[0] * 73856093L) ^ (b[4] * 19349663L)));
        if (Cfg.foundations) foundation(cv, c, b, r);
        if (Cfg.doorLanterns) doors(cv, c, b, r);
        if (Cfg.yards) yard(cv, c, b, r);
        if (hasDoor(cv, b)) { // seulement les vraies maisons (pas les puits, enclos, champs...)
            if (Cfg.chimneys) chimney(cv, c, b, r);
            if (Cfg.vines) vines(cv, c, b, r);
        }
    }

    private static boolean hasDoor(Canvas cv, int[] b) {
        for (int x = b[0]; x <= b[1]; x++) {
            for (int z = b[4]; z <= b[5]; z++) {
                for (int y = b[2]; y <= Math.min(b[3], b[2] + 5); y++) {
                    Material t = cv.type(x, y, z);
                    if (t != null && t.name().endsWith("_DOOR")) return true;
                }
            }
        }
        return false;
    }

    /** Point le plus haut de la maison (le toit) dans cette colonne. */
    private static int roofTop(Canvas cv, int x, int z, int[] b) {
        for (int y = b[3] + 3; y >= b[2]; y--) {
            Material t = cv.type(x, y, z);
            if (t == null) return G.NONE;
            if (G.solid(t) && !Blocks.anyLog(t)) return y;
        }
        return G.NONE;
    }

    // ------------------------------------------------------------------ cheminée

    /** Cheminée de pierre sur le faîte du toit, avec un feu de camp au sommet qui fait de la fumée. */
    private static void chimney(Canvas cv, Ctx c, int[] b, Random r) {
        if (b[3] - b[2] < 4 || r.nextDouble() >= Cfg.chimneyChance) return;
        int best = G.NONE;
        List<int[]> tops = new ArrayList<>();
        for (int x = b[0] + 1; x <= b[1] - 1; x++) {
            for (int z = b[4] + 1; z <= b[5] - 1; z++) {
                int y = roofTop(cv, x, z, b);
                if (y == G.NONE) continue;
                if (y > best) {
                    best = y;
                    tops.clear();
                }
                if (y == best) tops.add(new int[] {x, z});
            }
        }
        if (tops.isEmpty() || best < b[2] + 3) return;
        int[] p = tops.get(r.nextInt(tops.size()));
        for (int k = 1; k <= 4; k++) if (!G.free(cv, p[0], best + k, p[1])) return;
        cv.set(p[0], best, p[1], c.st.foundation.createBlockData());
        cv.set(p[0], best + 1, p[1], c.st.foundation.createBlockData());
        cv.set(p[0], best + 2, p[1], c.st.foundationAlt.createBlockData());
        cv.set(p[0], best + 3, p[1], Material.CAMPFIRE.createBlockData());
    }

    // ------------------------------------------------------------------ lierre

    private static boolean fullWall(Material t) {
        if (t == null || !G.solid(t)) return false;
        String n = t.name();
        for (String bad : new String[] {"PANE", "GLASS", "DOOR", "STAIRS", "SLAB", "FENCE", "WALL", "LADDER", "BED", "SIGN",
                "BANNER", "CARPET", "POT", "CHEST", "BARREL", "LANTERN", "TORCH", "CAMPFIRE", "TABLE", "FURNACE"}) {
            if (n.contains(bad)) return false;
        }
        return true;
    }

    /** Quelques bandes de lierre qui retombent le long des murs (plaines, savane, taïga). */
    private static void vines(Canvas cv, Ctx c, int[] b, Random r) {
        String k = c.st.kind();
        if (!(k.equals("plains") || k.equals("taiga") || k.equals("savanna"))) return;
        if (r.nextDouble() >= Cfg.vineChance) return;
        int strips = 2 + r.nextInt(3);
        for (int i = 0; i < strips; i++) {
            int f = r.nextInt(4); // 0 nord, 1 est, 2 sud, 3 ouest
            int wx, wz;
            if (f == 0 || f == 2) {
                if (b[1] - b[0] < 3) continue;
                wx = b[0] + 1 + r.nextInt(b[1] - b[0] - 1);
                wz = f == 0 ? b[4] : b[5];
            } else {
                if (b[5] - b[4] < 3) continue;
                wz = b[4] + 1 + r.nextInt(b[5] - b[4] - 1);
                wx = f == 1 ? b[1] : b[0];
            }
            int ox = wx + G.D4[f][0], oz = wz + G.D4[f][1];
            if (c.doorZone.contains(G.pack(ox, oz)) || inBuilding(c, ox, oz, 0)) continue;
            int top = b[2] + 2 + r.nextInt(3);
            int len = 2 + r.nextInt(3);
            BlockFace toWall = G.H[(f + 2) % 4];
            for (int j = 0; j < len; j++) {
                int y = top - j;
                if (y <= b[2]) break;
                if (!fullWall(cv.type(wx, y, wz)) || !G.free(cv, ox, y, oz)) break;
                BlockData v = Material.VINE.createBlockData();
                if (v instanceof MultipleFacing mf) {
                    mf.setFace(toWall, true);
                    cv.set(ox, y, oz, mf);
                }
            }
        }
    }

    /** Sous une maison dont les murs flottent au-dessus du vide, on descend des fondations de pierre. */
    private static void foundation(Canvas cv, Ctx c, int[] b, Random r) {
        int y0 = b[2];
        for (int x = b[0]; x <= b[1]; x++) {
            for (int z = b[4]; z <= b[5]; z++) {
                boolean edge = x == b[0] || x == b[1] || z == b[4] || z == b[5];
                if (!edge) continue;
                Material t = cv.type(x, y0, z);
                if (!G.solid(t)) continue;
                int y = y0 - 1, n = 0;
                while (n < 10) {
                    Material u = cv.type(x, y, z);
                    if (u == null || !(u.isAir() || Blocks.replaceable(u))) break;
                    Material f = r.nextInt(100) < 25 ? c.st.foundationAlt : c.st.foundation;
                    cv.set(x, y, z, f.createBlockData());
                    y--;
                    n++;
                }
            }
        }
    }

    /** Une lanterne sur poteau à côté de chaque porte extérieure. */
    private static void doors(Canvas cv, Ctx c, int[] b, Random r) {
        for (int x = b[0]; x <= b[1]; x++) {
            for (int z = b[4]; z <= b[5]; z++) {
                for (int y = b[2]; y <= Math.min(b[3], b[2] + 4); y++) {
                    Material t = cv.type(x, y, z);
                    if (t == null || !t.name().endsWith("_DOOR")) continue;
                    BlockData d = cv.data(x, y, z);
                    if (!(d instanceof Door dd) || dd.getHalf() != Bisected.Half.BOTTOM) continue;
                    // côté extérieur : la face de la maison la plus proche
                    int[] dist = {z - b[4], b[1] - x, b[5] - z, x - b[0]}; // nord, est, sud, ouest
                    int face = 0;
                    for (int i = 1; i < 4; i++) if (dist[i] < dist[face]) face = i;
                    if (dist[face] > 1) continue;
                    int dx = G.D4[face][0], dz = G.D4[face][1];
                    int ox = x + dx, oz = z + dz;
                    // zone dégagée devant la porte pour les décors
                    for (int ax = -2; ax <= 2; ax++) {
                        for (int az = -2; az <= 2; az++) c.doorZone.add(G.pack(ox + ax, oz + az));
                    }
                    int sign = r.nextBoolean() ? 1 : -1;
                    int lx = ox + dz * sign, lz = oz + dx * sign; // décalé sur le côté
                    if (inBuilding(c, lx, lz, 0)) continue;
                    G.lamp(cv, lx, y, lz, c.st.post, 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ jardinets

    private static void yard(Canvas cv, Ctx c, int[] b, Random r) {
        int want = (int) Math.round((2 + r.nextInt(3)) * Cfg.yardDensity);
        int placed = 0;
        for (int attempt = 0; attempt < want * 4 + 4 && placed < want; attempt++) {
            int side = r.nextInt(4);
            int d = 1 + r.nextInt(3);
            int x, z;
            switch (side) {
                case 0 -> { x = b[0] + r.nextInt(b[1] - b[0] + 1); z = b[4] - d; }
                case 1 -> { x = b[1] + d; z = b[4] + r.nextInt(b[5] - b[4] + 1); }
                case 2 -> { x = b[0] + r.nextInt(b[1] - b[0] + 1); z = b[5] + d; }
                default -> { x = b[0] - d; z = b[4] + r.nextInt(b[5] - b[4] + 1); }
            }
            if (inBuilding(c, x, z, 0) || c.doorZone.contains(G.pack(x, z))) continue;
            int gy = G.surface(cv, x, z);
            if (gy == G.NONE) continue;
            Material g = cv.type(x, gy, z);
            if (!G.land(g) || g == Material.DIRT_PATH || g == Material.STONE || g == Material.COBBLESTONE) continue;
            if (Math.abs(gy - b[2]) > 5) continue;
            // on ne bouche pas un chemin : pas de décor collé à un chemin
            if (nextToPath(cv, x, gy, z)) continue;
            if (decorate(cv, c, r, x, gy, z, side)) placed++;
        }
    }

    private static boolean nextToPath(Canvas cv, int x, int y, int z) {
        for (int[] d : G.D4) {
            int ny = G.surface(cv, x + d[0], z + d[1]);
            if (ny != G.NONE && cv.type(x + d[0], ny, z + d[1]) == Material.DIRT_PATH) return true;
        }
        return false;
    }

    private static final Material[] FLOWERS = {Material.DANDELION, Material.POPPY, Material.CORNFLOWER,
            Material.OXEYE_DAISY, Material.AZURE_BLUET, Material.ALLIUM, Material.RED_TULIP, Material.PINK_TULIP};
    private static final Material[] POTS = {Material.POTTED_DANDELION, Material.POTTED_POPPY,
            Material.POTTED_CORNFLOWER, Material.POTTED_AZURE_BLUET, Material.POTTED_OXEYE_DAISY};

    /** Choisit et pose un décor selon le type de village. side = côté de la maison (0 nord, 1 est, 2 sud, 3 ouest). */
    private static boolean decorate(Canvas cv, Ctx c, Random r, int x, int gy, int z, int side) {
        int y = gy + 1;
        BlockFace toHouse = G.H[(side + 2) % 4]; // vers la maison
        String k = c.st.kind;
        int roll = r.nextInt(100);
        boolean dirt = Blocks.ground(cv.type(x, gy, z)) && cv.type(x, gy, z) != Material.MYCELIUM;
        if (k.equals("desert")) {
            if (roll < 22) return cactus(cv, r, x, gy, z);
            if (roll < 42) return pot(cv, x, y, z, r, true);
            if (roll < 58) return sandPillar(cv, x, y, z);
            if (roll < 74) return barrels(cv, r, x, y, z);
            if (roll < 90) return bench(cv, c, x, y, z, toHouse, side);
            return lampPost(cv, c, x, y, z);
        }
        if (k.equals("savanna")) {
            if (roll < 20) return hay(cv, r, x, y, z);
            if (roll < 36) return barrels(cv, r, x, y, z);
            if (roll < 52) return bush(cv, c, r, x, y, z);
            if (roll < 66) return pot(cv, x, y, z, r, true);
            if (roll < 80) return woodStack(cv, c, r, x, y, z, side);
            if (roll < 94) return bench(cv, c, x, y, z, toHouse, side);
            return lampPost(cv, c, x, y, z);
        }
        if (k.equals("snowy")) {
            if (roll < 28) return woodStack(cv, c, r, x, y, z, side);
            if (roll < 42) return barrels(cv, r, x, y, z);
            if (roll < 58) return bush(cv, c, r, x, y, z);
            if (roll < 74) return snowman(cv, r, x, y, z);
            if (roll < 90) return bench(cv, c, x, y, z, toHouse, side);
            return lampPost(cv, c, x, y, z);
        }
        if (k.equals("taiga")) {
            if (roll < 26) return woodStack(cv, c, r, x, y, z, side);
            if (roll < 40) return barrels(cv, r, x, y, z);
            if (roll < 58) return berries(cv, r, x, y, z, dirt);
            if (roll < 72) return bush(cv, c, r, x, y, z);
            if (roll < 84) return hay(cv, r, x, y, z);
            if (roll < 96) return bench(cv, c, x, y, z, toHouse, side);
            return lampPost(cv, c, x, y, z);
        }
        if (roll < 30) return dirt && flowers(cv, r, x, gy, z);
        if (roll < 45) return bush(cv, c, r, x, y, z);
        if (roll < 58) return hay(cv, r, x, y, z);
        if (roll < 68) return barrels(cv, r, x, y, z);
        if (roll < 78) return woodStack(cv, c, r, x, y, z, side);
        if (roll < 88) return bench(cv, c, x, y, z, toHouse, side);
        if (roll < 95) return pot(cv, x, y, z, r, false);
        return lampPost(cv, c, x, y, z);
    }

    private static boolean flowers(Canvas cv, Random r, int x, int gy, int z) {
        Material f = FLOWERS[r.nextInt(FLOWERS.length)];
        Material f2 = FLOWERS[r.nextInt(FLOWERS.length)];
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (r.nextInt(100) >= 55) continue;
                Material g = cv.type(x + dx, gy, z + dz);
                if (g != Material.GRASS_BLOCK && g != Material.DIRT && g != Material.PODZOL) continue;
                if (G.put(cv, x + dx, gy + 1, z + dz, r.nextInt(3) == 0 ? f2 : f)) n++;
            }
        }
        return n > 0;
    }

    private static boolean bush(Canvas cv, Ctx c, Random r, int x, int y, int z) {
        BlockData d = c.st.leaves.createBlockData();
        if (d instanceof Leaves l) l.setPersistent(true);
        if (!G.put(cv, x, y, z, d)) return false;
        if (r.nextInt(100) < 35) G.put(cv, x, y + 1, z, d);
        return true;
    }

    private static boolean berries(Canvas cv, Random r, int x, int y, int z, boolean dirt) {
        if (!dirt) return false;
        BlockData d = Material.SWEET_BERRY_BUSH.createBlockData();
        if (d instanceof Ageable a) a.setAge(r.nextInt(100) < 60 ? a.getMaximumAge() : 2);
        boolean ok = G.put(cv, x, y, z, d);
        if (ok && r.nextBoolean()) G.put(cv, x + (r.nextBoolean() ? 1 : -1), y, z, d);
        return ok;
    }

    private static boolean hay(Canvas cv, Random r, int x, int y, int z) {
        if (!G.put(cv, x, y, z, G.axis(Material.HAY_BLOCK, org.bukkit.Axis.Y))) return false;
        if (r.nextInt(100) < 40) G.put(cv, x, y + 1, z, G.axis(Material.HAY_BLOCK, r.nextBoolean() ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
        if (r.nextInt(100) < 40) G.put(cv, x + 1, y, z, G.axis(Material.HAY_BLOCK, org.bukkit.Axis.Y));
        return true;
    }

    private static boolean barrels(Canvas cv, Random r, int x, int y, int z) {
        if (!G.put(cv, x, y, z, G.facing(Material.BARREL, BlockFace.UP))) return false;
        if (r.nextInt(100) < 50) G.put(cv, x + 1, y, z, G.facing(Material.BARREL, BlockFace.UP));
        if (r.nextInt(100) < 25) G.put(cv, x, y + 1, z, G.facing(Material.BARREL, BlockFace.UP));
        return true;
    }

    private static boolean woodStack(Canvas cv, Ctx c, Random r, int x, int y, int z, int side) {
        boolean alongX = side == 0 || side == 2;
        org.bukkit.Axis ax = alongX ? org.bukkit.Axis.X : org.bukkit.Axis.Z;
        int sx = alongX ? 1 : 0, sz = alongX ? 0 : 1;
        if (!G.free(cv, x, y, z) || !G.free(cv, x + sx, y, z + sz)) return false;
        cv.set(x, y, z, G.axis(c.st.log, ax));
        cv.set(x + sx, y, z + sz, G.axis(c.st.log, ax));
        if (r.nextInt(100) < 70) G.put(cv, x, y + 1, z, G.axis(c.st.log, ax));
        if (r.nextInt(100) < 40) G.put(cv, x + sx, y + 1, z + sz, G.axis(c.st.log, ax));
        return true;
    }

    private static boolean bench(Canvas cv, Ctx c, int x, int y, int z, BlockFace toHouse, int side) {
        boolean alongX = side == 0 || side == 2;
        int sx = alongX ? 1 : 0, sz = alongX ? 0 : 1;
        if (!G.free(cv, x, y, z) || !G.free(cv, x + sx, y, z + sz)) return false;
        cv.set(x, y, z, G.stairs(c.st.stairs, toHouse, false));
        cv.set(x + sx, y, z + sz, G.stairs(c.st.stairs, toHouse, false));
        return true;
    }

    private static boolean pot(Canvas cv, int x, int y, int z, Random r, boolean dry) {
        Material m = dry ? (r.nextBoolean() ? Material.POTTED_DEAD_BUSH : Material.POTTED_CACTUS)
                : POTS[r.nextInt(POTS.length)];
        return G.put(cv, x, y, z, m);
    }

    private static boolean cactus(Canvas cv, Random r, int x, int gy, int z) {
        Material g = cv.type(x, gy, z);
        if (g != Material.SAND && g != Material.RED_SAND) return false;
        for (int[] d : G.D4) {
            Material n = cv.type(x + d[0], gy + 1, z + d[1]);
            if (n != null && !n.isAir() && !Blocks.replaceable(n)) return false;
        }
        int h = 1 + r.nextInt(3);
        boolean any = false;
        for (int k = 1; k <= h; k++) any |= G.put(cv, x, gy + k, z, Material.CACTUS);
        return any;
    }

    private static boolean sandPillar(Canvas cv, int x, int y, int z) {
        if (!G.free(cv, x, y, z) || !G.free(cv, x, y + 1, z) || !G.free(cv, x, y + 2, z)) return false;
        cv.set(x, y, z, Material.CHISELED_SANDSTONE.createBlockData());
        cv.set(x, y + 1, z, Material.CUT_SANDSTONE.createBlockData());
        cv.set(x, y + 2, z, G.slab(Material.SANDSTONE_SLAB, false));
        return true;
    }

    private static boolean snowman(Canvas cv, Random r, int x, int y, int z) {
        for (int k = 0; k < 3; k++) if (!G.free(cv, x, y + k, z)) return false;
        cv.set(x, y, z, Material.SNOW_BLOCK.createBlockData());
        cv.set(x, y + 1, z, Material.SNOW_BLOCK.createBlockData());
        cv.set(x, y + 2, z, G.facing(Material.CARVED_PUMPKIN, G.H[r.nextInt(4)]));
        return true;
    }

    private static boolean lampPost(Canvas cv, Ctx c, int x, int y, int z) {
        return G.lamp(cv, x, y, z, c.st.post, 2);
    }

    // ================================================================== chemins et lampadaires

    /** Passe sur un chunk du village : chemins plus naturels et lampadaires espacés le long des chemins. */
    private static void roads(Canvas cv, Ctx c, int cx, int cz) {
        int x0 = Math.max(c.minX, cx << 4), x1 = Math.min(c.maxX, (cx << 4) + 15);
        int z0 = Math.max(c.minZ, cz << 4), z1 = Math.min(c.maxZ, (cz << 4) + 15);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int y = G.surface(cv, x, z);
                if (y == G.NONE) continue;
                if (cv.type(x, y, z) != Material.DIRT_PATH) continue;
                if (Cfg.pathWear) {
                    double u = G.hash(c.seed, x, 1, z);
                    if (u < 0.07) G.force(cv, x, y, z, Material.COARSE_DIRT);
                    else if (u < 0.13) G.force(cv, x, y, z, Material.GRAVEL);
                }
                for (int[] d : G.D4) {
                    int nx = x + d[0], nz = z + d[1];
                    int ny = G.surface(cv, nx, nz);
                    if (ny == G.NONE || Math.abs(ny - y) > 1) continue;
                    Material nm = cv.type(nx, ny, nz);
                    if (nm == Material.DIRT_PATH || !G.land(nm)) continue;
                    if (Cfg.pathWear && nm == Material.GRASS_BLOCK && G.hash(c.seed, nx, 3, nz) < 0.09) {
                        G.force(cv, nx, ny, nz, Material.COARSE_DIRT);
                        continue;
                    }
                    if (!Cfg.lamps || G.hash(c.seed, nx, 2, nz) >= Cfg.lampDensity) continue;
                    if (inBuilding(c, nx, nz, 2) || c.doorZone.contains(G.pack(nx, nz))) continue;
                    if (tooClose(c, nx, nz)) continue;
                    if (G.lamp(cv, nx, ny + 1, nz, c.st.post, 3)) c.lamps.add(new int[] {nx, nz});
                }
            }
        }
    }

    private static boolean tooClose(Ctx c, int x, int z) {
        for (int[] l : c.lamps) {
            int dx = l[0] - x, dz = l[1] - z;
            if (dx * dx + dz * dz < 49) return true;
        }
        return false;
    }
}

package fr.villages;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Random;

/**
 * Décors autour des structures vanilla : pyramide du désert, maison de sorcière, igloo, temple de la jungle.
 * On ne touche jamais aux salles intérieures (pièges, coffres) : seulement l'extérieur et les alentours.
 */
final class Sites {
    private Sites() {}

    /** Étapes pour une structure vanilla connue, ou liste vide. */
    static List<Runnable> plan(Canvas cv, Struct.Info info, long worldSeed) {
        BoundingBox bb = info.box();
        int[] b = {G.lo(bb.getMinX()), G.hi(bb.getMaxX()), G.lo(bb.getMinY()), G.hi(bb.getMaxY()),
                G.lo(bb.getMinZ()), G.hi(bb.getMaxZ())};
        long seed = G.mix(worldSeed ^ (b[0] * 341873128712L) ^ (b[4] * 132897987541L) ^ info.key().hashCode());
        return switch (info.key()) {
            case "desert_pyramid" -> Cfg.pyramid ? List.of(() -> pyramid(cv, b, seed)) : List.of();
            case "swamp_hut" -> Cfg.witchHut ? List.of(() -> hut(cv, b, seed)) : List.of();
            case "igloo" -> Cfg.igloo ? List.of(() -> igloo(cv, b, seed)) : List.of();
            case "jungle_pyramid" -> Cfg.jungleTemple ? List.of(() -> jungle(cv, b, seed)) : List.of();
            default -> List.of();
        };
    }

    /** Distance (Chebyshev) entre un point et la boîte, 0 si dedans. */
    private static int dist(int[] b, int x, int z) {
        int dx = Math.max(Math.max(b[0] - x, x - b[1]), 0);
        int dz = Math.max(Math.max(b[4] - z, z - b[5]), 0);
        return Math.max(dx, dz);
    }

    private static boolean sky(Canvas cv, int x, int y, int z, int top) {
        for (int yy = y + 1; yy <= top + 3; yy++) {
            Material t = cv.type(x, yy, z);
            if (t == null) return true;
            if (G.solid(t)) return false;
        }
        return true;
    }

    private static void vine(Canvas cv, int x, int y, int z, BlockFace toWall) {
        BlockData d = Material.VINE.createBlockData();
        if (d instanceof MultipleFacing mf) mf.setFace(toWall, true);
        G.put(cv, x, y, z, d);
    }

    private static void bush(Canvas cv, Random r, int x, int y, int z, Material leaves) {
        BlockData d = leaves.createBlockData();
        if (d instanceof Leaves l) l.setPersistent(true);
        if (G.put(cv, x, y, z, d) && r.nextInt(100) < 35) G.put(cv, x, y + 1, z, d);
    }

    // ================================================================== pyramide du désert

    private static void pyramid(Canvas cv, int[] b, long seed) {
        Random r = new Random(seed);
        int m = 7;
        for (int x = b[0] - m; x <= b[1] + m; x++) {
            for (int z = b[4] - m; z <= b[5] + m; z++) {
                int d = dist(b, x, z);
                if (d == 0) continue;
                int y = G.surface(cv, x, z);
                if (y == G.NONE) continue;
                Material g = cv.type(x, y, z);
                if (g != Material.SAND && g != Material.RED_SAND && g != Material.SANDSTONE) continue;
                double u = G.hash(seed, x, 11, z);
                if (d <= 2 && u < 0.10) {
                    G.put(cv, x, y + 1, z, Material.SAND);             // dune contre le mur
                } else if (u < 0.12) {
                    G.put(cv, x, y + 1, z, Material.DEAD_BUSH);
                } else if (u < 0.135) {
                    Material rub = r.nextInt(3) == 0 ? Material.CUT_SANDSTONE : Material.SANDSTONE;
                    if (r.nextInt(3) == 0) G.put(cv, x, y + 1, z, G.stairs(Material.SANDSTONE_STAIRS, G.H[r.nextInt(4)], false));
                    else G.put(cv, x, y + 1, z, rub);
                } else if (u < 0.142 && d >= 3 && g != Material.SANDSTONE) {
                    boolean clear = true;
                    for (int[] dd : G.D4) {
                        Material n = cv.type(x + dd[0], y + 1, z + dd[1]);
                        if (n != null && !n.isAir() && !Blocks.replaceable(n)) clear = false;
                    }
                    if (clear) {
                        int h = 1 + r.nextInt(3);
                        for (int k = 1; k <= h; k++) G.put(cv, x, y + k, z, Material.CACTUS);
                    }
                }
            }
        }
        // quatre obélisques aux coins, certains brisés
        int[][] corners = {{b[0] - 3, b[4] - 3}, {b[1] + 3, b[4] - 3}, {b[0] - 3, b[5] + 3}, {b[1] + 3, b[5] + 3}};
        for (int[] c : corners) {
            int y = G.surface(cv, c[0], c[1]);
            if (y == G.NONE) continue;
            Material g = cv.type(c[0], y, c[1]);
            if (g != Material.SAND && g != Material.RED_SAND && g != Material.SANDSTONE) continue;
            G.pillar(cv, r, c[0], y + 1, c[1], Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE,
                    Material.CHISELED_SANDSTONE, Material.SANDSTONE_SLAB, 5 + r.nextInt(3), r.nextInt(100) < 35);
        }
    }

    // ================================================================== maison de sorcière

    private static void hut(Canvas cv, int[] b, long seed) {
        Random r = new Random(seed);
        int cx = (b[0] + b[1]) / 2, cz = (b[4] + b[5]) / 2;
        int floorY = G.NONE;
        for (int y = b[2]; y <= b[3]; y++) {
            Material t = cv.type(cx, y, cz);
            if (t != null && !t.isAir() && !Blocks.liquid(t)) {
                floorY = y;
                break;
            }
        }
        // alentours : nénuphars sur l'eau, champignons sur la terre
        for (int x = b[0] - 6; x <= b[1] + 6; x++) {
            for (int z = b[4] - 6; z <= b[5] + 6; z++) {
                if (dist(b, x, z) == 0) continue;
                int y = G.surface(cv, x, z);
                if (y == G.NONE) continue;
                Material g = cv.type(x, y, z);
                double u = G.hash(seed, x, 12, z);
                if (g == Material.WATER) {
                    if (u < 0.10) G.put(cv, x, y + 1, z, Material.LILY_PAD);
                } else if (G.land(g)) {
                    if (u < 0.025) G.put(cv, x, y + 1, z, Material.RED_MUSHROOM);
                    else if (u < 0.05) G.put(cv, x, y + 1, z, Material.BROWN_MUSHROOM);
                    else if (u < 0.07) G.put(cv, x, y + 1, z, Material.FERN);
                }
            }
        }
        if (floorY == G.NONE) return;

        // porte : on construit le ponton devant
        int face = -1, dxr = 0, dzr = 0;
        for (int x = b[0]; x <= b[1] && face < 0; x++) {
            for (int z = b[4]; z <= b[5] && face < 0; z++) {
                for (int y = floorY; y <= floorY + 3; y++) {
                    Material t = cv.type(x, y, z);
                    if (t == null || !t.name().endsWith("_DOOR")) continue;
                    BlockData dd = cv.data(x, y, z);
                    if (!(dd instanceof Door door) || door.getHalf() != Bisected.Half.BOTTOM) continue;
                    int[] dist = {z - b[4], b[1] - x, b[5] - z, x - b[0]};
                    int f = 0;
                    for (int i = 1; i < 4; i++) if (dist[i] < dist[f]) f = i;
                    if (dist[f] > 1) continue;
                    face = f;
                    dxr = x;
                    dzr = z;
                    break;
                }
            }
        }
        if (face < 0) {
            // pas de porte trouvée : on prend le côté le plus court jusqu'à la terre ferme
            int best = 99;
            for (int f = 0; f < 4; f++) {
                int sx = f == 1 ? b[1] : f == 3 ? b[0] : cx;
                int sz = f == 0 ? b[4] : f == 2 ? b[5] : cz;
                int n = wetRun(cv, sx, sz, f, floorY);
                if (n < best) {
                    best = n;
                    face = f;
                    dxr = sx;
                    dzr = sz;
                }
            }
        }
        boardwalk(cv, r, dxr, dzr, face, floorY);
    }

    /** Nombre de pas dans l'eau avant la terre ferme (99 si trop loin). */
    private static int wetRun(Canvas cv, int sx, int sz, int face, int floorY) {
        for (int i = 1; i <= 16; i++) {
            int x = sx + G.D4[face][0] * i, z = sz + G.D4[face][1] * i;
            int y = G.surface(cv, x, z);
            if (y == G.NONE) return 99;
            Material g = cv.type(x, y, z);
            if (G.land(g) && y >= floorY - 3) return i;
        }
        return 99;
    }

    /** Ponton en planches de spruce, avec rambardes, poteaux et lanternes. */
    private static void boardwalk(Canvas cv, Random r, int sx, int sz, int face, int floorY) {
        int run = wetRun(cv, sx, sz, face, floorY);
        int len = run == 99 ? 6 : Math.min(run + 1, 16);
        int dx = G.D4[face][0], dz = G.D4[face][1];
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0; // direction latérale
        for (int i = 1; i <= len; i++) {
            int x = sx + dx * i, z = sz + dz * i;
            Material here = cv.type(x, floorY, z);
            if (here == null) break;
            G.force(cv, x, floorY, z, Material.SPRUCE_PLANKS);
            // poteaux de soutien tous les 3 pas
            if (i % 3 == 1) {
                for (int k = 1; k <= 8; k++) {
                    Material u = cv.type(x, floorY - k, z);
                    if (u == null || !(u.isAir() || Blocks.liquid(u) || Blocks.replaceable(u))) break;
                    cv.set(x, floorY - k, z, Material.SPRUCE_LOG.createBlockData());
                }
            }
            if (i >= 2) {
                for (int s = -1; s <= 1; s += 2) {
                    int rx = x + px * s, rz = z + pz * s;
                    G.put(cv, rx, floorY, rz, Material.SPRUCE_FENCE);
                    G.put(cv, rx, floorY + 1, rz, Material.SPRUCE_FENCE);
                    if (i % 4 == 2 && s == (r.nextBoolean() ? 1 : -1)) G.put(cv, rx, floorY + 2, rz, Material.LANTERN);
                }
            }
        }
        // à l'entrée du ponton : tonneau et marmite
        int bx = sx + dx + px * 2, bz = sz + dz + pz * 2;
        if (cv.type(bx, floorY - 1, bz) != null && G.solid(cv.type(bx, floorY - 1, bz))) {
            G.put(cv, bx, floorY, bz, G.facing(Material.BARREL, BlockFace.UP));
        }
    }

    // ================================================================== igloo

    private static void igloo(Canvas cv, int[] b, long seed) {
        Random r = new Random(seed);
        for (int x = b[0] - 6; x <= b[1] + 6; x++) {
            for (int z = b[4] - 6; z <= b[5] + 6; z++) {
                int d = dist(b, x, z);
                if (d == 0) continue;
                int y = G.surface(cv, x, z);
                if (y == G.NONE) continue;
                Material g = cv.type(x, y, z);
                if (!G.land(g)) continue;
                double u = G.hash(seed, x, 13, z);
                if (d <= 4 && u < 0.18) G.put(cv, x, y + 1, z, G.snow(1 + (int) (u * 20) % 3)); // congères
                else if (u > 0.985) G.put(cv, x, y + 1, z, Material.PACKED_ICE);
            }
        }
        // deux lampadaires de chaque côté
        placeLamp(cv, b[0] - 2, b[4] - 2);
        placeLamp(cv, b[1] + 2, b[5] + 2);
        // feu de camp avec deux souches pour s'asseoir
        int side = r.nextInt(4);
        int[] c = ringPoint(b, side, 4, r);
        int y = G.surface(cv, c[0], c[1]);
        if (y != G.NONE && G.land(cv.type(c[0], y, c[1])) && G.free(cv, c[0], y + 1, c[1])) {
            cv.set(c[0], y + 1, c[1], Material.CAMPFIRE.createBlockData());
            int[][] seats = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int n = 0;
            for (int[] s : seats) {
                if (n >= 2) break;
                int sy = G.surface(cv, c[0] + s[0], c[1] + s[1]);
                if (sy == G.NONE || !G.land(cv.type(c[0] + s[0], sy, c[1] + s[1]))) continue;
                if (G.put(cv, c[0] + s[0], sy + 1, c[1] + s[1], G.axis(Material.SPRUCE_LOG, Axis.Y))) n++;
            }
        }
        // bonhomme de neige de l'autre côté
        int[] s = ringPoint(b, (side + 2) % 4, 3, r);
        int sy = G.surface(cv, s[0], s[1]);
        if (sy != G.NONE && G.land(cv.type(s[0], sy, s[1]))) {
            boolean free = true;
            for (int k = 1; k <= 3; k++) free &= G.free(cv, s[0], sy + k, s[1]);
            if (free) {
                cv.set(s[0], sy + 1, s[1], Material.SNOW_BLOCK.createBlockData());
                cv.set(s[0], sy + 2, s[1], Material.SNOW_BLOCK.createBlockData());
                cv.set(s[0], sy + 3, s[1], G.facing(Material.CARVED_PUMPKIN, G.H[r.nextInt(4)]));
            }
        }
        // pile de bois près d'un côté
        int[] w = ringPoint(b, (side + 1) % 4, 2, r);
        int wy = G.surface(cv, w[0], w[1]);
        if (wy != G.NONE && G.land(cv.type(w[0], wy, w[1]))
                && G.free(cv, w[0], wy + 1, w[1]) && G.free(cv, w[0] + 1, wy + 1, w[1])) {
            cv.set(w[0], wy + 1, w[1], G.axis(Material.SPRUCE_LOG, Axis.X));
            cv.set(w[0] + 1, wy + 1, w[1], G.axis(Material.SPRUCE_LOG, Axis.X));
            G.put(cv, w[0], wy + 2, w[1], G.axis(Material.SPRUCE_LOG, Axis.X));
        }
    }

    private static void placeLamp(Canvas cv, int x, int z) {
        int y = G.surface(cv, x, z);
        if (y == G.NONE || !G.land(cv.type(x, y, z))) return;
        G.lamp(cv, x, y + 1, z, Material.SPRUCE_FENCE, 2);
    }

    /** Un point à distance d du bord de la boîte, côté 0 nord, 1 est, 2 sud, 3 ouest. */
    private static int[] ringPoint(int[] b, int side, int d, Random r) {
        return switch (side) {
            case 0 -> new int[] {b[0] + r.nextInt(b[1] - b[0] + 1), b[4] - d};
            case 1 -> new int[] {b[1] + d, b[4] + r.nextInt(b[5] - b[4] + 1)};
            case 2 -> new int[] {b[0] + r.nextInt(b[1] - b[0] + 1), b[5] + d};
            default -> new int[] {b[0] - d, b[4] + r.nextInt(b[5] - b[4] + 1)};
        };
    }

    // ================================================================== temple de la jungle

    private static void jungle(Canvas cv, int[] b, long seed) {
        Random r = new Random(seed);
        Material carpet = G.mat("MOSS_CARPET", Material.AIR);
        // lianes, mousse et pierre moussue sur tout ce qui est visible de l'extérieur
        for (int x = b[0]; x <= b[1]; x++) {
            for (int y = b[2]; y <= b[3]; y++) {
                for (int z = b[4]; z <= b[5]; z++) {
                    Material m = cv.type(x, y, z);
                    if (m != Material.COBBLESTONE && m != Material.MOSSY_COBBLESTONE
                            && m != Material.COBBLESTONE_STAIRS) continue;
                    boolean mossy = m == Material.MOSSY_COBBLESTONE;
                    // dessus
                    Material up = cv.type(x, y + 1, z);
                    if (up != null && up.isAir() && sky(cv, x, y + 1, z, b[3])) {
                        double u = G.hash(seed, x, y, z);
                        if (m == Material.COBBLESTONE && u < 0.30) G.force(cv, x, y, z, Material.MOSSY_COBBLESTONE);
                        else if (u > 0.88 && carpet != Material.AIR) G.put(cv, x, y + 1, z, carpet);
                    }
                    // côtés : lianes qui retombent
                    for (int i = 0; i < 4; i++) {
                        int nx = x + G.D4[i][0], nz = z + G.D4[i][1];
                        Material n = cv.type(nx, y, nz);
                        if (n == null || !n.isAir() || !sky(cv, nx, y, nz, b[3])) continue;
                        double u = G.hash(seed, nx * 7 + i, y, nz);
                        if (u >= (mossy ? 0.34 : 0.16)) continue;
                        BlockFace toWall = G.H[i].getOppositeFace();
                        vine(cv, nx, y, nz, toWall);
                        for (int k = 1; k <= 3; k++) {
                            Material wall = cv.type(x, y - k, z);
                            if (wall == null || !G.solid(wall) || !G.free(cv, nx, y - k, nz)) break;
                            if (G.hash(seed, nx + k, y - k, nz + i) > 0.62) break;
                            vine(cv, nx, y - k, nz, toWall);
                        }
                    }
                }
            }
        }
        // alentours : buissons, fougères, gravats mousseux
        for (int x = b[0] - 6; x <= b[1] + 6; x++) {
            for (int z = b[4] - 6; z <= b[5] + 6; z++) {
                if (dist(b, x, z) == 0) continue;
                int y = G.surface(cv, x, z);
                if (y == G.NONE) continue;
                Material g = cv.type(x, y, z);
                if (g != Material.GRASS_BLOCK && g != Material.DIRT && g != Material.PODZOL
                        && g != Material.COARSE_DIRT && g != Material.MOSS_BLOCK) continue;
                double u = G.hash(seed, x, 14, z);
                if (u < 0.035) bush(cv, r, x, y + 1, z, Material.JUNGLE_LEAVES);
                else if (u < 0.08) G.put(cv, x, y + 1, z, Material.FERN);
                else if (u < 0.095) G.put(cv, x, y + 1, z, Material.MOSSY_COBBLESTONE);
                else if (u < 0.11 && carpet != Material.AIR) G.put(cv, x, y + 1, z, carpet);
            }
        }
        // piliers de pierre moussue aux coins, plus ou moins brisés
        int[][] corners = {{b[0] - 3, b[4] - 3}, {b[1] + 3, b[4] - 3}, {b[0] - 3, b[5] + 3}, {b[1] + 3, b[5] + 3}};
        for (int[] c : corners) {
            int y = G.surface(cv, c[0], c[1]);
            if (y == G.NONE || !Blocks.ground(cv.type(c[0], y, c[1]))) continue;
            G.pillar(cv, r, c[0], y + 1, c[1], Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                    Material.CHISELED_STONE_BRICKS, Material.MOSSY_STONE_BRICK_SLAB, 4 + r.nextInt(3),
                    r.nextInt(100) < 60);
        }
    }
}

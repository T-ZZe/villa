package fr.villages;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Surface sur laquelle on construit : le monde déjà chargé (chunks chargés uniquement). */
interface Canvas {
    /** Type du bloc, ou null si on ne peut pas y toucher (hors zone / chunk non chargé). */
    Material type(int x, int y, int z);

    void set(int x, int y, int z, BlockData d);

    /** Hauteur à partir de laquelle on cherche le sol en descendant. */
    int topY(int x, int z);

    int minY();

    int maxY();

    String biome(int x, int y, int z);

    /** Données du bloc (axe d'un tronc, distance d'une feuille...), ou null. */
    BlockData data(int x, int y, int z);
}

final class WorldCanvas implements Canvas {
    private final World w;

    WorldCanvas(World w) {
        this.w = w;
    }

    private boolean ok(int x, int y, int z) {
        return y >= w.getMinHeight() && y < w.getMaxHeight() && w.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override
    public Material type(int x, int y, int z) {
        if (!ok(x, y, z)) return null;
        return w.getBlockAt(x, y, z).getType();
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        if (!ok(x, y, z)) return;
        w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    @Override
    public int topY(int x, int z) {
        return w.getHighestBlockYAt(x, z);
    }

    @Override
    public int minY() {
        return w.getMinHeight();
    }

    @Override
    public int maxY() {
        return w.getMaxHeight();
    }

    @Override
    public BlockData data(int x, int y, int z) {
        if (!ok(x, y, z)) return null;
        return w.getBlockAt(x, y, z).getBlockData();
    }

    @Override
    public String biome(int x, int y, int z) {
        try {
            return w.getBiome(x, y, z).getKey().getKey();
        } catch (Exception e) {
            return "";
        }
    }
}

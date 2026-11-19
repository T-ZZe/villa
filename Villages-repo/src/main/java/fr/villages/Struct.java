package fr.villages;

import org.bukkit.Chunk;
import org.bukkit.Keyed;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lit les structures générées d'un chunk (API Paper "GeneratedStructure").
 * Passe par la réflexion : si l'API change de nom, le plugin le dit au démarrage au lieu de planter.
 */
final class Struct {
    private Struct() {}

    /** Une structure : son type ("village_plains", "igloo"...), sa boîte, et les boîtes de ses pièces. */
    record Info(String key, BoundingBox box, List<BoundingBox> pieces) {}

    static boolean ok = false;
    static String error = "";
    private static Method mChunk, mStructure, mBox, mPieces, mPieceBox;

    static {
        try {
            mChunk = Chunk.class.getMethod("getStructures");
            Class<?> gs = Class.forName("org.bukkit.generator.structure.GeneratedStructure");
            Class<?> sp = Class.forName("org.bukkit.generator.structure.StructurePiece");
            mStructure = gs.getMethod("getStructure");
            mBox = gs.getMethod("getBoundingBox");
            mPieces = gs.getMethod("getPieces");
            mPieceBox = sp.getMethod("getBoundingBox");
            ok = true;
        } catch (Throwable t) {
            error = String.valueOf(t);
        }
    }

    static List<Info> in(Chunk c) {
        List<Info> out = new ArrayList<>();
        if (!ok) return out;
        try {
            Object res = mChunk.invoke(c);
            if (!(res instanceof Collection<?> col)) return out;
            for (Object gs : col) {
                Object st = mStructure.invoke(gs);
                String key = st instanceof Keyed k ? k.getKey().getKey() : String.valueOf(st);
                BoundingBox box = (BoundingBox) mBox.invoke(gs);
                List<BoundingBox> pieces = new ArrayList<>();
                Object ps = mPieces.invoke(gs);
                if (ps instanceof Collection<?> pc) {
                    for (Object p : pc) pieces.add((BoundingBox) mPieceBox.invoke(p));
                }
                out.add(new Info(key, box, pieces));
            }
        } catch (Throwable t) {
            ok = false;
            error = String.valueOf(t);
        }
        return out;
    }
}

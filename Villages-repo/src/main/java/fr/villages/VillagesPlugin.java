package fr.villages;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Villages : améliore les villages (5 biomes), la pyramide, la maison de sorcière, l'igloo et le temple de la jungle,
 * et ajoute une tour de guet en bois (taïga, très rare) et une maison champignon (île champignon).
 * Compatible avec le plugin Better Tree : mêmes matériaux, même esprit, aucun changement de biome.
 */
public class VillagesPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    /** Une structure à améliorer : on charge ses chunks, puis on exécute ses étapes tick après tick. */
    private static final class Task {
        final World w;
        final Struct.Info info;
        final String id;
        final AtomicInteger pending = new AtomicInteger();
        final List<int[]> tickets = new ArrayList<>();
        ArrayDeque<Runnable> steps = new ArrayDeque<>();
        int state = 0; // 0 = chargement des chunks, 1 = construction
        int waited = 0;

        Task(World w, Struct.Info info, String id) {
            this.w = w;
            this.info = info;
            this.id = id;
        }
    }

    private final Set<String> done = new HashSet<>();     // structures déjà traitées (sauvegardé)
    private final Set<String> queued = new HashSet<>();   // en attente ou en cours (mémoire)
    private final ArrayDeque<Task> queue = new ArrayDeque<>();
    private Task cur = null;
    private boolean warned = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Cfg.load(getConfig());
        loadDone();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("villages") != null) {
            getCommand("villages").setExecutor(this);
            getCommand("villages").setTabCompleter(this);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    tick();
                } catch (Throwable t) {
                    warn("Erreur : " + t);
                }
            }
        }.runTaskTimer(this, 40L, 2L);
        if (!Struct.ok) {
            getLogger().warning("API des structures introuvable (" + Struct.error + ") : les villages et structures "
                    + "ne seront pas améliorés. La tour de taïga et la maison champignon fonctionnent quand même.");
        }
        getLogger().info("Villages actif : villages, pyramides, huttes, igloos, temples, tour de taïga, maison champignon.");
    }

    @Override
    public void onDisable() {
        if (cur != null) release(cur);
        cur = null;
        queue.clear();
        queued.clear();
    }

    private void warn(String msg) {
        if (warned) return;
        warned = true;
        getLogger().warning(msg);
    }

    // ---------------------------------------------------------------- mémoire des structures traitées

    private File doneFile() {
        return new File(getDataFolder(), "processed.txt");
    }

    private void loadDone() {
        File f = doneFile();
        if (!f.exists()) return;
        try {
            done.addAll(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            getLogger().warning("Lecture de processed.txt impossible : " + ex);
        }
    }

    private void markDone(String id) {
        if (!done.add(id)) return;
        try {
            getDataFolder().mkdirs();
            Files.writeString(doneFile().toPath(), id + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            warn("Sauvegarde de processed.txt impossible : " + ex);
        }
    }

    // ---------------------------------------------------------------- détection

    private boolean allowed(World w) {
        if (w.getEnvironment() != World.Environment.NORMAL) return false;
        Set<String> names = Cfg.worlds;
        return names.isEmpty() || names.contains(w.getName());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        boolean isNew = e.isNewChunk();
        if (!isNew && !Cfg.existing) return;
        World w = e.getWorld();
        if (!allowed(w)) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        // on attend le tick suivant : on ne touche pas aux blocs pendant le chargement du chunk
        Bukkit.getScheduler().runTask(this, () -> inspect(w, cx, cz, isNew));
    }

    /** Cherche les structures du chunk (à améliorer) et, pour un chunk neuf, place la tour ou la maison champignon. */
    private void inspect(World w, int cx, int cz, boolean standalone) {
        if (!w.isChunkLoaded(cx, cz) || !allowed(w)) return;
        Chunk c = w.getChunkAt(cx, cz);
        for (Struct.Info info : Struct.in(c)) enqueue(w, info);
        if (!standalone) return;
        long seed = w.getSeed();
        try {
            if (Cfg.tower) {
                int[] t = TowerGen.chosen(seed, cx, cz, Cfg.towerChance);
                String id = "tower;" + w.getName() + ";" + cx + ";" + cz;
                if (t != null && !done.contains(id) && TowerGen.build(w, t[0], t[1], seed, Cfg.towerChest)) {
                    markDone(id);
                    getLogger().info("Tour de guet en bois créée en " + w.getName() + " (" + t[0] + ", " + t[1] + ").");
                }
            }
            if (Cfg.house) {
                int[] t = HouseGen.chosen(seed, cx, cz, Cfg.houseChance);
                String id = "house;" + w.getName() + ";" + cx + ";" + cz;
                if (t != null && !done.contains(id) && HouseGen.build(w, t[0], t[1], seed, Cfg.houseChest)) {
                    markDone(id);
                    getLogger().info("Maison champignon créée en " + w.getName() + " (" + t[0] + ", " + t[1] + ").");
                }
            }
        } catch (Throwable ex) {
            warn("Erreur pendant la construction : " + ex);
        }
    }

    private static boolean enabled(String key) {
        if (key.startsWith("village_")) return Cfg.villages;
        return switch (key) {
            case "desert_pyramid" -> Cfg.pyramid;
            case "swamp_hut" -> Cfg.witchHut;
            case "igloo" -> Cfg.igloo;
            case "jungle_pyramid" -> Cfg.jungleTemple;
            default -> false;
        };
    }

    private void enqueue(World w, Struct.Info info) {
        String key = info.key();
        if (!enabled(key)) return;
        String id = w.getName() + ";" + key + ";" + G.lo(info.box().getMinX()) + ";" + G.lo(info.box().getMinZ());
        if (done.contains(id) || !queued.add(id)) return;
        queue.add(new Task(w, info, id));
    }

    // ---------------------------------------------------------------- traitement, un peu à chaque tick

    private void tick() {
        if (cur == null) {
            cur = queue.poll();
            if (cur == null) return;
            if (!startLoad(cur)) finish(cur, false);
            return;
        }
        Task t = cur;
        if (t.state == 0) {
            t.waited += 2;
            if (t.pending.get() > 0) {
                if (t.waited > 2400) finish(t, false); // 2 minutes : on abandonne, ce sera retenté plus tard
                return;
            }
            try {
                Canvas cv = new WorldCanvas(t.w);
                List<Runnable> plan = t.info.key().startsWith("village_")
                        ? VillageGen.plan(cv, t.info, t.w.getSeed())
                        : Sites.plan(cv, t.info, t.w.getSeed());
                t.steps = new ArrayDeque<>(plan);
            } catch (Throwable ex) {
                warn("Plan impossible pour " + t.id + " : " + ex);
                finish(t, true);
                return;
            }
            t.state = 1;
            return;
        }
        long end = System.nanoTime() + 8_000_000L; // 8 ms par tick au maximum
        while (!t.steps.isEmpty() && System.nanoTime() < end) {
            Runnable s = t.steps.poll();
            try {
                s.run();
            } catch (Throwable ex) {
                warn("Erreur pendant l'amélioration de " + t.id + " : " + ex);
            }
        }
        if (t.steps.isEmpty()) finish(t, true);
    }

    /** Demande le chargement (et la génération) des chunks de la structure, avec une marge de 8 blocs. */
    private boolean startLoad(Task t) {
        int x0 = (G.lo(t.info.box().getMinX()) - 8) >> 4, x1 = (G.hi(t.info.box().getMaxX()) + 8) >> 4;
        int z0 = (G.lo(t.info.box().getMinZ()) - 8) >> 4, z1 = (G.hi(t.info.box().getMaxZ()) + 8) >> 4;
        int count = (x1 - x0 + 1) * (z1 - z0 + 1);
        if (count > 500) return false;
        t.pending.set(count);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                t.w.addPluginChunkTicket(x, z, this);
                t.tickets.add(new int[] {x, z});
                t.w.getChunkAtAsync(x, z, true).whenComplete((ch, ex) -> t.pending.decrementAndGet());
            }
        }
        return true;
    }

    private void release(Task t) {
        for (int[] c : t.tickets) t.w.removePluginChunkTicket(c[0], c[1], this);
        t.tickets.clear();
    }

    private void finish(Task t, boolean ok) {
        release(t);
        queued.remove(t.id);
        if (ok) markDone(t.id);
        cur = null;
    }

    // ---------------------------------------------------------------- commande

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("villages.admin")) {
            sender.sendMessage("§cCette commande est réservée aux opérateurs.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                reloadConfig();
                Cfg.load(getConfig());
                sender.sendMessage("§aConfiguration rechargée.");
            }
            case "stats" -> sender.sendMessage("§aStructures améliorées : §f" + done.size() + "§a, en attente : §f"
                    + (queue.size() + (cur != null ? 1 : 0)) + "§a. API des structures : §f" + (Struct.ok ? "ok" : "absente"));
            case "convert" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cCommande réservée aux joueurs.");
                    return true;
                }
                int radius = 8;
                if (args.length >= 2) {
                    try {
                        radius = Math.max(1, Math.min(20, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ex) {
                        p.sendMessage("§cRayon invalide (1 à 20 chunks).");
                        return true;
                    }
                }
                World w = p.getWorld();
                if (!allowed(w)) {
                    p.sendMessage("§cCe monde n'est pas concerné.");
                    return true;
                }
                int pcx = p.getLocation().getBlockX() >> 4, pcz = p.getLocation().getBlockZ() >> 4;
                int before = queue.size();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) inspect(w, pcx + dx, pcz + dz, false);
                }
                p.sendMessage("§a" + (queue.size() - before) + " structure(s) à améliorer trouvée(s) dans un rayon de "
                        + radius + " chunks. Le travail se fait petit à petit en arrière-plan.");
            }
            default -> sender.sendMessage("§c/villages convert [rayon] §7| §c/villages reload §7| §c/villages stats");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("villages.admin") || args.length != 1) return out;
        for (String s : List.of("convert", "reload", "stats")) {
            if (s.startsWith(args[0].toLowerCase())) out.add(s);
        }
        return out;
    }
}

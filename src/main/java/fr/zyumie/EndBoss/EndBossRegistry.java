package fr.zyumie.EndBoss;

import fr.zyumie.EndBoss.IA.EndBossBrain;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remplace les scans de toutes les entités de tous les mondes : on garde un cerveau par boss.
 * Mémorise aussi dans state.yml si le boss est en vie, pour que le portail de l'End reste
 * verrouillé même si le chunk du boss est déchargé ou après un redémarrage.
 */
public final class EndBossRegistry {

    private static final Map<UUID, EndBossBrain> BRAINS = new ConcurrentHashMap<>();
    private static NamespacedKey key;
    private static boolean bossAlive = false;

    private EndBossRegistry() {}

    public static void init(JavaPlugin plugin) {
        key = new NamespacedKey(plugin, "is_endboss");
        File f = new File(plugin.getDataFolder(), "state.yml");
        bossAlive = YamlConfiguration.loadConfiguration(f).getBoolean("boss-alive", false);
    }

    public static NamespacedKey key() {
        return key;
    }

    public static boolean hasTag(Entity e) {
        return e != null && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static EndBossBrain register(JavaPlugin plugin, WitherSkeleton boss) {
        return BRAINS.computeIfAbsent(boss.getUniqueId(), id -> new EndBossBrain(plugin, boss));
    }

    public static EndBossBrain get(Entity e) {
        return e == null ? null : BRAINS.get(e.getUniqueId());
    }

    public static boolean isBoss(Entity e) {
        return e != null && BRAINS.containsKey(e.getUniqueId());
    }

    public static Collection<EndBossBrain> all() {
        return new ArrayList<>(BRAINS.values());
    }

    public static void unregister(UUID id) {
        EndBossBrain brain = BRAINS.remove(id);
        if (brain != null) brain.shutdown();
    }

    public static void shutdownAll() {
        for (UUID id : new ArrayList<>(BRAINS.keySet())) unregister(id);
    }

    /** Reprend en main les boss déjà chargés (reload du plugin). */
    public static void scan(JavaPlugin plugin) {
        for (World w : Bukkit.getWorlds()) {
            for (WitherSkeleton ws : w.getEntitiesByClass(WitherSkeleton.class)) {
                if (hasTag(ws) && ws.isValid() && !ws.isDead()) register(plugin, ws);
            }
        }
    }

    public static boolean isBossAlive() {
        return bossAlive || !BRAINS.isEmpty();
    }

    public static void setBossAlive(JavaPlugin plugin, boolean alive) {
        bossAlive = alive;
        YamlConfiguration y = new YamlConfiguration();
        y.set("boss-alive", alive);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "state.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder state.yml : " + ex.getMessage());
        }
    }
}

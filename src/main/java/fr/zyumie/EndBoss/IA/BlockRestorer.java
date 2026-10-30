package fr.zyumie.EndBoss.IA;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tout bloc posé ou cassé par le boss passe par ici : l'état d'origine est mémorisé
 * et restauré automatiquement. Le terrain n'est donc jamais détruit définitivement
 * et aucun item n'est dupliqué (les blocs cassés ne dropent rien).
 */
public final class BlockRestorer {

    private record Entry(BlockData original, Material placed, long restoreAt) {}

    private static final Map<Location, Entry> ENTRIES = new LinkedHashMap<>();
    private static JavaPlugin plugin;
    private static Set<Material> protectedMaterials = EnumSet.noneOf(Material.class);

    private BlockRestorer() {}

    public static void init(JavaPlugin p) {
        plugin = p;
        reload();
    }

    public static void reload() {
        protectedMaterials = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("boss.griefing.protected-materials")) {
            Material m = Material.matchMaterial(name);
            if (m != null) protectedMaterials.add(m);
        }
    }

    /** Un bloc est protégé s'il est incassable, liquide, un conteneur/tile entity ou listé dans la config. */
    public static boolean isProtected(Block b) {
        Material m = b.getType();
        if (m.isAir() || m.getHardness() < 0 || b.isLiquid() || protectedMaterials.contains(m)) return true;
        return b.getState(false) instanceof TileState;
    }

    /** Remplace le bloc par {@code newType} et programme sa restauration. */
    public static void apply(Block b, Material newType, int ttlTicks) {
        Location key = b.getLocation();
        Entry old = ENTRIES.get(key);
        BlockData original = old != null ? old.original() : b.getBlockData();
        b.setType(newType, false);
        ENTRIES.put(key, new Entry(original, newType, System.currentTimeMillis() + ttlTicks * 50L));
    }

    public static void tick() {
        if (ENTRIES.isEmpty()) return;
        long now = System.currentTimeMillis();
        int budget = 200;
        Iterator<Map.Entry<Location, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext() && budget-- > 0) {
            Map.Entry<Location, Entry> me = it.next();
            Entry e = me.getValue();
            if (e.restoreAt() > now) continue;
            if (restore(me.getKey(), e, false)) {
                it.remove();
            } else {
                me.setValue(new Entry(e.original(), e.placed(), now + 2000L)); // quelqu'un est dedans : on réessaie
            }
        }
    }

    public static void restoreAll() {
        for (Map.Entry<Location, Entry> me : ENTRIES.entrySet()) {
            restore(me.getKey(), me.getValue(), true);
        }
        ENTRIES.clear();
    }

    private static boolean restore(Location loc, Entry e, boolean force) {
        if (loc.getWorld() == null) return true;
        Block b = loc.getBlock();
        if (b.getType() != e.placed()) return true; // un joueur a modifié le bloc entre-temps : on n'y touche pas

        if (!force && !e.original().getMaterial().isAir()) {
            Collection<Entity> inside = b.getWorld().getNearbyEntities(BoundingBox.of(b), en -> en instanceof LivingEntity);
            if (!inside.isEmpty()) return false; // on n'emmure personne
        }
        b.setBlockData(e.original(), false);
        b.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.01);
        return true;
    }
}

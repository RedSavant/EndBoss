package fr.zyumie.EndBoss.IA;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Exécution des capacités. Toutes les attaques lourdes sont TÉLÉGRAPHIÉES (anneaux de particules,
 * message d'action bar) : le boss est difficile mais lisible, un joueur attentif peut toujours réagir.
 */
public class EndBossCombat {

    private final EndBossBrain brain;
    private final WitherSkeleton boss;
    private final JavaPlugin plugin;
    private final Random rng = new Random();

    public EndBossCombat(EndBossBrain brain) {
        this.brain = brain;
        this.boss = brain.boss;
        this.plugin = brain.plugin;
    }

    public void execute(Ability a, Player t) {
        if (t == null || !brain.isAlive()) return;
        switch (a) {
            case LEAP -> leap(t);
            case DASH_STRIKE -> dashStrike(t, false);
            case SWORD_THROW -> swordThrow(t);
            case SHIELD_UP -> shieldUp();
            case BLINK -> blink(t);
            case MACE_SMASH -> maceSmash(t);
            case SHOCKWAVE -> shockwave();
            case SUMMON -> summon(t);
            case METEORS -> meteors(t);
        }
    }

    /** Contre-attaque rapide après une garde réussie. */
    public void counterStrike(Player t) {
        dashStrike(t, true);
    }

    // ------------------------------------------------------------------ utilitaires

    private double dmg(double base) {
        return base * brain.phases().damageMultiplier() * brain.cfg().getDouble("boss.ability-damage", 1.0);
    }

    private void hurt(Player p, double amount) {
        if (p.isValid() && !p.isDead()) p.damage(amount, boss);
    }

    private void knock(Player p, Location from, double power) {
        Vector push = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() < 0.0001) push = new Vector(0, 0, 1);
        push.normalize().multiply(power).setY(0.35 + power * 0.2);
        p.setVelocity(push);
    }

    /** Sol sous une position (télégraphes d'attaque). */
    private Location ground(Location loc) {
        Location g = loc.clone();
        for (int i = 0; i < 10 && g.getBlock().isPassable(); i++) g.subtract(0, 1, 0);
        g.setY(g.getBlockY() + 1.05);
        return g;
    }

    private void ring(Location c, double radius, Color color, float size, int points) {
        World w = c.getWorld();
        Particle.DustOptions opt = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double ang = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(ang) * radius, c.getY() + 0.1,
                    c.getZ() + Math.sin(ang) * radius, 1, 0, 0, 0, 0, opt);
        }
    }

    /** Dégâts de zone avec atténuation selon la distance. Les boucliers des joueurs fonctionnent normalement. */
    private void impact(Location c, double radius, double baseDamage, double knockback) {
        for (Player p : c.getWorld().getNearbyPlayers(c, radius)) {
            if (!brain.isValidTarget(p)) continue;
            double d = p.getLocation().distance(c);
            double falloff = 1.0 - (d / radius) * 0.5;
            hurt(p, dmg(baseDamage) * falloff);
            knock(p, c, knockback);
        }
    }

    private BlockData crackData(Location loc) {
        Block under = loc.clone().subtract(0, 1, 0).getBlock();
        return under.getType().isAir() ? Material.STONE.createBlockData() : under.getBlockData();
    }

    private void smashFx(Location loc) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);
        w.spawnParticle(Particle.BLOCK, loc, 60, 2.5, 0.2, 2.5, 0.1, crackData(loc));
        w.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND, 3.0f, 0.6f);
        w.playSound(loc, Sound.ENTITY_GENERIC_BIG_FALL, 2.5f, 0.4f);
    }

    /** Exécute {@code action} quand le boss a touché le sol (ou après maxTicks). */
    private void afterLanding(int minTicks, int maxTicks, Runnable action) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive()) {
                    cancel();
                    return;
                }
                if (ticks >= minTicks && (boss.isOnGround() || ticks >= maxTicks)) {
                    cancel();
                    action.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ capacités

    /** Bond vers la position PRÉDITE de la cible : rattrape un joueur qui kite. */
    private void leap(Player t) {
        brain.busy(18);
        brain.startCooldown(Ability.LEAP);
        Location from = boss.getLocation();
        Vector d = brain.predict(t, 10).toVector().subtract(from.toVector());
        double h = Math.hypot(d.getX(), d.getZ());
        if (h < 0.5) return;

        Vector v = new Vector(d.getX() / h, 0, d.getZ() / h).multiply(Math.min(1.6, 0.25 + h * 0.085));
        v.setY(0.62 + Math.min(0.45, h * 0.018));
        boss.setVelocity(v);
        from.getWorld().playSound(from, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.8f);
        from.getWorld().spawnParticle(Particle.CLOUD, from, 12, 0.4, 0.1, 0.4, 0.05);

        afterLanding(6, 35, () -> {
            Location land = boss.getLocation();
            impact(land, 3.5, 9.0, 0.7);
            land.getWorld().spawnParticle(Particle.EXPLOSION, land, 1);
            land.getWorld().playSound(land, Sound.ENTITY_GENERIC_BIG_FALL, 1.2f, 0.7f);
        });
    }

    private void dashStrike(Player t, boolean counter) {
        brain.busy(14);
        if (!counter) brain.startCooldown(Ability.DASH_STRIKE);
        Vector dir = t.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize().multiply(1.25).setY(0.2);
        boss.setVelocity(dir);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!brain.isAlive() || !t.isOnline() || t.isDead()) return;
            Location bl = boss.getLocation();
            if (t.getLocation().distanceSquared(bl) < 16) {
                hurt(t, dmg(counter ? 20 : 14));
                knock(t, bl, 0.9);
                bl.getWorld().spawnParticle(Particle.SWEEP_ATTACK, t.getLocation().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0);
                bl.getWorld().playSound(bl, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
            }
        }, 5L);
    }

    /** Épée volante à tête chercheuse limitée : esquivable en changeant de direction. 3 épées en phase 3. */
    private void swordThrow(Player t) {
        brain.busy(14);
        brain.startCooldown(Ability.SWORD_THROW);
        int count = brain.phases().current() >= 3 ? 3 : 1;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DROWNED_SHOOT, 1.5f, 0.5f);
        for (int i = 0; i < count; i++) {
            final double angle = (i - (count - 1) / 2.0) * 0.30;
            Bukkit.getScheduler().runTaskLater(plugin, () -> launchSword(t, angle), i * 4L);
        }
    }

    private void launchSword(Player t, double spread) {
        if (!brain.isAlive() || !t.isOnline()) return;
        World w = boss.getWorld();
        Location start = boss.getEyeLocation();
        Vector initial = t.getEyeLocation().toVector().subtract(start.toVector()).normalize()
                .rotateAroundY(spread).multiply(0.9);

        ItemDisplay disp = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHERITE_SWORD));
            d.setGlowing(true);
            d.setPersistent(false);
        });

        new BukkitRunnable() {
            Vector vel = initial;
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive() || !disp.isValid() || !t.isOnline() || ticks > 70) {
                    disp.remove();
                    cancel();
                    return;
                }
                Location cur = disp.getLocation();
                Vector want = brain.predict(t, 3).add(0, 1.0, 0).toVector().subtract(cur.toVector());
                if (want.lengthSquared() > 0.01) want.normalize().multiply(1.1);
                vel = vel.multiply(0.84).add(want.multiply(0.16)); // virage progressif : on peut l'esquiver
                if (vel.length() > 1.1) vel.normalize().multiply(1.1);

                Location next = cur.clone().add(vel);
                if (!next.getBlock().isPassable()) {
                    w.spawnParticle(Particle.EXPLOSION, cur, 1);
                    disp.remove();
                    cancel();
                    return;
                }
                next.setDirection(vel);
                disp.teleport(next);
                w.spawnParticle(Particle.CRIT, next, 3, 0.1, 0.1, 0.1, 0.05);

                for (Player p : w.getNearbyPlayers(next, 1.2)) {
                    if (!brain.isValidTarget(p)) continue;
                    hurt(p, dmg(13));
                    w.spawnParticle(Particle.EXPLOSION, next, 1);
                    w.playSound(next, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.0f);
                    disp.remove();
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void shieldUp() {
        brain.startCooldown(Ability.SHIELD_UP);
        brain.raiseShield(40 + rng.nextInt(25));
    }

    /** Téléportation dans le dos de la cible, avec un court télégraphe à l'arrivée. */
    private void blink(Player t) {
        Location dest = findBlinkSpot(t);
        if (dest == null) return;
        brain.busy(14);
        brain.startCooldown(Ability.BLINK);

        World w = boss.getWorld();
        Location from = boss.getLocation();
        w.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.6f);
        w.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive()) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 25, 0.4, 0.9, 0.4, 0.3);
                if (ticks >= 8) {
                    cancel();
                    Location arrive = dest.clone();
                    arrive.setDirection(t.getLocation().toVector().subtract(arrive.toVector()));
                    boss.teleport(arrive);
                    boss.setVelocity(new Vector(0, 0, 0));
                    w.playSound(arrive, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);
                    if (t.isOnline() && !t.isDead() && t.getLocation().distanceSquared(arrive) < 25) {
                        hurt(t, dmg(10));
                        knock(t, arrive, 0.8);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location findBlinkSpot(Player t) {
        Location tl = t.getLocation();
        Vector look = tl.getDirection().setY(0);
        if (look.lengthSquared() < 0.01) look = new Vector(1, 0, 0);
        look.normalize();
        int[] angles = {180, 150, -150, 110, -110, 70, -70, 0}; // 180° = dans le dos du joueur
        double[] radii = {2.8, 3.6, 4.6};
        for (double r : radii) {
            for (int a : angles) {
                Vector v = look.clone().rotateAroundY(Math.toRadians(a)).multiply(r);
                Location spot = standable(tl.clone().add(v));
                if (spot != null) return spot;
            }
        }
        return null;
    }

    private Location standable(Location c) {
        int h = (int) Math.ceil(boss.getHeight());
        for (int off : new int[]{0, 1, -1, 2, -2}) {
            Location l = c.clone().add(0, off, 0);
            Block feet = l.getBlock();
            boolean clear = true;
            for (int i = 0; i < h; i++) {
                if (!feet.getRelative(0, i, 0).isPassable()) {
                    clear = false;
                    break;
                }
            }
            if (clear && !feet.getRelative(0, -1, 0).isPassable()) {
                return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5);
            }
        }
        return null;
    }

    /**
     * Saut, suspension en l'air avec anneau rouge au sol (la zone est verrouillée),
     * puis plongeon. Le joueur a ~0,5 s pour sortir de la zone.
     */
    private void maceSmash(Player t) {
        brain.busy(70);
        brain.startCooldown(Ability.MACE_SMASH);
        int phase = brain.phases().current();
        World w = boss.getWorld();
        w.playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.4f);
        t.sendActionBar(Component.text("⚠ MACE SMASH !", NamedTextColor.DARK_RED));

        Vector toward = t.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() > 0.01) toward.normalize().multiply(0.25);
        boss.setVelocity(new Vector(toward.getX(), 1.05 + 0.1 * phase, toward.getZ()));

        double radius = phase >= 3 ? 8.0 : 6.5;
        new BukkitRunnable() {
            int ticks = 0;
            Location lock;
            boolean slammed;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive()) {
                    boss.setGravity(true);
                    cancel();
                    return;
                }
                if (ticks == 12) {
                    lock = ground(brain.predict(t, 8));
                    boss.setGravity(false);
                    boss.setVelocity(new Vector(0, 0, 0));
                }
                if (ticks > 12 && !slammed) {
                    ring(lock, radius, Color.RED, 1.6f, 36);
                    ring(lock, radius * Math.min(1.0, (ticks - 12) / 10.0), Color.ORANGE, 1.2f, 24);
                    if (ticks == 22) {
                        slammed = true;
                        boss.setGravity(true);
                        Vector v = lock.toVector().subtract(boss.getLocation().toVector());
                        if (v.lengthSquared() > 0.01) v.normalize().multiply(2.2);
                        boss.setVelocity(v);
                    }
                } else if (slammed && (boss.isOnGround() || ticks >= 42)) {
                    cancel();
                    Location loc = boss.getLocation();
                    impact(loc, radius, 28.0, 1.3);
                    smashFx(loc);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Onde de choc circulaire : on l'évite en SAUTANT au passage de l'anneau. */
    private void shockwave() {
        brain.busy(34);
        brain.startCooldown(Ability.SHOCKWAVE);
        double radius = brain.phases().current() >= 3 ? 13 : 10;
        Location center = boss.getLocation().clone();
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);
        for (Player p : w.getNearbyPlayers(center, radius + 6)) {
            p.sendActionBar(Component.text("⚠ SAUTEZ !", NamedTextColor.GOLD));
        }
        Set<Player> hit = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive()) {
                    cancel();
                    return;
                }
                if (ticks <= 18) {
                    ring(center, radius, Color.RED, 1.4f, 40);
                    ring(center, radius * ticks / 18.0, Color.ORANGE, 1.0f, 28);
                } else if (ticks <= 30) {
                    if (ticks == 19) w.playSound(center, Sound.ITEM_MACE_SMASH_GROUND, 3.0f, 0.5f);
                    double r = radius * (ticks - 18) / 12.0;
                    ring(center, r, Color.WHITE, 1.8f, 48);
                    for (Player p : w.getNearbyPlayers(center, radius + 2)) {
                        if (hit.contains(p) || !brain.isValidTarget(p)) continue;
                        Location pl = p.getLocation();
                        double d = Math.hypot(pl.getX() - center.getX(), pl.getZ() - center.getZ());
                        if (Math.abs(d - r) > 1.2) continue;
                        if (pl.getY() - center.getY() > 0.8) continue; // saut réussi
                        hit.add(p);
                        hurt(p, dmg(15));
                        knock(p, center, 1.1);
                    }
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Invoque des Vex (durée de vie limitée, plafonnés par boss.ai.minion-limit). */
    private void summon(Player t) {
        int room = brain.cfg().getInt("boss.ai.minion-limit", 4) - brain.minionCount();
        if (room <= 0) return;
        brain.busy(30);
        brain.startCooldown(Ability.SUMMON);

        int n = Math.min(room, brain.phases().current() >= 3 ? 3 : 2);
        World w = boss.getWorld();
        w.playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.6f);
        for (int i = 0; i < n; i++) {
            Location l = boss.getLocation().clone()
                    .add((rng.nextDouble() - 0.5) * 4, 1.5, (rng.nextDouble() - 0.5) * 4);
            Vex vex = w.spawn(l, Vex.class, v -> {
                v.setCustomName("§5Éclat du Vide");
                v.setTarget(t);
                v.setPersistent(false);
            });
            brain.addMinion(vex);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 20, 0.3, 0.3, 0.3, 0.05);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (vex.isValid()) vex.remove();
            }, 400L);
        }
    }

    /** Pluie de météores : le premier vise la position prédite, les autres bouchent les échappatoires. */
    private void meteors(Player t) {
        brain.busy(26);
        brain.startCooldown(Ability.METEORS);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.6f);
        t.sendActionBar(Component.text("☄ MÉTÉORES !", NamedTextColor.GOLD));
        for (int i = 0; i < 6; i++) {
            Location base = i == 0
                    ? brain.predict(t, 16)
                    : t.getLocation().clone().add((rng.nextDouble() - 0.5) * 14, 0, (rng.nextDouble() - 0.5) * 14);
            Location g = ground(base);
            Bukkit.getScheduler().runTaskLater(plugin, () -> meteor(g), i * 7L);
        }
    }

    private void meteor(Location g) {
        if (!brain.isAlive()) return;
        World w = g.getWorld();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!brain.isAlive()) {
                    cancel();
                    return;
                }
                if (ticks < 26) {
                    if (ticks % 2 == 0) ring(g, 3.2, Color.ORANGE, 1.4f, 24);
                    if (ticks > 18) {
                        double height = (26 - ticks) * 1.6;
                        w.spawnParticle(Particle.FLAME, g.getX(), g.getY() + height, g.getZ(), 6, 0.25, 0.25, 0.25, 0.01);
                    }
                } else {
                    cancel();
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, g, 1);
                    w.spawnParticle(Particle.FLAME, g, 40, 1.5, 0.3, 1.5, 0.08);
                    w.playSound(g, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
                    impact(g, 3.4, 17.0, 0.9);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}

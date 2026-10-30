package fr.zyumie.EndBoss.IA;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Le boss observe chaque joueur et apprend son style de combat (kiting à l'arc,
 * pillar vers le haut, vol à l'élytra...). Toutes les valeurs décroissent avec le temps :
 * le boss s'adapte à ce que le joueur fait MAINTENANT.
 * Sert aussi à estimer la vraie vélocité du joueur (Player#getVelocity est fiable à 0 côté serveur).
 */
public class PlayerProfile {

    private double shots, melee, evasive, pillar, aerial;
    private int stationary;
    private Location last;
    private long lastObserved = -1000;
    private Vector velocity = new Vector();

    public void recordShot() {
        shots += 1.0;
    }

    public void recordMelee() {
        melee += 1.0;
    }

    public void observe(Player p, Location bossLoc, long nowTicks, int dt) {
        Location cur = p.getLocation();
        boolean fresh = last != null && last.getWorld() != null
                && last.getWorld().equals(cur.getWorld())
                && nowTicks - lastObserved <= dt * 3L;
        if (fresh) {
            Vector delta = cur.toVector().subtract(last.toVector());
            Vector perTick = delta.clone().multiply(1.0 / Math.max(1, dt));
            velocity = velocity.multiply(0.5).add(perTick.multiply(0.5));

            double horiz = Math.hypot(delta.getX(), delta.getZ());
            double distNow = cur.distance(bossLoc);
            double distBefore = last.distance(bossLoc);

            if (distNow < 18 && distNow - distBefore > 0.25) evasive += 1.0;       // il fuit / il kite
            if (horiz < 0.2 && delta.getY() > 0.35) pillar += 1.5;                  // il monte en pillar
            if (p.isGliding() || p.isFlying()) aerial += 1.0;                       // élytra / vol
            stationary = (horiz < 0.04 && Math.abs(delta.getY()) < 0.04) ? stationary + 1 : 0;
        } else {
            velocity = new Vector();
            stationary = 0;
        }
        last = cur.clone();
        lastObserved = nowTicks;

        shots *= 0.992;
        melee *= 0.992;
        evasive *= 0.99;
        pillar *= 0.985;
        aerial *= 0.99;
    }

    /** 0..1 : le joueur joue à distance / en kiting. */
    public double kite() {
        return clamp((shots * 2.0 + evasive * 0.5) / 10.0);
    }

    /** 0..1 : le joueur monte des tours de blocs. */
    public double pillar() {
        return clamp(pillar / 6.0);
    }

    /** 0..1 : le joueur utilise le vol / l'élytra. */
    public double aerial() {
        return clamp(aerial / 10.0);
    }

    public double melee() {
        return clamp(melee / 8.0);
    }

    public boolean isPillaring() {
        return pillar() > 0.3;
    }

    public boolean isStationary() {
        return stationary >= 6;
    }

    public Vector velocity() {
        return velocity.clone();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

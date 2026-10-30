package fr.zyumie.EndBoss;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;

import java.util.ArrayList;
import java.util.Collection;

/** BossBar propre à chaque boss : vie, phase et joueurs dans le rayon d'affichage. */
public class EndBossBar {

    private final BossBar bar;
    private final double range;
    private String lastTitle = "";
    private int lastPhase = -1;

    public EndBossBar(String title, double range) {
        this.range = range;
        this.bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SOLID);
        this.bar.setVisible(true);
    }

    public void update(WitherSkeleton boss, int phase, String title, double maxHealth) {
        bar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / maxHealth)));

        if (!title.equals(lastTitle)) {
            bar.setTitle(title);
            lastTitle = title;
        }
        if (phase != lastPhase) {
            lastPhase = phase;
            switch (phase) {
                case 3 -> {
                    bar.setColor(BarColor.RED);
                    bar.setStyle(BarStyle.SEGMENTED_20);
                }
                case 2 -> {
                    bar.setColor(BarColor.YELLOW);
                    bar.setStyle(BarStyle.SEGMENTED_10);
                }
                default -> {
                    bar.setColor(BarColor.PURPLE);
                    bar.setStyle(BarStyle.SOLID);
                }
            }
        }

        Collection<Player> near = boss.getWorld().getNearbyPlayers(boss.getLocation(), range);
        for (Player p : near) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        for (Player p : new ArrayList<>(bar.getPlayers())) {
            if (!near.contains(p)) bar.removePlayer(p);
        }
    }

    public void remove() {
        bar.removeAll();
        bar.setVisible(false);
    }
}

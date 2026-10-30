package fr.zyumie.EndBoss.IA;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mémoire de menace : plus un joueur inflige de dégâts, plus le boss le considère dangereux.
 * La menace décroît avec le temps pour que le boss puisse changer de cible.
 */
public class ThreatTable {

    private final Map<UUID, Double> threat = new HashMap<>();

    public void add(UUID id, double value) {
        threat.merge(id, value, Double::sum);
    }

    public double get(UUID id) {
        return threat.getOrDefault(id, 0.0);
    }

    public void decay() {
        threat.replaceAll((id, v) -> v * 0.992);
        threat.values().removeIf(v -> v < 0.5);
    }

    public void clear() {
        threat.clear();
    }
}

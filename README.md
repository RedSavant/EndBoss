<div align="center">

[![Paper](https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/paper_vector.svg)](https://papermc.io/)
[![Purpur](https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/purpur_vector.svg)](https://purpurmc.org/)

</div>

---

# ⚔️ EndBoss

Boss de l'End pour **Paper 1.21** : un Wither Skeleton en 3 phases doté d'une IA adaptative, qui verrouille le portail de l'End tant qu'il est en vie.

## Fonctionnalités

- **3 phases** configurables (échelle, vitesse, dégâts, cooldowns, capacités, régénération), avec une transition cinématique entre chaque.
- **IA à score d'utilité** : le boss choisit sa cible (menace, distance, vue, vie basse) et son attaque selon la situation, en évitant de se répéter.
- **Apprentissage du joueur** : il repère l'arc, le pillar et l'élytra, et s'adapte.
- **Capacités télégraphiées** : bond prédictif, ruée, épées volantes, téléportation dans le dos, Mace Smash, onde de choc (à éviter en sautant), Vex invoqués, météores.
- **Défense** : bouclier de face brisable à la hache, esquive des projectiles, contre-attaque.
- **Terrain** : ponts, pillar, percement de murs, anti-pillar. Tout est restauré automatiquement, sans drop ni dégât définitif.
- **Régénération** : légère en phase 2 et 3, rapide si on le laisse tranquille.
- **Verrou du portail de l'End** tant que le boss est vivant (état sauvegardé, résiste aux redémarrages et aux chunks déchargés).
- BossBar par boss, classement des meilleurs combattants à sa mort, loots personnalisables.

## Prérequis

- Paper **1.21.3 ou plus** (API `Attribute.MAX_HEALTH`, `Particle.EXPLOSION_EMITTER`...)
- Java 21

## Installation

1. Compile le plugin et place le `.jar` dans `plugins/`.
2. Démarre le serveur : `plugins/EndBoss/Boss.yml` est généré.
3. `/endboss spawn` pour invoquer le boss.

## Commandes

Permission : `endboss.admin` (op par défaut).

| Commande | Description |
|---|---|
| `/endboss spawn [force]` | Invoque le boss (`force` ignore la limite d'une instance) |
| `/endboss kill` | Retire le boss et déverrouille le portail |
| `/endboss reload` | Recharge `Boss.yml` |
| `/endboss status` | PV, phase et cible du boss |
| `/endboss unlock` | Lève le verrou du portail de l'End |

## Configuration

Tout se règle dans `Boss.yml` (les clés absentes prennent leur valeur par défaut) :

- `boss.health`, `boss.speed`, `boss.damage`, `boss.damage-taken-multiplier`, `boss.ability-damage`
- `boss.ai.*` : niveau de skill, apprentissage, esquive, régénération au repos, limite de minions
- `boss.griefing.*` : casser/poser des blocs, durée de restauration, matériaux protégés
- `boss.phases.phase-N.*` : seuil de vie, échelle, multiplicateurs, régénération, liste des capacités
- `boss.display.overhead-format` : texte au-dessus de la tête (`{hearts}` ou `{hp}`)

## Structure

```
fr.zyumie.EndBoss
├── Main, EndBossRegistry, EndBossBar
├── IA/        EndBossBrain, Combat, Movement, Scaffold, AIController,
│              Ability, ThreatTable, PlayerProfile, BlockRestorer
└── Listener/  EndBossListener, EndBossManager, EndBossPhaseManager
```

---

*© 2026 Zyumie. Tous droits réservés.  
Aucune utilisation, modification ou redistribution sans autorisation explicite.*

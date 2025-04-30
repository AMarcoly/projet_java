# Projet Java — Partage de fichiers avec délégation et métriques

## Description

Ce projet implémente un système de transfert de fichiers en **Java** comprenant un **serveur**, des **clients téléchargeurs**, ainsi que des **clients délégués** (Trusted Helpers) en cas de surcharge. Il permet aussi de **collecter des métriques** sur les performances, le nombre d’aides et les connexions, pour évaluation statistique.

## Fonctionnalités principales

- Téléchargement de fichiers en blocs via un serveur TCP.
- Délégation automatique à des clients « Trusted Helpers » en cas de surcharge du serveur.
- Comparaison des fichiers téléchargés (via MD5) pour valider l'intégrité.
- Enregistrement des métriques (durée, connexions fermées, aides entre clients).
- Export des résultats de test dans un **fichier CSV** pour analyse graphique.
- Tests automatisés via la classe `TestLauncher`.

## Structure du projet

```
.
├── server/           --> Serveur TCP de partage de fichiers
├── client/           --> Clients téléchargeurs + Trusted Helpers
├── common/           --> Outils communs (Logger, Request, Utils, etc.)
├── test_logs/        --> Fichiers de log des tests
├── client_files/     --> Fichiers assemblés par les clients
├── server_files/     --> Fichiers disponibles côté serveur
├── metrics.csv       --> Résultats métriques exportés
├── TestLauncher.java --> Lancement automatisé des tests
└── README.md
```

## Compilation

Utiliser `javac` depuis la racine :

```bash
javac server/*.java client/*.java common/*.java TestLauncher.java
```

## Exécution

### Lancer les tests automatiquement

```bash
java TestLauncher
```

Cela lance :
- un serveur sur le port 5000 avec une capacité fixée
- un test complet client/serveur
- un test de délégation (avec surcharge simulée)
- exporte les métriques dans `metrics.csv`

### Lancer manuellement

#### Serveur

```bash
java server.Server --port=5000 --capacity=2
```

#### Client

```bash
java client.Client --ip=127.0.0.1 --port=5000 --file=file1.txt --DC=2
```

## Tests inclus

- `testUtils` : vérifie les fonctions d’outils (`Utils`).
- `testServerAndClientEndToEnd` : test de bout en bout.
- `testDelegationScenario` : test avec délégation automatique.

## Fichier CSV généré

Le fichier `metrics.csv` contient pour chaque test :
- la durée totale
- le nombre de blocs téléchargés
- le nombre d’aides/délégations
- le nombre de connexions fermées aléatoirement

Exemple :
```
test,duration_ms,blocks_received,help_count,closed_connections
EndToEnd,1580,2,0,0
Delegation,4763,4,2,1
```

## Auteurs

Projet réalisé dans le cadre du Master 1 Informatique.  Marcoly ANTOINE et Cam Lao NGUYEN
Développé en Java pur (sans framework externe), avec usage de `Socket`, `ExecutorService` et `Logger`.

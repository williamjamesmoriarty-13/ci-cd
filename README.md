# Système universitaire "patient" — terrain de test pour le moteur de sûreté (M2)

Système miniature à 3 services backend polyglottes (Python/Flask +
Java/Spring Boot), une base PostgreSQL partagée mais isolée par schéma, et un
frontend React minimal. Conçu comme **application cible** pour tester plus
tard un moteur de vérification de sûreté / remédiation automatique sur
Kubernetes : chaque appel inter-service est loggé de façon structurée et
identifiable (format JSON commun, `trace_id` propagé de bout en bout) pour
alimenter un futur graphe de dépendances dynamique (OpenTelemetry).

## Architecture

```
                    ┌─────────────────────┐
        ┌──────────▶│ teacher-admin-service│  (Java / Spring Boot, port 8080)
        │           │  cours, annonces,    │
        │           │  notes               │
        │           └──────────▲───────────┘
        │                      │
┌───────┴─────────┐            │
│ student-service  │            │
│ (Python/Flask,   │            │
│  port 5001)      │            │
└───────┬──────────┘            │
        │                      │
        │           ┌──────────┴───────────┐
        └──────────▶│ enrollment-service    │
                     │ (Python/Flask,       │
                     │  port 5003)          │
                     └──────────────────────┘
```

Arêtes actives du graphe de dépendances :
- `student-service` → `teacher-admin-service` (`GET /students/{id}/available-courses`)
- `student-service` → `enrollment-service` (`GET /students/{id}/enrollments`, `GET /students/{id}/grades`)
- `enrollment-service` → `student-service` (vérification de l'étudiant à l'inscription)
- `enrollment-service` → `teacher-admin-service` (vérification de capacité, réservation de place, lecture des notes)

Toute communication inter-service passe **exclusivement par API REST** (jamais
de requête SQL cross-schéma). Chaque service a son propre schéma PostgreSQL
et son propre rôle applicatif dédié, isolé par le principe du moindre
privilège (voir `postgres/init/01-init-schemas.sh`).

## Sécurité de base appliquée sur chaque service

- Validation stricte des entrées (marshmallow côté Python, Bean Validation côté Java)
- Aucun détail d'erreur interne (stacktrace, message d'exception brut) renvoyé au client
- Utilisateur non-root dans chaque conteneur
- Serveurs de production uniquement : gunicorn (Flask) et Tomcat embarqué (Spring Boot), jamais de serveur de développement
- Secrets exclusivement via variables d'environnement (jamais en dur), avec échec explicite au démarrage si une variable obligatoire manque
- Rôles PostgreSQL dédiés par service, restreints à leur propre schéma

## Démarrage rapide

```bash
cp .env.example .env
# éditer .env si besoin (mots de passe par défaut à changer en production)

./scripts/e2e-test.sh
```

Ce script unique :
1. démarre toute la stack (`docker compose up -d --build`)
2. attend que tous les healthchecks passent au vert
3. crée un étudiant, crée un cours, inscrit l'étudiant, puis interroge
   `student-service` pour vérifier toute la chaîne d'appels
4. affiche, ligne par ligne, le résultat de chaque appel inter-service :
   `[OK] enrollment-service -> teacher-admin-service (12.4 ms)` ou
   `[ÉCHEC] ...`

Prérequis sur la machine hôte : `docker`, `docker compose` (v2), `curl`, `jq`.

### Démarrage manuel (sans le script)

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps          # vérifier que tout est "healthy"
```

### Arrêt / nettoyage complet

```bash
docker compose down -v     # -v supprime aussi le volume Postgres
```

## Services et ports (valeurs par défaut de .env.example)

| Service                | Techno            | Port hôte | Rôle |
|-------------------------|-------------------|-----------|------|
| postgres                | PostgreSQL 16     | 5432      | Une base, 3 schémas isolés |
| student-service          | Python / Flask    | 5001      | Profil étudiant + fan-out |
| teacher-admin-service     | Java / Spring Boot | 5002      | Cours, annonces, notes |
| enrollment-service        | Python / Flask    | 5003      | Pont étudiant ↔ cours |
| frontend                 | React (Vite+nginx)| 3000      | UI minimale |

## Endpoints principaux

**student-service** (`http://localhost:5001`)
- `POST /students`, `GET /students`, `GET /students/{id}`, `PUT /students/{id}`, `DELETE /students/{id}`
- `GET /students/{id}/available-courses` → appelle teacher-admin-service
- `GET /students/{id}/enrollments` → appelle enrollment-service
- `GET /students/{id}/grades` → appelle enrollment-service (qui appelle teacher-admin-service)
- `GET /health`

**teacher-admin-service** (`http://localhost:5002`)
- `POST /courses`, `GET /courses`, `GET /courses/{id}`, `GET /courses/{id}/capacity`
- `POST /courses/{id}/reserve-seat`, `POST /courses/{id}/release-seat`
- `POST /announcements`, `GET /announcements?courseId=`
- `POST /grades`, `GET /grades?studentId=`
- `GET /actuator/health`

**enrollment-service** (`http://localhost:5003`)
- `POST /enrollments` (vérifie l'étudiant + le cours, réserve une place)
- `GET /enrollments?student_id=`, `GET /enrollments/{id}`, `DELETE /enrollments/{id}`
- `GET /grades?student_id=` (proxy vers teacher-admin-service)
- `GET /health`

## Migrations de base de données

- `student-service` et `enrollment-service` : Alembic, exécuté automatiquement
  au démarrage du conteneur (`entrypoint.sh` → `alembic upgrade head`).
- `teacher-admin-service` : Flyway, exécuté automatiquement au démarrage de
  l'application Spring Boot.

Aucune étape manuelle : tout est appliqué automatiquement au premier
`docker compose up`.

## Format des logs inter-services

Chaque service écrit des logs JSON Lines sur stdout, avec un schéma commun
(voir `student-service/app/logging_utils.py` et l'équivalent Java
`StructuredLogger`). Exemple d'appel sortant :

```json
{"timestamp":"2026-08-01T10:00:00.123Z","level":"INFO","service":"enrollment-service","event":"outbound_call","target_service":"teacher-admin-service","method":"POST","path":"/courses/3/reserve-seat","outcome":"success","status_code":200,"latency_ms":8.2,"trace_id":"b3c1..."}
```

Le `trace_id` est propagé de service en service via le header `X-Trace-Id`,
ce qui permet de reconstituer une chaîne d'appels complète même sur
plusieurs sauts (ex: `student-service → enrollment-service → teacher-admin-service`).

## Vérifier manuellement l'interconnexion

```bash
# Suivre les logs structurés en direct
docker compose logs -f student-service teacher-admin-service enrollment-service

# Exemple de scénario manuel
curl -X POST http://localhost:5001/students \
  -H "Content-Type: application/json" \
  -d '{"first_name":"Grace","last_name":"Hopper","email":"grace@universite.test"}'

curl -X POST http://localhost:5002/courses \
  -H "Content-Type: application/json" \
  -d '{"title":"Compilateurs","description":"...","capacity":30}'

curl -X POST http://localhost:5003/enrollments \
  -H "Content-Type: application/json" \
  -d '{"student_id":1,"course_id":1}'

curl http://localhost:5001/students/1/grades
```

## Dépannage

### `Error: Database is uninitialized and superuser password is not specified`

Cette erreur de Postgres (boucle de redémarrage, puis échec en cascade des
autres services à cause de `depends_on: postgres healthy`) signifie que le
fichier `.env` est **absent** au moment du `docker compose up` : les
variables comme `${POSTGRES_PASSWORD}` sont alors interpolées en chaîne
vide. Cela arrive si vous lancez `docker compose up` directement sans être
passé par `cp .env.example .env` au préalable (le script `e2e-test.sh` le
fait automatiquement, mais pas un `docker compose up` manuel).

Correction :
```bash
docker compose down -v      # nettoie le volume Postgres resté à moitié initialisé
cp .env.example .env
docker compose up -d --build
```

Le volume `postgres_data` n'est initialisé (schémas + rôles) qu'au tout
premier démarrage réussi du conteneur. Si Postgres a déjà démarré une fois
en échec, il faut supprimer le volume (`down -v`) avant de relancer, sinon
un volume "vide mais déjà créé" peut laisser Postgres dans un état
incohérent.

## Limites connues / points d'attention

- Les migrations tournent au démarrage du conteneur applicatif ; en cas de
  déploiement multi-réplicas il faudrait les extraire dans un job dédié
  (hors périmètre ici, un seul réplica par service).
- Le frontend appelle les 3 backends directement depuis le navigateur via
  les ports exposés sur l'hôte (pas de passerelle API unique) — cohérent
  avec l'objectif "développement uniquement, pas de fonctionnalité
  superflue".
- Pas d'authentification/autorisation : hors périmètre du patient système
  (le sujet porte sur la sûreté de la remédiation, pas la sécurité applicative
  complète).

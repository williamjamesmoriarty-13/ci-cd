"""
Instrumentation Prometheus pour student-service.

- PrometheusMetrics (prometheus_flask_exporter) instrumente automatiquement
  TOUTES les routes Flask existantes sans y toucher : latence (histogram)
  et nombre de requêtes par méthode/endpoint/statut. Elle enregistre
  elle-même la route GET /metrics.
- Le Counter `outbound_calls_total` est ajouté manuellement dans
  http_client.py pour les appels sortants vers les autres services
  (teacher-admin-service, enrollment-service), avec le même label
  `target_service` que les logs JSON existants, pour pouvoir corréler les
  deux plus tard avec le graphe de dépendances.

Aucune logique métier existante n'est modifiée : ce module est purement
additif (avant_request/after_request internes à prometheus_flask_exporter,
indépendants de ceux déjà définis dans app/__init__.py).
"""
from prometheus_client import Counter
from prometheus_flask_exporter import PrometheusMetrics

# Compteur des appels SORTANTS (vers teacher-admin-service / enrollment-service).
# Label "outcome" aligné sur le champ "outcome" des logs JSON structurés
# (logging_utils.py) : "success" | "failure".
OUTBOUND_CALLS = Counter(
    "outbound_calls_total",
    "Nombre d'appels sortants effectués vers un autre service",
    ["target_service", "outcome"],
)


def init_metrics(app):
    """Attache /metrics à l'application Flask. group_by="url_rule" évite
    l'explosion de cardinalité sur les routes avec paramètres (ex:
    /students/<int:student_id> reste un seul label, pas un par id)."""
    metrics = PrometheusMetrics(app, group_by="url_rule", path="/metrics")
    metrics.info(
        "student_service_info", "Informations statiques du service", version="1.0.0"
    )
    return metrics
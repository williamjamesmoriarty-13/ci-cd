from flask import Blueprint, current_app, g, jsonify, request
from marshmallow import ValidationError
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError, SQLAlchemyError

from .http_client import DownstreamError, call_service
from .models import Enrollment, db
from .schemas import EnrollmentCreateSchema

bp = Blueprint("enrollments", __name__)

enrollment_create_schema = EnrollmentCreateSchema()


def _logger():
    return current_app.extensions["structured_logger"]


def _trace_id():
    return getattr(g, "trace_id", "unknown")


@bp.route("/health", methods=["GET"])
def health():
    try:
        db.session.execute(text("SELECT 1"))
        return jsonify({"status": "ok", "service": "enrollment-service"}), 200
    except SQLAlchemyError:
        current_app.logger.error({"service": "enrollment-service", "event": "health_check_failed"})
        return jsonify({"status": "unhealthy", "service": "enrollment-service"}), 503


# --------------------------------------------------------------------------
# Création d'une inscription : dépend RÉELLEMENT et ACTIVEMENT de
# student-service (existence de l'étudiant) ET teacher-admin-service
# (existence du cours + place disponible + réservation de siège).
# --------------------------------------------------------------------------
@bp.route("/enrollments", methods=["POST"])
def create_enrollment():
    payload = request.get_json(silent=True)
    if payload is None:
        return jsonify({"error": "Corps de requête JSON invalide ou manquant"}), 400
    try:
        data = enrollment_create_schema.load(payload)
    except ValidationError as err:
        return jsonify({"error": "Validation échouée", "details": err.messages}), 400

    student_id = data["student_id"]
    course_id = data["course_id"]
    logger = _logger()
    trace_id = _trace_id()
    timeout = current_app.config["HTTP_TIMEOUT_SECONDS"]

    # 1) Vérification de l'existence de l'étudiant auprès de student-service
    student_url = f"{current_app.config['STUDENT_SERVICE_URL']}/internal/students/{student_id}/exists"
    try:
        resp = call_service(logger, "enrollment-service", "student-service", "GET", student_url,
                             trace_id=trace_id, timeout=timeout)
    except DownstreamError:
        return jsonify({"error": "Le service étudiant est momentanément indisponible"}), 502
    if not resp.json().get("exists", False):
        return jsonify({"error": "Étudiant introuvable"}), 404

    # 2) Vérification de l'existence et de la capacité du cours auprès de teacher-admin-service
    course_url = f"{current_app.config['TEACHER_ADMIN_SERVICE_URL']}/courses/{course_id}"
    try:
        resp = call_service(logger, "enrollment-service", "teacher-admin-service", "GET", course_url,
                             trace_id=trace_id, timeout=timeout)
    except DownstreamError as exc:
        if exc.status_code == 404:
            return jsonify({"error": "Cours introuvable"}), 404
        return jsonify({"error": "Le service des cours est momentanément indisponible"}), 502

    course = resp.json()
    if course.get("availableSeats", 0) <= 0:
        return jsonify({"error": "Ce cours est complet"}), 409

    # 3) Réservation effective d'une place côté teacher-admin-service
    reserve_url = f"{current_app.config['TEACHER_ADMIN_SERVICE_URL']}/courses/{course_id}/reserve-seat"
    try:
        call_service(logger, "enrollment-service", "teacher-admin-service", "POST", reserve_url,
                     trace_id=trace_id, timeout=timeout)
    except DownstreamError as exc:
        if exc.status_code == 409:
            return jsonify({"error": "Ce cours est complet"}), 409
        return jsonify({"error": "Impossible de réserver une place pour ce cours"}), 502

    # 4) Enregistrement local de l'inscription
    enrollment = Enrollment(student_id=student_id, course_id=course_id, status="enrolled")
    db.session.add(enrollment)
    try:
        db.session.commit()
    except IntegrityError:
        db.session.rollback()
        # Rollback compensatoire de la réservation de siège pour rester cohérent
        release_url = f"{current_app.config['TEACHER_ADMIN_SERVICE_URL']}/courses/{course_id}/release-seat"
        try:
            call_service(logger, "enrollment-service", "teacher-admin-service", "POST", release_url,
                         trace_id=trace_id, timeout=timeout)
        except DownstreamError:
            pass
        return jsonify({"error": "Cet étudiant est déjà inscrit à ce cours"}), 409
    except SQLAlchemyError:
        db.session.rollback()
        logger.error({"service": "enrollment-service", "event": "db_error", "action": "create_enrollment"})
        return jsonify({"error": "Erreur interne, réessayez plus tard"}), 500

    return jsonify(enrollment.to_dict()), 201


@bp.route("/enrollments", methods=["GET"])
def list_enrollments():
    student_id = request.args.get("student_id", type=int)
    query = Enrollment.query
    if student_id is not None:
        query = query.filter_by(student_id=student_id)
    enrollments = query.order_by(Enrollment.id.asc()).limit(200).all()
    return jsonify([e.to_dict() for e in enrollments]), 200

    
@bp.route("/enrollments/<int:enrollment_id>", methods=["GET"])
def get_enrollment(enrollment_id: int):
    enrollment = Enrollment.query.get(enrollment_id)
    if enrollment is None:
        return jsonify({"error": "Inscription introuvable"}), 404
    return jsonify(enrollment.to_dict()), 200


@bp.route("/enrollments/<int:enrollment_id>", methods=["DELETE"])
def cancel_enrollment(enrollment_id: int):
    enrollment = Enrollment.query.get(enrollment_id)
    if enrollment is None:
        return jsonify({"error": "Inscription introuvable"}), 404

    logger = _logger()
    trace_id = _trace_id()
    timeout = current_app.config["HTTP_TIMEOUT_SECONDS"]

    release_url = f"{current_app.config['TEACHER_ADMIN_SERVICE_URL']}/courses/{enrollment.course_id}/release-seat"
    try:
        call_service(logger, "enrollment-service", "teacher-admin-service", "POST", release_url,
                     trace_id=trace_id, timeout=timeout)
    except DownstreamError:
        return jsonify({"error": "Impossible de libérer la place pour ce cours"}), 502

    enrollment.status = "cancelled"
    db.session.commit()
    return jsonify(enrollment.to_dict()), 200


# --------------------------------------------------------------------------
# Proxy des notes : les notes sont la propriété de teacher-admin-service,
# enrollment-service les relaie pour le compte de student-service. Cela crée
# le chemin multi-sauts student-service -> enrollment-service -> teacher-admin-service.
# --------------------------------------------------------------------------
@bp.route("/grades", methods=["GET"])
def grades():
    student_id = request.args.get("student_id", type=int)
    if student_id is None:
        return jsonify({"error": "Le paramètre student_id est requis"}), 400

    url = f"{current_app.config['TEACHER_ADMIN_SERVICE_URL']}/grades?studentId={student_id}"
    try:
        response = call_service(_logger(), "enrollment-service", "teacher-admin-service", "GET", url,
                                 trace_id=_trace_id(), timeout=current_app.config["HTTP_TIMEOUT_SECONDS"])
    except DownstreamError:
        return jsonify({"error": "Le service des notes est momentanément indisponible"}), 502

    return jsonify(response.json()), 200

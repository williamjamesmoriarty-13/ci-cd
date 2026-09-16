#!/bin/sh
set -e

echo "[enrollment-service] Application des migrations Alembic..."
alembic upgrade head

echo "[enrollment-service] Démarrage de gunicorn..."
exec python wsgi.py

import time
import subprocess
import psycopg2  # Or your DB driver


# Wait for PostgreSQL to be ready
def wait_for_db():
    print("[enrollment-service] Application des migrations Alembic...")
    subprocess.run(["alembic", "upgrade", "head"], check=True)

    print("[enrollment-service] Démarrage de WSGI...")
    subprocess.run(["gunicorn", "-b", "0.0.0.0:5001", "wsgi:app"], check=True)


if __name__ == "__main__":
    wait_for_db()

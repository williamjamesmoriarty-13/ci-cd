# Dockerfile.dev — Flask (student-service ou enrollment-service)
# Mettre ce fichier dans le dossier du service : student-service/Dockerfile.dev
# (idem pour enrollment-service/Dockerfile.dev)
#
# Pas de multi-stage : on veut juste un rechargement automatique.
# Le code source est monté via volume dans docker-compose.dev.yml.

FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
        gcc \
        libpq-dev \
        libpq5 \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# On copie d'abord requirements pour profiter du cache Docker
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Le reste du code est injecté par le volume (pas besoin de COPY . .)
# Flask --debug recharge automatiquement quand un fichier .py change.

EXPOSE 5001

# ============================================================
# Stage 1 : Build des dépendances
# ============================================================
FROM cgr.dev/chainguard/python:latest-dev AS builder

WORKDIR /build

# La variante -dev fournit un shell + pip + apk (gestionnaire Wolfi)
# gcc/libpq-dev deviennent :
USER root
RUN apk add --no-cache build-base postgresql-dev

COPY requirements.txt .

RUN python -m pip install \
        --no-cache-dir \
        --upgrade pip \
    && python -m pip install \
        --no-cache-dir \
        --prefix=/install \
        --ignore-installed \
        -r requirements.txt

# ============================================================
# Stage 2 : Runtime
# ============================================================
FROM cgr.dev/chainguard/python:latest

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONPATH="/usr/local/lib/python3.14/site-packages:${PYTHONPATH}"

WORKDIR /app

COPY --from=builder /install /usr/local
COPY app ./app
COPY migrations ./migrations
COPY alembic.ini wsgi.py entrypoint.py ./

USER nonroot

EXPOSE 5001

ENTRYPOINT ["python", "entrypoint.py"]
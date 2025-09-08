FROM python:3.11-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1

# (optional but helpful for psycopg, etc.)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential libpq-dev \
  && rm -rf /var/lib/apt/lists/*

COPY rocket-credit-analysis/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY rocket-credit-analysis/alembic.ini ./alembic.ini
COPY rocket-credit-analysis/migrations/ ./migrations/
COPY rocket-credit-analysis/app/ ./app/

COPY rocket-credit-analysis/docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
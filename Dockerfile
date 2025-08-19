FROM python:3.11-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY alembic.ini .
COPY migrations/ migrations/

COPY app/ app/
# COPY models/ /models/   # if you ship model files

COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV PORT=8082
EXPOSE 8082
ENTRYPOINT ["/entrypoint.sh"]
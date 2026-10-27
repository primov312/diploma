FROM python:3.12-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1

COPY rocket-credit-collector/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY rocket-credit-collector/app/ ./app/

RUN useradd --system --uid 10002 collector
USER collector

ENV COLLECTOR_PORT=8100
EXPOSE 8100

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${COLLECTOR_PORT:-8100} --workers 1"]

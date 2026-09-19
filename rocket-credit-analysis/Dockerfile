FROM python:3.12-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1

COPY rocket-credit-analysis/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY rocket-credit-analysis/app/ ./app/

RUN useradd --system --uid 10001 analysis
USER analysis

ENV ANALYSIS_PORT=8000
EXPOSE 8000

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${ANALYSIS_PORT:-8000} --workers ${ANALYSIS_WORKERS:-2}"]

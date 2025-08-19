# app/settings.py
from pydantic_settings import BaseSettings
class Settings(BaseSettings):
    port: int = 8082
    user_data_url: str = "http://user-data:8080"
    user_data_timeout: float = 1.5
    threshold: float = 0.60
    sqlalchemy_url: str = "postgresql+psycopg://postgres:postgres@creditdb:5432/creditdb"
    kafka_bootstrap: str = "kafka:9092"
    # thresholds as safe fallback if DB policy missing
    approve_threshold: float = 0.60
    review_threshold: float = 0.50

    # cache TTL for policy
    policy_cache_ttl_seconds: int = 30

    # amount capacity calc
    income_affordability_multiplier: float = 0.3
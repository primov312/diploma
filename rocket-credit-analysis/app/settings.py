# app/settings.py
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration. The service is stateless: no database, object store
    or message broker. Everything it needs is this file, the policy JSON and
    (later) a local model artifact."""

    model_config = SettingsConfigDict(env_prefix="ANALYSIS_")

    port: int = 8000
    # Shared secret the Java backend sends in X-Analysis-Token. Empty disables the
    # check (local development only); Compose always sets it.
    shared_secret: str = ""
    policy_path: str = "app/policy.json"
    model_path: str = "app/model/model-v1.json"
    # FIXTURE answers from fixed data without any model call; AI asks Jev (TypeSafe System One).
    demo_provider_mode: str = "FIXTURE"
    typesafe_api_key: str = ""
    # Pinned so a report's modelVersion names the exact model; jev-latest is an alias.
    typesafe_model: str = "jev-1.13.0"
    typesafe_timeout: int = 30

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
    demo_provider_mode: str = "FIXTURE"
    # Which hosted model the optional AI mode calls: JEV (default; TypeSafe System One,
    # typed judgments only), or the generative GEMINI / ANTHROPIC adapters.
    ai_provider: str = "JEV"
    typesafe_api_key: str = ""
    # Pinned so a report's modelVersion names the exact model; jev-latest is an alias.
    typesafe_model: str = "jev-1.13.0"
    typesafe_timeout: int = 30
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"
    gemini_timeout: int = 30
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-haiku-5-5"
    anthropic_timeout: int = 60

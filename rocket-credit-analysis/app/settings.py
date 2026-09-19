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

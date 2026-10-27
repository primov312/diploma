# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
# app/settings.py
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration. The collector is stateless: it keeps no database and writes no files.

    The allow-list is the heart of the safety model. The collector only ever contacts a host that the
    operator has named here, so a user cannot point it at an arbitrary site or at the internal network.
    """

    model_config = SettingsConfigDict(env_prefix="COLLECTOR_")

    port: int = 8100
    # Shared secret the Java backend sends in X-Collector-Token. Empty disables the check (local development only).
    shared_secret: str = ""
    # Comma separated host names the collector may fetch from. The default is the bundled demo source, which the
    # collector itself serves under /demo-source/ so the whole flow works offline and is reproducible.
    allowed_hosts: str = "collector,localhost"
    user_agent: str = "RocketCreditCollector/1.0 (+consent-based; respects robots.txt)"
    timeout_seconds: float = 8.0
    max_bytes: int = 262_144
    max_posts: int = 40
    max_text_chars: int = 2000

    def allowed_host_set(self) -> frozenset[str]:
        return frozenset(h.strip().lower() for h in self.allowed_hosts.split(",") if h.strip())

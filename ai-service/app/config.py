from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import model_validator
from typing import Literal

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    app_env: str = "local"
    ai_internal_secret: str = "local-development-ai-secret-change-before-deployment-2026"
    java_internal_secret: str = "local-development-java-secret-change-before-deployment-2026"
    redis_url: str = "redis://:redis_dev_password@localhost:6379/0"
    minio_endpoint: str = "http://localhost:9000"
    java_internal_base_url: str = "http://localhost:8080"
    rabbitmq_url: str = "amqp://aftersales:rabbitmq_dev_password@localhost:5672/"
    llm_provider: Literal["fake"] = "fake"
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "aftersales_kb_v1"
    embedding_dimension: int = 8

    @model_validator(mode="after")
    def validate_production(self):
        if self.app_env not in {"local", "demo", "prod", "test"}:
            raise ValueError("APP_ENV must be local, demo, test or prod")
        if self.app_env != "prod":
            return self
        secrets = [self.ai_internal_secret, self.java_internal_secret]
        if any(len(s.encode()) < 32 or any(v in s.lower() for v in ("local-development", "replace-with", "change-me")) for s in secrets):
            raise ValueError("Production service secrets must be random and at least 32 bytes")
        if len(set(secrets)) != 2:
            raise ValueError("Service identities require distinct secrets")
        if any(marker in url for marker in ("dev_password", "replace-with") for url in (self.redis_url, self.rabbitmq_url)):
            raise ValueError("Production infrastructure credentials must be configured")
        return self

settings = Settings()

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
    llm_provider: Literal["fake", "deepseek"] = "fake"
    llm_base_url: str = "https://api.deepseek.com"
    llm_api_key: str = ""
    llm_chat_model: str = "deepseek-v4-pro"
    llm_max_tokens: int = 512
    embedding_provider: Literal["fake", "siliconflow"] = "fake"
    embedding_base_url: str = "https://api.siliconflow.cn/v1"
    embedding_api_key: str = ""
    embedding_model: str = "BAAI/bge-m3"
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "aftersales_kb_v1"
    embedding_dimension: int = 8
    rag_score_threshold: float | None = None

    @model_validator(mode="after")
    def validate_rag_threshold(self):
        import math
        if self.rag_score_threshold is not None and (not math.isfinite(self.rag_score_threshold) or not -1 <= self.rag_score_threshold <= 1):
            raise ValueError("RAG_SCORE_THRESHOLD must be a finite Cosine score between -1 and 1")
        if not 1 <= self.llm_max_tokens <= 4096 or self.embedding_dimension <= 0:
            raise ValueError("Invalid token limit or embedding dimension")
        from urllib.parse import urlsplit
        for kind, endpoint, expected_host in (
            (self.llm_provider, self.llm_base_url, "api.deepseek.com"),
            (self.embedding_provider, self.embedding_base_url, "api.siliconflow.cn"),
        ):
            url = urlsplit(endpoint)
            if kind != "fake" and (url.scheme != "https" or url.hostname != expected_host
                                    or url.username or url.password or url.query or url.fragment):
                raise ValueError("Provider endpoint must use its trusted HTTPS host")
        return self

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

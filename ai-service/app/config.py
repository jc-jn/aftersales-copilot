from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    ai_internal_secret: str = "change-me-local-only"
    java_internal_base_url: str = "http://localhost:8080"
    rabbitmq_url: str = "amqp://aftersales:rabbitmq_dev_password@localhost:5672/"
    llm_provider: str = "fake"

settings = Settings()

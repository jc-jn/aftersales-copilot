"""All ordinary tests are free and isolated from the developer's real model .env."""
import os

os.environ["LLM_PROVIDER"] = "fake"
os.environ["EMBEDDING_PROVIDER"] = "fake"
os.environ["EMBEDDING_DIMENSION"] = "8"
os.environ["APP_ENV"] = "test"
os.environ.pop("RAG_SCORE_THRESHOLD", None)

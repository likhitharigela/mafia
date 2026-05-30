import os

os.environ.setdefault("GIN_EVENT_BASE_URL", "http://test-gin")
os.environ.setdefault("SPRING_ENGINE_BASE_URL", "http://test-spring")
os.environ.setdefault("JWT_SECRET", "test-secret")
os.environ.setdefault("JWT_ALGORITHM", "HS256")
os.environ.setdefault("JWT_EXPIRES_MINUTES", "60")
os.environ.setdefault("TEMPORAL_HOST", "temporal:7233")
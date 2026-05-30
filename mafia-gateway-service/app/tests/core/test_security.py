import time

import jwt
import pytest
from fastapi import HTTPException

from app.core.security import jwt_handler


def test_create_and_decode_token():
    secret = "test-secret"
    algo = "HS256"

    token = jwt_handler.create_access_token("test-user", secret, algo, 60)

    decoded = jwt_handler.decode_access_token(token, secret, algo)
    assert decoded == "test-user"


def test_create_access_token_contains_expected_claims():
    secret = "test-secret"
    algo = "HS256"

    before = int(time.time())
    token = jwt_handler.create_access_token("test-user", secret, algo, 60)
    payload = jwt.decode(token, secret, algorithms=[algo])
    after = int(time.time())

    assert payload["sub"] == "test-user"
    assert "iat" in payload
    assert "exp" in payload
    assert before <= payload["iat"] <= after
    assert payload["exp"] > payload["iat"]


def test_decode_invalid_token():
    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token("invalid.token.string", "secret", "HS256")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid or expired token"


def test_decode_expired_token():
    token = jwt_handler.create_access_token("test-user", "secret", "HS256", -1)

    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token(token, "secret", "HS256")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid or expired token"


def test_decode_token_with_wrong_secret():
    token = jwt_handler.create_access_token("test-user", "correct-secret", "HS256", 60)

    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token(token, "wrong-secret", "HS256")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid or expired token"


def test_decode_token_with_wrong_algorithm():
    token = jwt_handler.create_access_token("test-user", "secret", "HS256", 60)

    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token(token, "secret", "HS512")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid or expired token"


def test_decode_token_missing_subject():
    token = jwt.encode(
        {
            "iat": int(time.time()),
            "exp": int(time.time()) + 3600,
        },
        "secret",
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token(token, "secret", "HS256")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid token payload"


def test_decode_token_with_non_string_subject_returns_401():
    token = jwt.encode(
        {
            "sub": 12345,
            "iat": int(time.time()),
            "exp": int(time.time()) + 3600,
        },
        "secret",
        algorithm="HS256",
    )

    with pytest.raises(HTTPException) as excinfo:
        jwt_handler.decode_access_token(token, "secret", "HS256")

    assert excinfo.value.status_code == 401
    assert excinfo.value.detail == "Invalid or expired token"
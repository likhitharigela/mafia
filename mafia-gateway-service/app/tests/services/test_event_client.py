import os
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest


os.environ.setdefault("GIN_EVENT_BASE_URL", "http://gin.local")


def _mock_response(payload: dict | list, status_code: int = 200):
    resp = MagicMock()
    resp.raise_for_status = MagicMock()
    if status_code >= 400:
        resp.raise_for_status.side_effect = httpx.HTTPStatusError(
            "error",
            request=MagicMock(),
            response=MagicMock(status_code=status_code),
        )
    resp.json = MagicMock(return_value=payload)
    resp.status_code = status_code
    return resp


@pytest.fixture
def mock_httpx_client():
    client = MagicMock()
    client.get = AsyncMock()
    client.post = AsyncMock()
    return client


@pytest.fixture(autouse=True)
def patch_httpx_client(mock_httpx_client):
    with patch("app.services.gin_client.event_client._client", mock_httpx_client):
        yield


@pytest.mark.asyncio
async def test_get_timer(mock_httpx_client):
    from app.services.gin_client.event_client import get_timer

    mock_httpx_client.get.return_value = _mock_response({"phase": "NIGHT", "remainingTime": 21})

    res = await get_timer("room1")

    assert res["phase"] == "NIGHT"
    assert res["remainingTime"] == 21
    mock_httpx_client.get.assert_awaited_once_with("/api/phase/room1/status")


@pytest.mark.asyncio
async def test_get_timer_raises_on_error(mock_httpx_client):
    from app.services.gin_client.event_client import get_timer

    mock_httpx_client.get.return_value = _mock_response({"error": "not found"}, status_code=404)

    with pytest.raises(httpx.HTTPStatusError):
        await get_timer("unknown-room")


@pytest.mark.asyncio
async def test_get_events(mock_httpx_client):
    from app.services.gin_client.event_client import get_events

    mock_httpx_client.get.return_value = _mock_response([{"event": "KILL"}, {"event": "SAVE"}])

    res = await get_events("room1")

    assert len(res) == 2
    assert res[0]["event"] == "KILL"
    mock_httpx_client.get.assert_awaited_once_with("/events/room1")


@pytest.mark.asyncio
async def test_get_events_raises_on_error(mock_httpx_client):
    from app.services.gin_client.event_client import get_events

    mock_httpx_client.get.return_value = _mock_response({"error": "error"}, status_code=500)

    with pytest.raises(httpx.HTTPStatusError):
        await get_events("unknown-room")


@pytest.mark.asyncio
async def test_start_phase_timer(mock_httpx_client):
    from app.services.gin_client.event_client import start_phase_timer

    mock_httpx_client.post.return_value = _mock_response(
        {"phase": "DAY_DISCUSSION", "started": True}
    )

    res = await start_phase_timer("room1", "DAY_DISCUSSION", 60)

    assert res["phase"] == "DAY_DISCUSSION"
    assert res["started"] is True

    mock_httpx_client.post.assert_awaited_once_with(
        "/api/phase/room1/start",
        json={
            "phase": "DAY_DISCUSSION",
            "durationSeconds": 60,
        },
    )


@pytest.mark.asyncio
async def test_start_phase_timer_raises_on_error(mock_httpx_client):
    from app.services.gin_client.event_client import start_phase_timer

    mock_httpx_client.post.return_value = _mock_response(
        {"error": "conflict"},
        status_code=409,
    )

    with pytest.raises(httpx.HTTPStatusError):
        await start_phase_timer("room1", "DAY_DISCUSSION", 60)


@pytest.mark.asyncio
async def test_raise_for_status_called_on_get_timer(mock_httpx_client):
    from app.services.gin_client.event_client import get_timer

    resp = _mock_response({"phase": "DAY"})
    mock_httpx_client.get.return_value = resp

    await get_timer("room2")

    resp.raise_for_status.assert_called_once()


@pytest.mark.asyncio
async def test_raise_for_status_called_on_get_events(mock_httpx_client):
    from app.services.gin_client.event_client import get_events

    resp = _mock_response([{"event": "KILL"}])
    mock_httpx_client.get.return_value = resp

    await get_events("room3")

    resp.raise_for_status.assert_called_once()


@pytest.mark.asyncio
async def test_raise_for_status_called_on_start_phase_timer(mock_httpx_client):
    from app.services.gin_client.event_client import start_phase_timer

    resp = _mock_response({"phase": "NIGHT"})
    mock_httpx_client.post.return_value = resp

    await start_phase_timer("room4", "NIGHT", 30)

    resp.raise_for_status.assert_called_once()
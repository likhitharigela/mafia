import os
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.temporal.activities.game_activities import (
    advance_phase_activity,
    cleanup_room_activity,
    start_game_activity,
    start_phase_timer_activity,
)


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.engine_get_game_state", new_callable=AsyncMock)
@patch("app.temporal.activities.game_activities.engine_start_game", new_callable=AsyncMock)
async def test_start_game_activity_returns_first_phase(mock_start_game, mock_get_game_state):
    mock_get_game_state.return_value = {"phase": "NIGHT"}

    result = await start_game_activity("room-1")

    assert result == "NIGHT"
    mock_start_game.assert_awaited_once_with("room-1")
    mock_get_game_state.assert_awaited_once_with("room-1")


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.engine_get_game_state", new_callable=AsyncMock)
@patch("app.temporal.activities.game_activities.engine_start_game", new_callable=AsyncMock)
async def test_start_game_activity_defaults_to_night(mock_start_game, mock_get_game_state):
    mock_get_game_state.return_value = {}

    result = await start_game_activity("room-1")

    assert result == "NIGHT"
    mock_start_game.assert_awaited_once_with("room-1")
    mock_get_game_state.assert_awaited_once_with("room-1")


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.gin_start_phase_timer", new_callable=AsyncMock)
async def test_start_phase_timer_activity_starts_timer(mock_start_timer):
    result = await start_phase_timer_activity("room-1", "DAY_DISCUSSION")

    assert result == 60
    mock_start_timer.assert_awaited_once_with("room-1", "DAY_DISCUSSION", 60)


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.gin_start_phase_timer", new_callable=AsyncMock)
async def test_start_phase_timer_activity_returns_zero_for_unknown_phase(mock_start_timer):
    result = await start_phase_timer_activity("room-1", "UNKNOWN_PHASE")

    assert result == 0
    mock_start_timer.assert_not_awaited()


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.gin_start_phase_timer", new_callable=AsyncMock)
async def test_start_phase_timer_activity_returns_zero_for_no_timer_phase(mock_start_timer):
    result = await start_phase_timer_activity("room-1", "")

    assert result == 0
    mock_start_timer.assert_not_awaited()


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.engine_get_game_state", new_callable=AsyncMock)
@patch("app.temporal.activities.game_activities.engine_advance_phase", new_callable=AsyncMock)
async def test_advance_phase_activity_returns_new_phase(mock_advance_phase, mock_get_game_state):
    mock_get_game_state.return_value = {"phase": "VOTING"}

    result = await advance_phase_activity("room-1")

    assert result == "VOTING"
    mock_advance_phase.assert_awaited_once_with("room-1")
    mock_get_game_state.assert_awaited_once_with("room-1")


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.engine_get_game_state", new_callable=AsyncMock)
@patch("app.temporal.activities.game_activities.engine_advance_phase", new_callable=AsyncMock)
async def test_advance_phase_activity_defaults_to_unknown(mock_advance_phase, mock_get_game_state):
    mock_get_game_state.return_value = {}

    result = await advance_phase_activity("room-1")

    assert result == "UNKNOWN"
    mock_advance_phase.assert_awaited_once_with("room-1")
    mock_get_game_state.assert_awaited_once_with("room-1")


@pytest.mark.asyncio
@patch.dict(os.environ, {"GIN_EVENT_BASE_URL": "http://gin-service:8081"}, clear=False)
@patch("app.temporal.activities.game_activities.httpx.AsyncClient")
async def test_cleanup_room_activity_calls_cancel_endpoint(mock_async_client):
    mock_client = MagicMock()
    mock_client.post = AsyncMock(return_value=MagicMock(status_code=200))
    mock_async_client.return_value.__aenter__.return_value = mock_client

    result = await cleanup_room_activity("room-1", "game ended")

    assert result is None
    mock_async_client.assert_called_once_with(
        base_url="http://gin-service:8081",
        timeout=5.0,
    )
    mock_client.post.assert_awaited_once_with("/api/phase/room-1/cancel")


@pytest.mark.asyncio
@patch.dict(os.environ, {}, clear=True)
@patch("app.temporal.activities.game_activities.httpx.AsyncClient")
async def test_cleanup_room_activity_uses_default_base_url(mock_async_client):
    mock_client = MagicMock()
    mock_client.post = AsyncMock(return_value=MagicMock(status_code=200))
    mock_async_client.return_value.__aenter__.return_value = mock_client

    await cleanup_room_activity("room-2", "timeout")

    mock_async_client.assert_called_once_with(
        base_url="http://mafia-event-service:8081/api",
        timeout=5.0,
    )
    mock_client.post.assert_awaited_once_with("/api/phase/room-2/cancel")


@pytest.mark.asyncio
@patch("app.temporal.activities.game_activities.httpx.AsyncClient")
async def test_cleanup_room_activity_swallows_exceptions(mock_async_client):
    mock_client = MagicMock()
    mock_client.post = AsyncMock(side_effect=Exception("network error"))
    mock_async_client.return_value.__aenter__.return_value = mock_client

    result = await cleanup_room_activity("room-3", "shutdown")

    assert result is None
    mock_client.post.assert_awaited_once_with("/api/phase/room-3/cancel")
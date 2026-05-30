import asyncio
import os
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.temporal.worker.worker import create_temporal_client, run_worker, TASK_QUEUE
from app.temporal.workflows.game_lifecycle import GameLifecycleWorkflow
from app.temporal.activities.game_activities import (
    start_game_activity,
    start_phase_timer_activity,
    advance_phase_activity,
    cleanup_room_activity,
)


@pytest.mark.asyncio
@patch("app.temporal.worker.worker.Client.connect", new_callable=AsyncMock)
async def test_create_temporal_client_uses_env_host(mock_connect):
    mock_client = object()
    mock_connect.return_value = mock_client

    with patch.dict(os.environ, {"TEMPORAL_HOST": "temporal-test:7233"}, clear=False):
        result = await create_temporal_client()

    assert result is mock_client
    mock_connect.assert_awaited_once_with("temporal-test:7233")


@pytest.mark.asyncio
@patch("app.temporal.worker.worker.Client.connect", new_callable=AsyncMock)
async def test_create_temporal_client_uses_default_host(mock_connect):
    mock_client = object()
    mock_connect.return_value = mock_client

    with patch.dict(os.environ, {}, clear=True):
        result = await create_temporal_client()

    assert result is mock_client
    mock_connect.assert_awaited_once_with("temporal:7233")


@pytest.mark.asyncio
@patch("app.temporal.worker.worker.asyncio.Event")
@patch("app.temporal.worker.worker.Worker")
async def test_run_worker_builds_worker_with_expected_config(mock_worker_cls, mock_event_cls):
    mock_client = object()

    mock_worker = MagicMock()
    mock_worker.__aenter__ = AsyncMock(return_value=mock_worker)
    mock_worker.__aexit__ = AsyncMock(return_value=None)
    mock_worker_cls.return_value = mock_worker

    mock_event = MagicMock()
    mock_event.wait = AsyncMock(side_effect=asyncio.CancelledError)
    mock_event_cls.return_value = mock_event

    with pytest.raises(asyncio.CancelledError):
        await run_worker(mock_client)

    mock_worker_cls.assert_called_once_with(
        mock_client,
        task_queue=TASK_QUEUE,
        workflows=[GameLifecycleWorkflow],
        activities=[
            start_game_activity,
            start_phase_timer_activity,
            advance_phase_activity,
            cleanup_room_activity,
        ],
    )
    mock_worker.__aenter__.assert_awaited_once()
    mock_event.wait.assert_awaited_once()
    mock_worker.__aexit__.assert_awaited_once()


@pytest.mark.asyncio
@patch("app.temporal.worker.worker.asyncio.Event")
@patch("app.temporal.worker.worker.Worker")
async def test_run_worker_enters_worker_context(mock_worker_cls, mock_event_cls):
    mock_client = object()

    mock_worker = MagicMock()
    mock_worker.__aenter__ = AsyncMock(return_value=mock_worker)
    mock_worker.__aexit__ = AsyncMock(return_value=None)
    mock_worker_cls.return_value = mock_worker

    mock_event = MagicMock()
    mock_event.wait = AsyncMock(side_effect=asyncio.CancelledError)
    mock_event_cls.return_value = mock_event

    with pytest.raises(asyncio.CancelledError):
        await run_worker(mock_client)

    mock_worker.__aenter__.assert_awaited_once()
    mock_worker.__aexit__.assert_awaited_once()


@pytest.mark.asyncio
@patch("app.temporal.worker.worker.asyncio.Event")
@patch("app.temporal.worker.worker.Worker")
async def test_run_worker_waits_forever_until_cancelled(mock_worker_cls, mock_event_cls):
    mock_client = object()

    mock_worker = MagicMock()
    mock_worker.__aenter__ = AsyncMock(return_value=mock_worker)
    mock_worker.__aexit__ = AsyncMock(return_value=None)
    mock_worker_cls.return_value = mock_worker

    mock_event = MagicMock()
    mock_event.wait = AsyncMock(side_effect=asyncio.CancelledError)
    mock_event_cls.return_value = mock_event

    with pytest.raises(asyncio.CancelledError):
        await run_worker(mock_client)

    mock_event_cls.assert_called_once()
    mock_event.wait.assert_awaited_once()
from unittest.mock import AsyncMock, MagicMock

import pytest
from temporalio.exceptions import WorkflowAlreadyStartedError

from app.temporal.client_helpers import (
    _handle,
    _workflow_id,
    query_room_state,
    signal_abandon_room,
    signal_game_ended,
    signal_game_started,
    signal_phase_advanced,
    start_lifecycle_workflow,
)
from app.temporal.worker.worker import TASK_QUEUE
from app.temporal.workflows.game_lifecycle import GameLifecycleWorkflow


def test_workflow_id_builds_expected_value():
    assert _workflow_id("room-1") == "game-lifecycle-room-1"


def test_handle_gets_workflow_handle():
    client = MagicMock()
    handle = MagicMock()
    client.get_workflow_handle.return_value = handle

    result = _handle(client, "room-1")

    assert result is handle
    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")


@pytest.mark.asyncio
async def test_start_lifecycle_workflow_starts_workflow():
    client = MagicMock()
    client.start_workflow = AsyncMock()

    await start_lifecycle_workflow(
        client=client,
        room_id="room-1",
        room_code="CODE01",
        host_username="host1",
    )

    client.start_workflow.assert_awaited_once_with(
        GameLifecycleWorkflow.run,
        args=["room-1", "CODE01", "host1"],
        id="game-lifecycle-room-1",
        task_queue=TASK_QUEUE,
    )


@pytest.mark.asyncio
async def test_start_lifecycle_workflow_ignores_already_started():
    client = MagicMock()
    client.start_workflow = AsyncMock(
        side_effect=WorkflowAlreadyStartedError(
            workflow_id="game-lifecycle-room-1",
            workflow_type="GameLifecycleWorkflow",
            run_id="run-1",
        )
    )

    await start_lifecycle_workflow(
        client=client,
        room_id="room-1",
        room_code="CODE01",
        host_username="host1",
    )

    client.start_workflow.assert_awaited_once()


@pytest.mark.asyncio
async def test_signal_game_started():
    client = MagicMock()
    handle = MagicMock()
    handle.signal = AsyncMock()
    client.get_workflow_handle.return_value = handle

    await signal_game_started(
        client=client,
        room_id="room-1",
        first_phase="NIGHT",
        host_username="host1",
        room_code="CODE01",
    )

    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.signal.assert_awaited_once()

    signal_call = handle.signal.await_args
    assert signal_call.args[0] == GameLifecycleWorkflow.on_game_started
    payload = signal_call.args[1]
    assert payload.first_phase == "NIGHT"
    assert payload.host_username == "host1"
    assert payload.room_code == "CODE01"


@pytest.mark.asyncio
async def test_signal_phase_advanced():
    client = MagicMock()
    handle = MagicMock()
    handle.signal = AsyncMock()
    client.get_workflow_handle.return_value = handle

    await signal_phase_advanced(
        client=client,
        room_id="room-1",
        new_phase="VOTING",
    )

    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.signal.assert_awaited_once()

    signal_call = handle.signal.await_args
    assert signal_call.args[0] == GameLifecycleWorkflow.on_phase_advanced
    payload = signal_call.args[1]
    assert payload.new_phase == "VOTING"


@pytest.mark.asyncio
async def test_signal_game_ended():
    client = MagicMock()
    handle = MagicMock()
    handle.signal = AsyncMock()
    client.get_workflow_handle.return_value = handle

    await signal_game_ended(
        client=client,
        room_id="room-1",
        winner="MAFIA",
    )

    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.signal.assert_awaited_once()

    signal_call = handle.signal.await_args
    assert signal_call.args[0] == GameLifecycleWorkflow.on_game_ended
    payload = signal_call.args[1]
    assert payload.winner == "MAFIA"


@pytest.mark.asyncio
async def test_signal_abandon_room_uses_default_reason():
    client = MagicMock()
    handle = MagicMock()
    handle.signal = AsyncMock()
    client.get_workflow_handle.return_value = handle

    await signal_abandon_room(
        client=client,
        room_id="room-1",
    )

    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.signal.assert_awaited_once()

    signal_call = handle.signal.await_args
    assert signal_call.args[0] == GameLifecycleWorkflow.on_abandon_room
    payload = signal_call.args[1]
    assert payload.reason == "players_dropped"


@pytest.mark.asyncio
async def test_signal_abandon_room_uses_custom_reason():
    client = MagicMock()
    handle = MagicMock()
    handle.signal = AsyncMock()
    client.get_workflow_handle.return_value = handle

    await signal_abandon_room(
        client=client,
        room_id="room-1",
        reason="host_left",
    )

    signal_call = handle.signal.await_args
    assert signal_call.args[0] == GameLifecycleWorkflow.on_abandon_room
    payload = signal_call.args[1]
    assert payload.reason == "host_left"


@pytest.mark.asyncio
async def test_query_room_state_returns_result():
    client = MagicMock()
    handle = MagicMock()
    handle.query = AsyncMock(
        return_value={
            "room_id": "room-1",
            "phase": "NIGHT",
            "is_active": True,
        }
    )
    client.get_workflow_handle.return_value = handle

    result = await query_room_state(client, "room-1")

    assert result == {
        "room_id": "room-1",
        "phase": "NIGHT",
        "is_active": True,
    }
    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.query.assert_awaited_once_with(GameLifecycleWorkflow.get_room_state)


@pytest.mark.asyncio
async def test_query_room_state_returns_empty_dict_on_error():
    client = MagicMock()
    handle = MagicMock()
    handle.query = AsyncMock(side_effect=Exception("workflow not found"))
    client.get_workflow_handle.return_value = handle

    result = await query_room_state(client, "room-1")

    assert result == {}
    client.get_workflow_handle.assert_called_once_with("game-lifecycle-room-1")
    handle.query.assert_awaited_once_with(GameLifecycleWorkflow.get_room_state)
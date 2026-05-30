import uuid

import pytest
from temporalio import activity
from temporalio.client import WorkflowHandle
from temporalio.testing import WorkflowEnvironment
from temporalio.worker import Worker

from app.temporal.models import (
    AbandonRoomSignal,
    GameEndedSignal,
    GameStartedSignal,
    PhaseAdvancedSignal,
)
from app.temporal.workflows.game_lifecycle import GameLifecycleWorkflow


@activity.defn(name="start_phase_timer_activity")
async def fake_start_phase_timer_activity(room_id: str, phase: str) -> int:
    return 30


@activity.defn(name="cleanup_room_activity")
async def fake_cleanup_room_activity(room_id: str, reason: str) -> None:
    return None


@pytest.fixture
async def workflow_env():
    async with await WorkflowEnvironment.start_time_skipping() as env:
        yield env


@pytest.fixture
async def workflow_handle(workflow_env) -> WorkflowHandle:
    task_queue = f"test-task-queue-{uuid.uuid4()}"

    async with Worker(
        workflow_env.client,
        task_queue=task_queue,
        workflows=[GameLifecycleWorkflow],
        activities=[fake_start_phase_timer_activity, fake_cleanup_room_activity],
    ):
        handle = await workflow_env.client.start_workflow(
            GameLifecycleWorkflow.run,
            args=["room-1", "CODE01", "host1"],
            id=f"wf-{uuid.uuid4()}",
            task_queue=task_queue,
        )
        yield handle


@pytest.mark.asyncio
async def test_initial_query_returns_room_state(workflow_handle: WorkflowHandle):
    state = await workflow_handle.query("get_room_state")

    assert state["room_id"] == "room-1"
    assert state["room_code"] == "CODE01"
    assert state["host_username"] == "host1"
    assert state["phase"] == "LOBBY"
    assert state["winner"] == "NONE"
    assert state["is_active"] is False
    assert state["is_game_over"] is False
    assert state["phase_history"] == []


@pytest.mark.asyncio
async def test_workflow_starts_and_tracks_first_phase(workflow_handle: WorkflowHandle):
    await workflow_handle.signal(
        "game_started",
        GameStartedSignal(
            host_username="host1",
            room_code="CODE01",
            first_phase="NIGHT",
        ),
    )

    state = await workflow_handle.query("get_room_state")

    assert state["phase"] == "NIGHT"
    assert state["is_active"] is True
    assert state["phase_history"] == ["NIGHT"]


@pytest.mark.asyncio
async def test_phase_advanced_signal_does_not_change_phase_immediately(workflow_handle: WorkflowHandle):
    await workflow_handle.signal(
        "game_started",
        GameStartedSignal(
            host_username="host1",
            room_code="CODE01",
            first_phase="NIGHT",
        ),
    )

    await workflow_handle.signal(
        "phase_advanced",
        PhaseAdvancedSignal(new_phase="DAY_DISCUSSION"),
    )

    state = await workflow_handle.query("get_room_state")

    assert state["phase"] == "NIGHT"
    assert state["phase_history"] == ["NIGHT"]


@pytest.mark.asyncio
async def test_game_ended_finishes_workflow(workflow_env):
    task_queue = f"test-task-queue-{uuid.uuid4()}"

    async with Worker(
        workflow_env.client,
        task_queue=task_queue,
        workflows=[GameLifecycleWorkflow],
        activities=[fake_start_phase_timer_activity, fake_cleanup_room_activity],
    ):
        handle = await workflow_env.client.start_workflow(
            GameLifecycleWorkflow.run,
            args=["room-1", "CODE01", "host1"],
            id=f"wf-{uuid.uuid4()}",
            task_queue=task_queue,
        )

        await handle.signal(
            "game_started",
            GameStartedSignal(
                host_username="host1",
                room_code="CODE01",
                first_phase="NIGHT",
            ),
        )

        await handle.signal(
            "game_ended",
            GameEndedSignal(winner="MAFIA"),
        )

        result = await handle.result()
        assert result == "MAFIA"

        state = await handle.query("get_room_state")
        assert state["phase"] == "GAME_OVER"
        assert state["winner"] == "MAFIA"
        assert state["is_game_over"] is True


@pytest.mark.asyncio
async def test_abandon_before_game_started_returns_abandoned(workflow_env):
    task_queue = f"test-task-queue-{uuid.uuid4()}"

    async with Worker(
        workflow_env.client,
        task_queue=task_queue,
        workflows=[GameLifecycleWorkflow],
        activities=[fake_start_phase_timer_activity, fake_cleanup_room_activity],
    ):
        handle = await workflow_env.client.start_workflow(
            GameLifecycleWorkflow.run,
            args=["room-1", "CODE01", "host1"],
            id=f"wf-{uuid.uuid4()}",
            task_queue=task_queue,
        )

        await handle.signal(
            "abandon_room",
            AbandonRoomSignal(reason="host-left"),
        )

        result = await handle.result()
        assert result == "ABANDONED"


@pytest.mark.asyncio
async def test_abandon_after_game_started_marks_inactive(workflow_env):
    task_queue = f"test-task-queue-{uuid.uuid4()}"

    async with Worker(
        workflow_env.client,
        task_queue=task_queue,
        workflows=[GameLifecycleWorkflow],
        activities=[fake_start_phase_timer_activity, fake_cleanup_room_activity],
    ):
        handle = await workflow_env.client.start_workflow(
            GameLifecycleWorkflow.run,
            args=["room-1", "CODE01", "host1"],
            id=f"wf-{uuid.uuid4()}",
            task_queue=task_queue,
        )

        await handle.signal(
            "game_started",
            GameStartedSignal(
                host_username="host1",
                room_code="CODE01",
                first_phase="NIGHT",
            ),
        )

        await handle.signal(
            "abandon_room",
            AbandonRoomSignal(reason="room-empty"),
        )

        result = await handle.result()
        assert result == "ABANDONED"

        state = await handle.query("get_room_state")
        assert state["phase"] == "ABANDONED"
        assert state["is_active"] is False


@pytest.mark.asyncio
async def test_game_over_phase_without_winner_returns_none(workflow_env):
    task_queue = f"test-task-queue-{uuid.uuid4()}"

    async with Worker(
        workflow_env.client,
        task_queue=task_queue,
        workflows=[GameLifecycleWorkflow],
        activities=[fake_start_phase_timer_activity, fake_cleanup_room_activity],
    ):
        handle = await workflow_env.client.start_workflow(
            GameLifecycleWorkflow.run,
            args=["room-1", "CODE01", "host1"],
            id=f"wf-{uuid.uuid4()}",
            task_queue=task_queue,
        )

        await handle.signal(
            "game_started",
            GameStartedSignal(
                host_username="host1",
                room_code="CODE01",
                first_phase="NIGHT",
            ),
        )

        await handle.signal(
            "phase_advanced",
            PhaseAdvancedSignal(new_phase="GAME_OVER"),
        )

        result = await handle.result()
        assert result == "NONE"
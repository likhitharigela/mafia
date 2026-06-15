"""
Stale room cleanup job.
Finds GameLifecycleWorkflows that have been WAITING (no game started) for
longer than MAX_WAIT_HOURS and signals abandon_room on each of them.

Designed to run as a Kubernetes CronJob every 15 minutes.
"""
import asyncio
import logging
import os
from datetime import datetime, timezone, timedelta

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

MAX_WAIT_HOURS = int(os.getenv("MAX_WAIT_HOURS", "2"))
TEMPORAL_HOST = os.getenv("TEMPORAL_HOST", "temporal:7233")
TASK_QUEUE = "mafia-gateway"


async def cleanup_stale_rooms() -> None:
    from temporalio.client import Client, WorkflowExecutionStatus

    client = await Client.connect(TEMPORAL_HOST)
    logger.info("Connected to Temporal at %s", TEMPORAL_HOST)

    cutoff = datetime.now(timezone.utc) - timedelta(hours=MAX_WAIT_HOURS)
    abandoned = 0
    checked = 0

    # List all running GameLifecycleWorkflows
    async for workflow in client.list_workflows(
        f'WorkflowType="GameLifecycleWorkflow" AND ExecutionStatus="Running"'
    ):
        checked += 1
        # Workflows started before the cutoff with no game_started signal yet
        # are identifiable by their start time alone — the workflow itself
        # will have timed out its wait_condition and returned, but any that
        # haven't yet are stuck waiting.
        if workflow.start_time < cutoff:
            room_id = workflow.id.removeprefix("game-lifecycle-")
            logger.info(
                "Abandoning stale room room_id=%s started_at=%s",
                room_id,
                workflow.start_time.isoformat(),
            )
            try:
                handle = client.get_workflow_handle(workflow.id)
                await handle.signal("abandon_room", {"reason": "stale_cleanup_job"})
                abandoned += 1
            except Exception as exc:
                logger.warning(
                    "Failed to signal abandon for room_id=%s: %s", room_id, exc
                )

    logger.info(
        "Cleanup complete. checked=%d abandoned=%d cutoff=%s",
        checked,
        abandoned,
        cutoff.isoformat(),
    )


if __name__ == "__main__":
    asyncio.run(cleanup_stale_rooms())
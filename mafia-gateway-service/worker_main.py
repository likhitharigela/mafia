"""
Standalone Temporal worker entrypoint for the gateway service.
Run as a separate container/pod — does not start the HTTP server.
"""
import asyncio
import logging
import os

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


async def main() -> None:
    from app.temporal.worker.worker import create_temporal_client, run_worker

    logger.info("Gateway Temporal worker starting...")
    client = await create_temporal_client()
    logger.info("Connected to Temporal, polling for tasks...")
    await run_worker(client)


if __name__ == "__main__":
    asyncio.run(main())
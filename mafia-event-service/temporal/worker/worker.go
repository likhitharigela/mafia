package worker

import (
	"log"
	"os"

	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/worker"

	"github.com/example/mafia-event-service/temporal/activities"
	"github.com/example/mafia-event-service/temporal/workflows"
)

const TaskQueue = "mafia-phase-timers"

var dialClient = client.Dial
var newWorker = func(c client.Client, taskQueue string, opts worker.Options) worker.Worker {
	return worker.New(c, taskQueue, opts)
}
var interruptCh = worker.InterruptCh

func Start() (client.Client, error) {
	temporalHost := os.Getenv("TEMPORAL_HOST")
	if temporalHost == "" {
		temporalHost = "temporal:7233"
	}

	c, err := dialClient(client.Options{HostPort: temporalHost})
	if err != nil {
		return nil, err
	}

	w := newWorker(c, TaskQueue, worker.Options{})
	w.RegisterWorkflow(workflows.PhaseTimerWorkflow)
	w.RegisterActivity(activities.AdvancePhase)

	go func() {
		if err := w.Run(interruptCh()); err != nil {
			log.Fatalf("[TemporalWorker] worker stopped: %v", err)
		}
	}()

	log.Printf("[TemporalWorker] started on task_queue=%s host=%s", TaskQueue, temporalHost)
	return c, nil
}
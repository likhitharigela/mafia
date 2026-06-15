package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	temporalworker "github.com/example/mafia-event-service/temporal/worker"
)

func main() {
	c, err := temporalworker.Start()
	if err != nil {
		log.Fatalf("[Worker] failed to start Temporal worker: %v", err)
	}
	defer c.Close()

	log.Println("[Worker] Temporal worker running. Waiting for tasks...")

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("[Worker] Shutting down...")
}	
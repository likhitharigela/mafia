FROM golang:1.25 AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o event-service ./main.go
RUN CGO_ENABLED=0 GOOS=linux go build -o event-worker ./cmd/worker

FROM gcr.io/distroless/static-debian12 AS server
WORKDIR /app
COPY --from=builder /app/event-service .
CMD ["./event-service"]

FROM gcr.io/distroless/static-debian12 AS worker
WORKDIR /app
COPY --from=builder /app/event-worker .
CMD ["./event-worker"]
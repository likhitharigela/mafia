package controller

import (
	"net/http"
	"testing"

	"github.com/example/mafia-event-service/internal/service"
	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
)

func setupTimerRouter() *gin.Engine {
	gin.SetMode(gin.TestMode)
	tm := service.NewTimerManager(&MockTemporalClient{})
	es := service.NewEventStore()
	r := gin.New()
	controller := NewTimerController(tm, es)
	controller.RegisterTimerRoutes(r)
	return r
}

func TestHealthWhenHealthEndpointIsCalled(t *testing.T) {
	r := setupTimerRouter()
	w := getRequest(r, "/api/health")
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestGetTimerSnapshotWhenCalledWithValidRoomID(t *testing.T) {
	r := setupTimerRouter()
	w := getRequest(r, "/api/timer/room1")
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestGetEventFeedWhenCalledWithValidRoomID(t *testing.T) {
	r := setupTimerRouter()
	w := getRequest(r, "/api/events/room1")
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestPushEvent(t *testing.T) {
	tests := []struct {
		name           string
		body           []byte
		expectedStatus int
	}{
		{
			name:           "TestShouldPushEventSuccessfullyWithValidInput",
			body:           []byte(`{"type":"VOTE","description":"Bob voted Alice"}`),
			expectedStatus: http.StatusOK,
		},
		{
			name:           "TestShouldReturnBadRequestWhenTypeIsMissing",
			body:           []byte(`{"description":"Bob voted Alice"}`),
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "TestShouldReturnBadRequestWithMalformedJSON",
			body:           []byte(`{JokeJSON}`),
			expectedStatus: http.StatusBadRequest,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := setupTimerRouter()
			w := postJSON(r, "/api/events/room1", tt.body)
			assert.Equal(t, tt.expectedStatus, w.Code)
		})
	}
}
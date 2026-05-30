package activities

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.temporal.io/sdk/testsuite"
)

func TestAdvancePhase(t *testing.T) {
	tests := []struct {
		name          string
		gatewayURL    string
		input         AdvancePhaseInput
		serverStatus  int
		expectError   bool
		errorContains string
		verifyRequest bool
	}{
		{
			name:          "TestShouldReturnErrorWhenGatewayBaseURLIsMissing",
			gatewayURL:    "",
			input:         AdvancePhaseInput{RoomID: "room1", Phase: "NIGHT"},
			expectError:   true,
			errorContains: "GATEWAY_BASE_URL not set",
		},
		{
			name:          "TestShouldReturnErrorWhenGatewayURLIsInvalid",
			gatewayURL:    "://bad-url",
			input:         AdvancePhaseInput{RoomID: "room1", Phase: "NIGHT"},
			expectError:   true,
			errorContains: "build request",
		},
		{
			name:          "TestShouldReturnErrorWhenGatewayReturnsNon2xx",
			input:         AdvancePhaseInput{RoomID: "room1", Phase: "NIGHT"},
			serverStatus:  http.StatusInternalServerError,
			expectError:   true,
			errorContains: "returned HTTP 500",
			verifyRequest: true,
		},
		{
			name:          "TestShouldSucceedWhenGatewayReturns2xx",
			input:         AdvancePhaseInput{RoomID: "room1", Phase: "NIGHT"},
			serverStatus:  http.StatusOK,
			expectError:   false,
			verifyRequest: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var server *httptest.Server

			if tt.gatewayURL == "" && tt.errorContains == "GATEWAY_BASE_URL not set" {
				t.Setenv("GATEWAY_BASE_URL", "")
			} else if tt.gatewayURL != "" {
				t.Setenv("GATEWAY_BASE_URL", tt.gatewayURL)
			} else {
				server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
					if tt.verifyRequest {
						assert.Equal(t, http.MethodPost, r.Method)
						assert.Equal(t, "/internal/advance-phase", r.URL.Path)
						assert.Equal(t, "application/json", r.Header.Get("Content-Type"))

						var body map[string]string
						require.NoError(t, json.NewDecoder(r.Body).Decode(&body))
						assert.Equal(t, tt.input.RoomID, body["room_id"])
					}
					w.WriteHeader(tt.serverStatus)
				}))
				defer server.Close()

				t.Setenv("GATEWAY_BASE_URL", server.URL)
			}

			var ts testsuite.WorkflowTestSuite
			env := ts.NewTestActivityEnvironment()
			env.RegisterActivity(AdvancePhase)

			_, err := env.ExecuteActivity(AdvancePhase, tt.input)

			if tt.expectError {
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.errorContains)
			} else {
				require.NoError(t, err)
			}
		})
	}
}
package workflows

import (
	"errors"
	"testing"
	"time"

	"github.com/example/mafia-event-service/temporal/activities"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.temporal.io/sdk/testsuite"
	"github.com/stretchr/testify/mock"
)

func TestPhaseTimerWorkflow(t *testing.T) {
	tests := []struct {
		name          string
		input         PhaseTimerInput
		cancelBefore  bool
		activityErr   error
		expectError   bool
		errorContains string
	}{
		{
			name:        "TestShouldExecuteAdvancePhaseWhenTimerExpires",
			input:       PhaseTimerInput{RoomID: "room1", Phase: "NIGHT", DurationSec: 1},
			expectError: false,
		},
		{
			name:         "TestShouldReturnNilWhenWorkflowIsCancelledBeforeTimerExpires",
			input:        PhaseTimerInput{RoomID: "room1", Phase: "DAY", DurationSec: 10},
			cancelBefore: true,
			expectError:  false,
		},
		{
			name:          "TestShouldReturnErrorWhenAdvancePhaseActivityFails",
			input:         PhaseTimerInput{RoomID: "room1", Phase: "VOTING", DurationSec: 1},
			activityErr:   errors.New("advance failed"),
			expectError:   true,
			errorContains: "advance failed",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var ts testsuite.WorkflowTestSuite
			env := ts.NewTestWorkflowEnvironment()

			if !tt.cancelBefore {
				if tt.activityErr != nil {
					env.OnActivity(
						activities.AdvancePhase,
						mock.Anything,
						activities.AdvancePhaseInput{
							RoomID: tt.input.RoomID,
							Phase:  tt.input.Phase,
						},
					).Return(tt.activityErr)
				} else {
					env.OnActivity(
						activities.AdvancePhase,
						mock.Anything,
						activities.AdvancePhaseInput{
							RoomID: tt.input.RoomID,
							Phase:  tt.input.Phase,
						},
					).Return(nil)
				}
			}

			if tt.cancelBefore {
				env.RegisterDelayedCallback(func() {
					env.CancelWorkflow()
				}, time.Second)
			}

			env.ExecuteWorkflow(PhaseTimerWorkflow, tt.input)

			require.True(t, env.IsWorkflowCompleted())

			if tt.expectError {
				require.Error(t, env.GetWorkflowError())
				assert.Contains(t, env.GetWorkflowError().Error(), tt.errorContains)
			} else {
				require.NoError(t, env.GetWorkflowError())
			}
		})
	}
}
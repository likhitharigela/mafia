package worker

import (
	"context"
	"errors"
	"testing"

	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/api/operatorservice/v1"
	"go.temporal.io/api/workflowservice/v1"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/converter"
	sdkworker "go.temporal.io/sdk/worker"
	"go.temporal.io/sdk/activity"
	"go.temporal.io/sdk/workflow"
	"github.com/nexus-rpc/sdk-go/nexus"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockTemporalClient struct{}
type mockWorkflowRun struct{}

func (r *mockWorkflowRun) GetID() string                                                                          { return "mock-wf-id" }
func (r *mockWorkflowRun) GetRunID() string                                                                       { return "mock-run-id" }
func (r *mockWorkflowRun) Get(_ context.Context, _ interface{}) error                                             { return nil }
func (r *mockWorkflowRun) GetWithOptions(_ context.Context, _ interface{}, _ client.WorkflowRunGetOptions) error { return nil }

func (m *mockTemporalClient) ExecuteWorkflow(_ context.Context, _ client.StartWorkflowOptions, _ interface{}, _ ...interface{}) (client.WorkflowRun, error) {
	return &mockWorkflowRun{}, nil
}
func (m *mockTemporalClient) GetWorkflow(_ context.Context, _, _ string) client.WorkflowRun {
	return &mockWorkflowRun{}
}
func (m *mockTemporalClient) SignalWorkflow(_ context.Context, _, _, _ string, _ interface{}) error {
	return nil
}
func (m *mockTemporalClient) SignalWithStartWorkflow(_ context.Context, _ string, _ string, _ interface{}, _ client.StartWorkflowOptions, _ interface{}, _ ...interface{}) (client.WorkflowRun, error) {
	return &mockWorkflowRun{}, nil
}
func (m *mockTemporalClient) CancelWorkflow(_ context.Context, _, _ string) error { return nil }
func (m *mockTemporalClient) TerminateWorkflow(_ context.Context, _, _, _ string, _ ...interface{}) error {
	return nil
}
func (m *mockTemporalClient) GetWorkflowHistory(_ context.Context, _, _ string, _ bool, _ enumspb.HistoryEventFilterType) client.HistoryEventIterator {
	return nil
}
func (m *mockTemporalClient) CompleteActivity(_ context.Context, _ []byte, _ interface{}, _ error) error {
	return nil
}
func (m *mockTemporalClient) CompleteActivityByID(_ context.Context, _, _, _, _ string, _ interface{}, _ error) error {
	return nil
}
func (m *mockTemporalClient) RecordActivityHeartbeat(_ context.Context, _ []byte, _ ...interface{}) error {
	return nil
}
func (m *mockTemporalClient) RecordActivityHeartbeatByID(_ context.Context, _, _, _, _ string, _ ...interface{}) error {
	return nil
}
func (m *mockTemporalClient) ListClosedWorkflow(_ context.Context, _ *workflowservice.ListClosedWorkflowExecutionsRequest) (*workflowservice.ListClosedWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) ListOpenWorkflow(_ context.Context, _ *workflowservice.ListOpenWorkflowExecutionsRequest) (*workflowservice.ListOpenWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) ListWorkflow(_ context.Context, _ *workflowservice.ListWorkflowExecutionsRequest) (*workflowservice.ListWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) ListArchivedWorkflow(_ context.Context, _ *workflowservice.ListArchivedWorkflowExecutionsRequest) (*workflowservice.ListArchivedWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) ScanWorkflow(_ context.Context, _ *workflowservice.ScanWorkflowExecutionsRequest) (*workflowservice.ScanWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) CountWorkflow(_ context.Context, _ *workflowservice.CountWorkflowExecutionsRequest) (*workflowservice.CountWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) GetSearchAttributes(_ context.Context) (*workflowservice.GetSearchAttributesResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) QueryWorkflow(_ context.Context, _, _, _ string, _ ...interface{}) (converter.EncodedValue, error) {
	return nil, nil
}
func (m *mockTemporalClient) QueryWorkflowWithOptions(_ context.Context, _ *client.QueryWorkflowWithOptionsRequest) (*client.QueryWorkflowWithOptionsResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) DescribeWorkflowExecution(_ context.Context, _, _ string) (*workflowservice.DescribeWorkflowExecutionResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) DescribeTaskQueue(_ context.Context, _ string, _ enumspb.TaskQueueType) (*workflowservice.DescribeTaskQueueResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) DescribeTaskQueueEnhanced(_ context.Context, _ client.DescribeTaskQueueEnhancedOptions) (client.TaskQueueDescription, error) {
	return client.TaskQueueDescription{}, nil
}
func (m *mockTemporalClient) ResetWorkflowExecution(_ context.Context, _ *workflowservice.ResetWorkflowExecutionRequest) (*workflowservice.ResetWorkflowExecutionResponse, error) {
	return nil, nil
}
func (m *mockTemporalClient) UpdateWorkerBuildIdCompatibility(_ context.Context, _ *client.UpdateWorkerBuildIdCompatibilityOptions) error {
	return nil
}
func (m *mockTemporalClient) GetWorkerBuildIdCompatibility(_ context.Context, _ *client.GetWorkerBuildIdCompatibilityOptions) (*client.WorkerBuildIDVersionSets, error) {
	return nil, nil
}
func (m *mockTemporalClient) GetWorkerTaskReachability(_ context.Context, _ *client.GetWorkerTaskReachabilityOptions) (*client.WorkerTaskReachability, error) {
	return nil, nil
}
func (m *mockTemporalClient) UpdateWorkerVersioningRules(_ context.Context, _ client.UpdateWorkerVersioningRulesOptions) (*client.WorkerVersioningRules, error) {
	return nil, nil
}
func (m *mockTemporalClient) GetWorkerVersioningRules(_ context.Context, _ client.GetWorkerVersioningOptions) (*client.WorkerVersioningRules, error) {
	return nil, nil
}
func (m *mockTemporalClient) CheckHealth(_ context.Context, _ *client.CheckHealthRequest) (*client.CheckHealthResponse, error) {
	return &client.CheckHealthResponse{}, nil
}
func (m *mockTemporalClient) UpdateWorkflow(_ context.Context, _ client.UpdateWorkflowOptions) (client.WorkflowUpdateHandle, error) {
	return nil, nil
}
func (m *mockTemporalClient) GetWorkflowUpdateHandle(_ client.GetWorkflowUpdateHandleOptions) client.WorkflowUpdateHandle {
	return nil
}
func (m *mockTemporalClient) WorkflowService() workflowservice.WorkflowServiceClient { return nil }
func (m *mockTemporalClient) OperatorService() operatorservice.OperatorServiceClient { return nil }
func (m *mockTemporalClient) ScheduleClient() client.ScheduleClient                  { return nil }
func (m *mockTemporalClient) Close()                                                 {}

type mockWorker struct {
	runCalled              bool
	registerWorkflowCalled bool
	registerActivityCalled bool
	runErr                 error
}

func (m *mockWorker) Start() error { return nil }

func (m *mockWorker) Run(ch <-chan interface{}) error {
	m.runCalled = true
	return m.runErr
}

func (m *mockWorker) Stop() {}

func (m *mockWorker) RegisterWorkflow(w interface{}) {
	m.registerWorkflowCalled = true
}

func (m *mockWorker) RegisterWorkflowWithOptions(w interface{}, options workflow.RegisterOptions) {
	m.registerWorkflowCalled = true
}

func (m *mockWorker) RegisterActivity(a interface{}) {
	m.registerActivityCalled = true
}

func (m *mockWorker) RegisterActivityWithOptions(a interface{}, options activity.RegisterOptions) {
	m.registerActivityCalled = true
}

func (m *mockWorker) RegisterNexusService(service *nexus.Service) {
	// no-op
}

func TestStart(t *testing.T) {
	originalDial := dialClient
	originalNewWorker := newWorker
	originalInterruptCh := interruptCh
	defer func() {
		dialClient = originalDial
		newWorker = originalNewWorker
		interruptCh = originalInterruptCh
	}()

	tests := []struct {
		name         string
		tempHost     string
		dialErr      error
		expectedHost string
		expectError  bool
	}{
		{
			name:         "TestShouldUseDefaultHostWhenEnvVarMissing",
			tempHost:     "",
			expectedHost: "temporal:7233",
			expectError:  false,
		},
		{
			name:         "TestShouldUseEnvHostWhenProvided",
			tempHost:     "localhost:7233",
			expectedHost: "localhost:7233",
			expectError:  false,
		},
		{
			name:         "TestShouldReturnErrorWhenDialFails",
			tempHost:     "bad-host:7233",
			expectedHost: "bad-host:7233",
			dialErr:      errors.New("dial failed"),
			expectError:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.tempHost == "" {
				t.Setenv("TEMPORAL_HOST", "")
			} else {
				t.Setenv("TEMPORAL_HOST", tt.tempHost)
			}

			var capturedHost string
			mockC := &mockTemporalClient{}
			mockW := &mockWorker{}

			dialClient = func(opts client.Options) (client.Client, error) {
				capturedHost = opts.HostPort
				if tt.dialErr != nil {
					return nil, tt.dialErr
				}
				return mockC, nil
			}

			newWorker = func(c client.Client, taskQueue string, opts sdkworker.Options) sdkworker.Worker {
				assert.Equal(t, mockC, c)
				assert.Equal(t, TaskQueue, taskQueue)
				return mockW
			}

			interruptCh = func() <-chan interface{} {
				ch := make(chan interface{})
				close(ch)
				return ch
			}

			gotClient, err := Start()

			assert.Equal(t, tt.expectedHost, capturedHost)

			if tt.expectError {
				require.Error(t, err)
				assert.Nil(t, gotClient)
				return
			}

			require.NoError(t, err)
			assert.Equal(t, mockC, gotClient)
			assert.True(t, mockW.registerWorkflowCalled)
			assert.True(t, mockW.registerActivityCalled)
		})
	}
}
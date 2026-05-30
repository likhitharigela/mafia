package controller

import (
	"context"

	enumspb "go.temporal.io/api/enums/v1"
	"go.temporal.io/api/operatorservice/v1"
	"go.temporal.io/api/workflowservice/v1"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/converter"
)

type MockWorkflowRun struct{}

func (r *MockWorkflowRun) GetID() string                                                                         { return "mock-wf-id" }
func (r *MockWorkflowRun) GetRunID() string                                                                      { return "mock-run-id" }
func (r *MockWorkflowRun) Get(_ context.Context, _ interface{}) error                                            { return nil }
func (r *MockWorkflowRun) GetWithOptions(_ context.Context, _ interface{}, _ client.WorkflowRunGetOptions) error { return nil }

type MockTemporalClient struct{}

func (m *MockTemporalClient) ExecuteWorkflow(_ context.Context, _ client.StartWorkflowOptions, _ interface{}, _ ...interface{}) (client.WorkflowRun, error) {
	return &MockWorkflowRun{}, nil
}
func (m *MockTemporalClient) GetWorkflow(_ context.Context, _, _ string) client.WorkflowRun {
	return &MockWorkflowRun{}
}
func (m *MockTemporalClient) SignalWorkflow(_ context.Context, _, _, _ string, _ interface{}) error {
	return nil
}
func (m *MockTemporalClient) SignalWithStartWorkflow(_ context.Context, _ string, _ string, _ interface{}, _ client.StartWorkflowOptions, _ interface{}, _ ...interface{}) (client.WorkflowRun, error) {
	return &MockWorkflowRun{}, nil
}
func (m *MockTemporalClient) CancelWorkflow(_ context.Context, _, _ string) error { return nil }
func (m *MockTemporalClient) TerminateWorkflow(_ context.Context, _, _, _ string, _ ...interface{}) error {
	return nil
}
func (m *MockTemporalClient) GetWorkflowHistory(_ context.Context, _, _ string, _ bool, _ enumspb.HistoryEventFilterType) client.HistoryEventIterator {
	return nil
}
func (m *MockTemporalClient) CompleteActivity(_ context.Context, _ []byte, _ interface{}, _ error) error {
	return nil
}
func (m *MockTemporalClient) CompleteActivityByID(_ context.Context, _, _, _, _ string, _ interface{}, _ error) error {
	return nil
}
func (m *MockTemporalClient) RecordActivityHeartbeat(_ context.Context, _ []byte, _ ...interface{}) error {
	return nil
}
func (m *MockTemporalClient) RecordActivityHeartbeatByID(_ context.Context, _, _, _, _ string, _ ...interface{}) error {
	return nil
}
func (m *MockTemporalClient) ListClosedWorkflow(_ context.Context, _ *workflowservice.ListClosedWorkflowExecutionsRequest) (*workflowservice.ListClosedWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) ListOpenWorkflow(_ context.Context, _ *workflowservice.ListOpenWorkflowExecutionsRequest) (*workflowservice.ListOpenWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) ListWorkflow(_ context.Context, _ *workflowservice.ListWorkflowExecutionsRequest) (*workflowservice.ListWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) ListArchivedWorkflow(_ context.Context, _ *workflowservice.ListArchivedWorkflowExecutionsRequest) (*workflowservice.ListArchivedWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) ScanWorkflow(_ context.Context, _ *workflowservice.ScanWorkflowExecutionsRequest) (*workflowservice.ScanWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) CountWorkflow(_ context.Context, _ *workflowservice.CountWorkflowExecutionsRequest) (*workflowservice.CountWorkflowExecutionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) GetSearchAttributes(_ context.Context) (*workflowservice.GetSearchAttributesResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) QueryWorkflow(_ context.Context, _, _, _ string, _ ...interface{}) (converter.EncodedValue, error) {
	return nil, nil
}
func (m *MockTemporalClient) QueryWorkflowWithOptions(_ context.Context, _ *client.QueryWorkflowWithOptionsRequest) (*client.QueryWorkflowWithOptionsResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) DescribeWorkflowExecution(_ context.Context, _, _ string) (*workflowservice.DescribeWorkflowExecutionResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) DescribeTaskQueue(_ context.Context, _ string, _ enumspb.TaskQueueType) (*workflowservice.DescribeTaskQueueResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) DescribeTaskQueueEnhanced(_ context.Context, _ client.DescribeTaskQueueEnhancedOptions) (client.TaskQueueDescription, error) {
	return client.TaskQueueDescription{}, nil
}
func (m *MockTemporalClient) ResetWorkflowExecution(_ context.Context, _ *workflowservice.ResetWorkflowExecutionRequest) (*workflowservice.ResetWorkflowExecutionResponse, error) {
	return nil, nil
}
func (m *MockTemporalClient) UpdateWorkerBuildIdCompatibility(_ context.Context, _ *client.UpdateWorkerBuildIdCompatibilityOptions) error {
	return nil
}
func (m *MockTemporalClient) GetWorkerBuildIdCompatibility(_ context.Context, _ *client.GetWorkerBuildIdCompatibilityOptions) (*client.WorkerBuildIDVersionSets, error) {
	return nil, nil
}
func (m *MockTemporalClient) GetWorkerTaskReachability(_ context.Context, _ *client.GetWorkerTaskReachabilityOptions) (*client.WorkerTaskReachability, error) {
	return nil, nil
}
func (m *MockTemporalClient) UpdateWorkerVersioningRules(_ context.Context, _ client.UpdateWorkerVersioningRulesOptions) (*client.WorkerVersioningRules, error) {
	return nil, nil
}
func (m *MockTemporalClient) GetWorkerVersioningRules(_ context.Context, _ client.GetWorkerVersioningOptions) (*client.WorkerVersioningRules, error) {
	return nil, nil
}
func (m *MockTemporalClient) CheckHealth(_ context.Context, _ *client.CheckHealthRequest) (*client.CheckHealthResponse, error) {
	return &client.CheckHealthResponse{}, nil
}
func (m *MockTemporalClient) UpdateWorkflow(_ context.Context, _ client.UpdateWorkflowOptions) (client.WorkflowUpdateHandle, error) {
	return nil, nil
}
func (m *MockTemporalClient) GetWorkflowUpdateHandle(_ client.GetWorkflowUpdateHandleOptions) client.WorkflowUpdateHandle {
	return nil
}
func (m *MockTemporalClient) WorkflowService() workflowservice.WorkflowServiceClient {
	return nil
}
func (m *MockTemporalClient) OperatorService() operatorservice.OperatorServiceClient {
	return nil
}
func (m *MockTemporalClient) ScheduleClient() client.ScheduleClient { return nil }
func (m *MockTemporalClient) Close()                                 {}
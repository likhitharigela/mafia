# deploy.ps1 - builds all Docker images into minikube and applies all manifests
# Run from the root of your mafia project: .\k8s\deploy.ps1

Write-Host "=== Step 1: Point Docker to minikube registry ===" -ForegroundColor Cyan
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

Write-Host "=== Step 2: Build all images inside minikube ===" -ForegroundColor Cyan
docker build -t mafia-mafia-game-engine:latest ./mafia-game-engine
docker build --target server -t mafia-mafia-event-service:latest ./mafia-event-service
docker build --target worker -t mafia-mafia-event-worker:latest ./mafia-event-service
docker build -t mafia-mafia-gateway-service:latest ./mafia-gateway-service
docker build -t mafia-mafia-engine-frontend:latest ./mafia-engine-frontend

Write-Host "=== Step 3: Check KEDA ===" -ForegroundColor Cyan
$kedaInstalled = kubectl get namespace keda --ignore-not-found 2>$null
if (-not $kedaInstalled) {
    helm repo add kedacore https://kedacore.github.io/charts
    helm repo update
    helm install keda kedacore/keda --namespace keda --create-namespace
    Write-Host "KEDA installed. Waiting for operator..." -ForegroundColor Yellow
    kubectl wait --for=condition=ready pod -l app=keda-operator -n keda --timeout=120s
} else {
    Write-Host "KEDA already installed, skipping." -ForegroundColor Green
}

Write-Host "=== Step 4: Apply all Kubernetes manifests ===" -ForegroundColor Cyan
kubectl apply -f ./k8s/manifests/

Write-Host "=== Step 5: Wait for core pods to be ready ===" -ForegroundColor Cyan
kubectl wait --for=condition=ready pod -l app=mongodb -n mafia --timeout=120s
kubectl wait --for=condition=ready pod -l app=temporal -n mafia --timeout=180s
kubectl wait --for=condition=ready pod -l app=mafia-game-engine -n mafia --timeout=120s
kubectl wait --for=condition=ready pod -l app=mafia-gateway-service -n mafia --timeout=120s
kubectl wait --for=condition=ready pod -l app=mafia-frontend -n mafia --timeout=60s

Write-Host ""
Write-Host "=== Deployment complete! ===" -ForegroundColor Green
Write-Host "Frontend:    http://localhost:30173" -ForegroundColor Yellow
Write-Host "Gateway API: http://localhost:30000" -ForegroundColor Yellow
Write-Host "Temporal UI: http://localhost:30088" -ForegroundColor Yellow
Write-Host ""
Write-Host "Workers scale via KEDA - check with:" -ForegroundColor Cyan
Write-Host "  kubectl get scaledobjects -n mafia" -ForegroundColor White
Write-Host "  kubectl get pods -n mafia -l app=mafia-event-worker" -ForegroundColor White
Write-Host "  kubectl get pods -n mafia -l app=mafia-gateway-worker" -ForegroundColor White
Write-Host ""
Write-Host "Run kubectl get pods -n mafia to check all pod status" -ForegroundColor Cyan
# deploy.ps1 — builds all Docker images into minikube and applies all manifests
# Run from the root of your mafia project: .\k8s\deploy.ps1

Write-Host "=== Step 1: Point Docker to minikube's registry ===" -ForegroundColor Cyan
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

Write-Host "=== Step 2: Build all images inside minikube ===" -ForegroundColor Cyan
docker build -t mafia-mafia-game-engine:latest ./mafia-game-engine
docker build -t mafia-mafia-event-service:latest ./mafia-event-service
docker build -t mafia-mafia-gateway-service:latest ./mafia-gateway-service
docker build -t mafia-mafia-engine-frontend:latest ./mafia-engine-frontend

Write-Host "=== Step 3: Apply all Kubernetes manifests ===" -ForegroundColor Cyan
kubectl apply -f ./k8s/manifests/

Write-Host "=== Step 4: Wait for pods to be ready ===" -ForegroundColor Cyan
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
Write-Host "Run 'kubectl get pods -n mafia' to check pod status" -ForegroundColor Cyan

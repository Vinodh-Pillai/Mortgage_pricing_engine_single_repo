# Catalog Service Kubernetes Deployment

Build and load/push the image first:

```bash
./gradlew bootJar
docker build -t catalog-service:0.1.0 .
```

For local clusters, load the image into the cluster runtime as appropriate, then deploy:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/catalog-dev.yaml
kubectl rollout status statefulset/catalog-postgres -n wcpe-dev
kubectl rollout status deployment/catalog-service -n wcpe-dev
kubectl port-forward -n wcpe-dev svc/catalog-service 8082:8082
```

Validate:

```bash
curl http://localhost:8082/actuator/health/readiness
curl http://localhost:8082/api/v1/tenants/018fa4f0-1a4f-7e99-a02d-1b0100010001/product-catalog/active
```

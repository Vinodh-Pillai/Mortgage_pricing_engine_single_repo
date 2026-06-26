# Integration Service Deployment Status

`integration-service` is registered in `deploy-agent/config.json` for the local/dev health-only deployment path.

The module now has a minimal runnable Spring Boot surface:

- `build.gradle` applies Spring Boot and produces `build/libs/integration-service-0.1.0.jar`.
- `Dockerfile` builds a local runnable image from the boot jar.
- `IntegrationServiceApplication` and `/api/v1/integration/health` provide a deployable health-only runtime surface while partner endpoints remain contract/service-code backed.
- `docker-compose.local.yml` exposes the health-only service at local port `8087`.
- `k8s/integration-service-dev.yaml` defines the dev namespace, config map, deployment, probes, read-only root filesystem, and ClusterIP service.

Remaining deployment hardening step: promote partner endpoints from service classes to live controllers before exposing partner integration APIs beyond the current health-only runtime surface.

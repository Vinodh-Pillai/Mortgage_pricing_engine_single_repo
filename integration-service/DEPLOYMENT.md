# Integration Service Deployment Status

`integration-service` is not registered in `deploy-agent/config.json` yet.

The module now has a minimal runnable Spring Boot surface:

- `build.gradle` applies Spring Boot and produces `build/libs/integration-service-0.1.0.jar`.
- `Dockerfile` builds a local runnable image from the boot jar.
- `IntegrationServiceApplication` and `/api/v1/integration/health` provide a deployable health-only runtime surface while partner endpoints remain contract/service-code backed.

Next deployment hardening step: add a local manifest at `projects/integration-service/k8s/integration-service-dev.yaml` and a deploy-agent entry matching the other service modules once the partner endpoints are promoted from service classes to live controllers.

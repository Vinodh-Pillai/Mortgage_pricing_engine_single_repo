# Integration Service Deployment Status

`integration-service` is not registered in `deploy-agent/config.json` yet.

The module currently builds contract-test code only:

- `build.gradle` applies `java`, not Spring Boot.
- There is no `Dockerfile`.
- There is no runnable application entrypoint or Boot jar task.

Adding it to the deploy-agent local Kubernetes loop would create a failing deployment that points at a jar/image the module does not produce. Once the service gains a runnable artifact, add a local manifest at `projects/integration-service/k8s/integration-service-dev.yaml` and a deploy-agent entry matching the other service modules.

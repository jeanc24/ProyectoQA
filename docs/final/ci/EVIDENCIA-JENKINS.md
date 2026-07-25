# Evidencia CICD-02 — Jenkins pipeline verde

| Campo | Valor |
| ----- | ----- |
| Issue | #85 / CICD-02 |
| Artefacto | [`jenkins-pipeline-green.png`](./jenkins-pipeline-green.png) |
| Guía | [`JENKINS.md`](./JENKINS.md) |
| Commit esperado | `ci: extend jenkins pipeline for full final delivery` |

## Cómo generar la captura

```bash
docker compose build jenkins
docker compose up -d jenkins
# UI http://localhost:8082 — job Pipeline, Script Path: infra/jenkins/Jenkinsfile
# Build Now → Pipeline Overview en verde → screenshot
```

Guardar la imagen en este directorio con el nombre exacto `jenkins-pipeline-green.png`.

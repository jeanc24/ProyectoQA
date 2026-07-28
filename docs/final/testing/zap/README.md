# OWASP ZAP — evidencias (TEST-03)

- **Target:** `http://host.docker.internal:8080/swagger-ui.html`
- **Fecha:** 2026-07-23T17:32:26Z
- **Exit code ZAP:** `0` (0=OK, 1=warnings, 2+=fail; se usa `-I` para no bloquear por WARN)
- **Reportes:** `zap-report.html`, `zap-report.json`, `zap-warnings.md`

## Cómo regenerar

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api
# esperar health: curl -sf http://localhost:8080/actuator/health
./scripts/zap-baseline.sh
```

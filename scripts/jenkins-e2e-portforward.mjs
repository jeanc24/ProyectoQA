// CICD-02 — Reenvío TCP para E2E en Jenkins-en-Docker.
// El navegador Playwright corre dentro del contenedor Jenkins. keycloak-js usa
// PKCE (Web Crypto), que solo está disponible en contextos seguros (HTTPS o
// localhost). host.docker.internal es contexto inseguro → sin crypto.subtle.
// Solución: exponer localhost:<puerto> dentro del contenedor y reenviar al host
// (host.docker.internal), así el navegador usa localhost (seguro y ya registrado
// como redirect URI) y el tráfico llega al stack de staging publicado en el host.
//
// Uso: node scripts/jenkins-e2e-portforward.mjs 3008 8181 8088 &
import net from "node:net";

const TARGET_HOST = process.env.FORWARD_TARGET_HOST || "host.docker.internal";
const ports = process.argv.slice(2).map((p) => parseInt(p, 10)).filter(Boolean);

if (ports.length === 0) {
  console.error("Uso: node jenkins-e2e-portforward.mjs <puerto> [puerto...]");
  process.exit(1);
}

for (const port of ports) {
  const server = net.createServer((client) => {
    const upstream = net.connect(port, TARGET_HOST);
    client.on("error", () => upstream.destroy());
    upstream.on("error", () => client.destroy());
    client.pipe(upstream);
    upstream.pipe(client);
  });
  server.on("error", (err) => {
    console.error(`forward :${port} error: ${err.message}`);
    process.exit(1);
  });
  server.listen(port, "127.0.0.1", () => {
    console.log(`forward 127.0.0.1:${port} -> ${TARGET_HOST}:${port}`);
  });
}

import { defineConfig, devices } from '@playwright/test';

/**
 * Recorridos críticos contra el backend real (perfil demo, H2 temporal) en puertos aislados:
 * backend en 18080 y Vite en 5174, sin tocar las instancias habituales de 8080 y 5173.
 * Requiere el JAR: `mvn -f backend/pom.xml package -DskipTests` desde la raíz.
 */
const BACKEND = 'http://127.0.0.1:18080';
const FRONTEND = 'http://127.0.0.1:5174';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: { baseURL: FRONTEND, trace: 'retain-on-failure', locale: 'es-AR', timezoneId: 'America/Argentina/Buenos_Aires' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: process.env.E2E_BACKEND_CMD
        ?? `java -jar ../backend/target/gestion-stock-0.3.0-SNAPSHOT.jar --server.port=18080 --coto.web.origenes=${FRONTEND}`,
      url: `${BACKEND}/api/estado`,
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: 'npx vite --host 127.0.0.1 --port 5174 --strictPort',
      url: FRONTEND,
      env: { API_PROXY_TARGET: BACKEND },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
});

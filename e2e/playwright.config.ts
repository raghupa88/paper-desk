import { defineConfig, devices } from '@playwright/test';
import path from 'path';

/**
 * Full-stack E2E suite: drives the real backend (H2 in-memory, no external
 * DB needed) and the real webpack dev server together, exactly as a
 * developer would run them locally. Each spec creates its own unique user
 * (see fixtures.ts) so tests are independent despite sharing one backend
 * process and one in-memory database for the whole run.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Use the environment's pre-installed Chromium rather than
        // downloading one that matches this exact @playwright/test version.
        launchOptions: process.env.PLAYWRIGHT_CHROMIUM_PATH
          ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_PATH }
          : {},
      },
    },
  ],
  webServer: [
    {
      command: 'mvn -q -B --no-transfer-progress spring-boot:run',
      cwd: path.resolve(__dirname, '../backend'),
      url: 'http://localhost:8080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm run dev',
      cwd: path.resolve(__dirname, '../frontend'),
      url: 'http://localhost:3000',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  workers: 2,
  timeout: 120000,
  expect: {
    timeout: 10000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.05,
    },
  },
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:5174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'Desktop Chrome 1280x800',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
      },
    },
    {
      name: 'Pixel 7',
      use: {
        ...devices['Pixel 7'],
      },
    },
    {
      name: 'iPhone 13',
      use: {
        ...devices['iPhone 13'],
      },
      testMatch: /dispositivos\.spec\.ts/,
    },
    {
      name: 'iPad',
      use: {
        ...devices['iPad (gen 7)'],
      },
      testMatch: /dispositivos\.spec\.ts/,
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: !process.env.CI,
  },
});

import { defineConfig } from 'cypress';

export default defineConfig({
  allowCypressEnv: false,
  expose: {
    apiUrl: 'http://localhost:8080/api/v1',
    mailpitUrl: 'http://localhost:8025'
  },
  e2e: {
    baseUrl: 'http://localhost:4200',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    video: false,
    screenshotOnRunFailure: true,
    retries: { runMode: 1, openMode: 0 }
  }
});

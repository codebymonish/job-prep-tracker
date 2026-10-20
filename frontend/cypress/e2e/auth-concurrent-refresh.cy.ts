describe('Single-flight token refresh', () => {
  const testPassword = 'Password123!';
  const testName = 'G7 Test User';
  let testEmail = '';

  before(() => {
    testEmail = `e2e-g7-${crypto.randomUUID()}@example.com`;
    cy.createVerifiedUser(testEmail, testPassword, testName);
  });
  
  beforeEach(() => cy.loginViaUI(testEmail, testPassword));

  it('fires exactly one refresh for two simultaneous 401s and both retries succeed', () => {
    
    // 1. Install intercepts BEFORE page load.
    // Spy and MOCK retry requests to return 200 independently of backend protection.
    cy.intercept({ method: 'GET', url: '**/actuator/health/liveness' }, (req) => {
        req.reply({ statusCode: 200, body: { status: 'UP' } });
    }).as('retryCalls');

    const resolvers: Array<() => void> = [];
    cy.intercept({ method: 'GET', url: '**/actuator/health/liveness', times: 2 }, (req) => {
      return new Cypress.Promise<void>((resolve) => {
        resolvers.push(() => { req.reply({ statusCode: 401, body: {} }); resolve(); });
      });
    }).as('initialCalls');

    // 2. Full page load. Any startup session-restore refresh happens here and is NOT spied.
    cy.visitAndInjectAxe('/e2e/auth-test-harness');

    // 3. Wait until the guarded route has actually activated (proving bootstrap is finished).
    cy.get('[data-testid="harness-ready"]').should('exist');

    // 4. NOW install the refresh spy to capture the single-flight boundary isolated from bootstrap.
    cy.intercept('POST', '**/auth/refresh').as('refreshCall');

    // 5. Confirm both 401 requests are held.
    cy.get('@initialCalls.all').should('have.length', 2).then(() => {
      expect(resolvers).to.have.length(2);
      // 6. Release both 401s.
      resolvers.forEach(release => release());
    });

    // 7. Assert exactly one refresh was triggered by those two 401s.
    cy.wait('@refreshCall').its('response.statusCode').should('eq', 200);
    cy.wait('@retryCalls').its('response.statusCode').should('eq', 200);
    cy.wait('@retryCalls').its('response.statusCode').should('eq', 200);
    cy.get('@refreshCall.all').should('have.length', 1);

    cy.get('@initialCalls.all').then((initialCalls: any[]) => {
      expect(initialCalls).to.have.length(2);
      initialCalls.forEach((i: any) => expect(i.response.statusCode).to.eq(401));
    });

    cy.checkA11y();
  });
});

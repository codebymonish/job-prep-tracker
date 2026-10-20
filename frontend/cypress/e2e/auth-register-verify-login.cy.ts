describe('Auth: Register, Verify, Login, Logout Flow', () => {
  const testPassword = 'Password123!';

  beforeEach(() => {
    const mailpitUrl = Cypress.expose('mailpitUrl');
    cy.request('DELETE', `${mailpitUrl}/api/v1/messages`);
  });

  it('completes the full authentication lifecycle', () => {
    // Generate unique email inside the test to survive retries safely using UUID
    const testEmail = `e2e-reg-${crypto.randomUUID()}@example.com`;

    // 1. Register
    cy.intercept('POST', '**/auth/register').as('registerCall');
    cy.visitAndInjectAxe('/auth/register');
    cy.checkA11y();

    cy.get('#name').type('Test User');
    cy.get('#email').type(testEmail);
    cy.get('#password').type(testPassword);
    cy.get('button[type="submit"]').click();
    cy.wait('@registerCall').its('response.statusCode').should('eq', 201);
    cy.get('.success-message[role="status"]').should('contain', 'Registration successful');
    cy.checkA11y();

    // 2. Verify
    cy.getMailpitMessage(testEmail, 'Verify your Job Tracker account').then(msg => {
      cy.getMailpitMessageBody(msg.ID).then(body => {
        cy.extractVerificationToken(body).then(token => {
          cy.visitAndInjectAxe(`/auth/verify?token=${token}`);
          cy.checkA11y();
          
          cy.url().should('not.include', 'token='); // Assert URL cleaned immediately
          cy.get('.success-message[role="alert"]').should('contain', 'successfully verified');
          cy.checkA11y();

          // 3. Navigate to Login (No auto-redirect)
          cy.contains('a.btn', 'Log In').click();
          cy.url().should('include', '/auth/login');
          cy.checkA11y();

          // 4. Failed Login
          cy.get('#email').type(testEmail);
          cy.get('#password').type('WrongPassword!');
          cy.get('button[type="submit"]').click();
          cy.get('.error-message[role="alert"]').should('contain', 'Invalid email or password');
          cy.checkA11y();

          // 5. Successful Login
          cy.intercept('POST', '**/auth/login').as('loginCall');
          cy.get('#password').clear().type(testPassword);
          cy.get('button[type="submit"]').click();
          
          cy.wait('@loginCall').its('response.statusCode').should('eq', 200);
          cy.get('@loginCall').its('response.body.accessToken').should('be.a', 'string');
          
          cy.getCookie('refreshToken').should((cookie) => {
            expect(cookie).to.exist;
            expect(cookie?.httpOnly).to.be.true;
            expect(cookie?.secure).to.be.false; 
            expect(cookie?.sameSite).to.eq('strict'); 
            expect(cookie?.path).to.eq('/api/v1/auth');
          });
          
          cy.url().should('include', '/applications');
          cy.checkA11y();

          // 6. Logout (Navigation occurs in callback, so intercept first)
          cy.intercept('POST', '**/auth/logout').as('logoutCall');
          cy.contains('button', 'Log Out').click();
          cy.wait('@logoutCall').its('response.statusCode').should('eq', 204);
          cy.url().should('include', '/auth/login');

          // 7. Verify session terminated
          cy.visitAndInjectAxe('/applications');
          cy.url().should('include', '/auth/login');
        });
      });
    });
  });
});

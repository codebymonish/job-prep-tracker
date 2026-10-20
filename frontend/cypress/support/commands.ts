// All references use Cypress.expose(), as Cypress.env() is disabled.

Cypress.Commands.add('getMailpitMessage', (toEmail: string, subject: string) => {
  const mailpitUrl = Cypress.expose('mailpitUrl');
  const poll = (attempts: number): Cypress.Chainable<any> => {
    if (attempts === 0) throw new Error(`Mailpit: no message for ${toEmail} (subject: "${subject}") after 5s`);
    return cy.request('GET', `${mailpitUrl}/api/v1/messages`).then(res => {
      const msg = res.body.messages?.find((m: any) => m.To?.[0]?.Address === toEmail && m.Subject === subject);
      if (msg) return cy.wrap(msg);
      return cy.wait(500).then(() => poll(attempts - 1));
    });
  };
  return poll(10);
});

Cypress.Commands.add('getMailpitMessageBody', (id: string) => {
  return cy.request(`${Cypress.expose('mailpitUrl')}/api/v1/message/${id}`).its('body.Text');
});

Cypress.Commands.add('extractVerificationToken', (body: string) => {
  const lines = body.split('\n');
  const urlLine = lines.find(l => l.includes('localhost:4200/auth/verify?token='));
  if (!urlLine) throw new Error('Verification URL not found in email body');
  const url = new URL(urlLine.trim());
  expect(url.origin).to.eq('http://localhost:4200');
  expect(url.pathname).to.eq('/auth/verify');
  const token = url.searchParams.get('token');
  expect(token).to.be.a('string').and.to.have.length.greaterThan(0);
  return cy.wrap(token as string);
});

Cypress.Commands.add('loginViaUI', (email: string, password: string) => {
  cy.visitAndInjectAxe('/auth/login');
  cy.get('#email').type(email);
  cy.get('#password').type(password);
  cy.get('button[type="submit"]').click();
  cy.url().should('include', '/applications');
});

Cypress.Commands.add('createVerifiedUser', (email: string, password: string, name: string) => {
  const mailpitUrl = Cypress.expose('mailpitUrl');
  cy.request('DELETE', `${mailpitUrl}/api/v1/messages`);

  cy.visitAndInjectAxe('/auth/register');
  cy.checkA11y();
  cy.get('#name').type(name);
  cy.get('#email').type(email);
  cy.get('#password').type(password);
  cy.get('button[type="submit"]').click();
  
  cy.get('.success-message[role="status"]').should('contain', 'Registration successful');
  cy.checkA11y();

  cy.getMailpitMessage(email, 'Verify your Job Tracker account').then(msg => {
    cy.getMailpitMessageBody(msg.ID).then(body => {
      cy.extractVerificationToken(body).then(token => {
        cy.visitAndInjectAxe(`/auth/verify?token=${token}`);
        cy.checkA11y();
        cy.url().should('not.include', 'token=');
        cy.get('.success-message[role="alert"]').should('contain', 'successfully verified');
        cy.checkA11y();
      });
    });
  });
});

Cypress.Commands.add('visitAndInjectAxe', (url: string) => {
  return cy.visit(url).then(() => cy.injectAxe());
});

declare namespace Cypress {
  interface Chainable<Subject = any> {
    getMailpitMessage(toEmail: string, subject: string): Chainable<any>;
    getMailpitMessageBody(id: string): Chainable<string>;
    extractVerificationToken(body: string): Chainable<string>;
    loginViaUI(email: string, password: string): Chainable<void>;
    createVerifiedUser(email: string, password: string, name: string): Chainable<void>;
    visitAndInjectAxe(url: string): Chainable<void>;
  }
}

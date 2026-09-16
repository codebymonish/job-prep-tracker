import { Component } from '@angular/core';

@Component({
  selector: 'app-admin',
  standalone: true,
  template: `
    <div>
      <h1>Admin Area</h1>
      <p>Protected admin area.</p>
    </div>
  `
})
export class AdminComponent {}

import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-applications',
  standalone: true,
  template: `
    <div>
      <h1>Applications</h1>
      <p>Welcome to the protected area.</p>
      <button (click)="logout()">Log Out</button>
    </div>
  `
})
export class ApplicationsComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  logout() {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/auth/login']);
    });
  }
}

import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-nav-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <header class="nav-header">
      <div class="nav-container">
        <a class="nav-brand" routerLink="/dashboard">
          <span class="brand-icon">&#x1F4BC;</span>
          <span class="brand-name">JobTracker</span>
        </a>
        <nav class="nav-links" aria-label="Main navigation">
          <a
            id="nav-dashboard"
            routerLink="/dashboard"
            routerLinkActive="active"
            class="nav-link">
            Dashboard
          </a>
          <a
            id="nav-applications"
            routerLink="/applications"
            routerLinkActive="active"
            class="nav-link">
            Applications
          </a>
        </nav>
        <div class="nav-user">
          <span class="user-email">{{ userEmail }}</span>
          <button id="logout-btn" class="btn-logout" (click)="logout()">Sign out</button>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .nav-header {
      position: sticky;
      top: 0;
      z-index: 100;
      background: linear-gradient(135deg, #1a1f35 0%, #2d3561 100%);
      border-bottom: 1px solid rgba(255,255,255,0.08);
      box-shadow: 0 2px 20px rgba(0,0,0,0.3);
    }
    .nav-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 2rem;
      height: 60px;
      display: flex;
      align-items: center;
      gap: 2rem;
    }
    .nav-brand {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      text-decoration: none;
      flex-shrink: 0;
    }
    .brand-icon {
      font-size: 1.3rem;
    }
    .brand-name {
      font-size: 1.1rem;
      font-weight: 700;
      color: #fff;
      letter-spacing: 0.02em;
    }
    .nav-links {
      display: flex;
      gap: 0.25rem;
      flex: 1;
    }
    .nav-link {
      padding: 0.4rem 0.9rem;
      border-radius: 6px;
      color: rgba(255,255,255,0.65);
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: background 0.15s, color 0.15s;
    }
    .nav-link:hover {
      background: rgba(255,255,255,0.1);
      color: #fff;
    }
    .nav-link.active {
      background: rgba(99,102,241,0.3);
      color: #a5b4fc;
    }
    .nav-user {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-left: auto;
      flex-shrink: 0;
    }
    .user-email {
      font-size: 0.82rem;
      color: rgba(255,255,255,0.5);
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .btn-logout {
      padding: 0.35rem 0.85rem;
      background: rgba(239,68,68,0.15);
      color: #fca5a5;
      border: 1px solid rgba(239,68,68,0.3);
      border-radius: 6px;
      font-size: 0.82rem;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.15s, color 0.15s, border-color 0.15s;
    }
    .btn-logout:hover {
      background: rgba(239,68,68,0.3);
      color: #fff;
      border-color: rgba(239,68,68,0.5);
    }
  `]
})
export class NavHeaderComponent {
  private authService = inject(AuthService);
  private authState = inject(AuthStateService);
  private router = inject(Router);

  get userEmail(): string {
    return this.authState.snapshot.user?.email ?? '';
  }

  logout() {
    this.authService.logout().subscribe({
      complete: () => this.router.navigate(['/auth/login'])
    });
  }
}

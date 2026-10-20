import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApplicationService } from '../../core/services/application.service';
import { AuthStateService } from '../../core/services/auth-state.service';
import { ApplicationStats } from '../../core/models/application.model';

interface StatCard {
  label: string;
  key: keyof ApplicationStats;
  colorClass: string;
  icon: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <header class="dashboard-header">
        <div>
          <h1 class="greeting">{{ greeting }}, {{ firstName }}!</h1>
          <p class="subtitle">Here's your job search pipeline at a glance.</p>
        </div>
        <button id="dashboard-new-app-btn" class="btn-primary" (click)="goToNew()">
          + New Application
        </button>
      </header>

      <!-- Skeleton loading -->
      <div *ngIf="isLoading" class="stats-grid">
        <div *ngFor="let i of [1,2,3,4,5,6]" class="stat-card skeleton-card">
          <div class="skeleton-value"></div>
          <div class="skeleton-label"></div>
        </div>
      </div>

      <!-- Error state -->
      <div *ngIf="!isLoading && errorMessage" class="error-banner" role="alert">
        <p>{{ errorMessage }}</p>
        <button class="btn-retry" (click)="loadStats()">Retry</button>
      </div>

      <!-- Stats grid -->
      <div *ngIf="!isLoading && !errorMessage && stats" class="stats-grid">
        <div
          *ngFor="let card of statCards"
          class="stat-card"
          [class]="'stat-card--' + card.colorClass"
          (click)="navigateToFilter(card.key)"
          [class.clickable]="card.key !== 'total'">
          <div class="stat-icon">{{ card.icon }}</div>
          <div class="stat-value">{{ stats[card.key] }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>

      <!-- Quick links -->
      <section *ngIf="!isLoading && !errorMessage" class="quick-links">
        <h2>Quick Actions</h2>
        <div class="actions-row">
          <button id="view-all-btn" class="action-card" (click)="goToApplications()">
            <span class="action-icon">&#x1F4CB;</span>
            <span class="action-label">View All Applications</span>
          </button>
          <button id="track-new-btn" class="action-card" (click)="goToNew()">
            <span class="action-icon">&#x2795;</span>
            <span class="action-label">Track New Application</span>
          </button>
        </div>
      </section>
    </div>
  `,
  styles: [`
        .dashboard-container {
      font-family: 'Inter', sans-serif;
      max-width: 1100px;
      margin: 0 auto;
      padding: 2.5rem 2rem;
    }
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 2.5rem;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .greeting {
      font-size: 1.8rem;
      font-weight: 700;
      color: #1a1f35;
      margin: 0 0 0.25rem;
    }
    .subtitle {
      color: #6b7280;
      margin: 0;
      font-size: 0.95rem;
    }
    .btn-primary {
      padding: 0.6rem 1.25rem;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      transition: opacity 0.15s, transform 0.1s;
      white-space: nowrap;
    }
    .btn-primary:hover { opacity: 0.9; transform: translateY(-1px); }

    /* Stats grid */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 1.25rem;
      margin-bottom: 2.5rem;
    }
    .stat-card {
      background: white;
      border-radius: 12px;
      padding: 1.5rem 1.25rem;
      box-shadow: 0 1px 8px rgba(0,0,0,0.06);
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      border: 1px solid #e5e7eb;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .stat-card.clickable { cursor: pointer; }
    .stat-card.clickable:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(0,0,0,0.1);
    }
    .stat-icon { font-size: 1.5rem; margin-bottom: 0.25rem; }
    .stat-value { font-size: 2rem; font-weight: 700; color: #111827; line-height: 1; }
    .stat-label { font-size: 0.8rem; font-weight: 500; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em; }

    /* Color variants */
    .stat-card--indigo { border-top: 3px solid #6366f1; }
    .stat-card--indigo .stat-value { color: #4f46e5; }
    .stat-card--blue { border-top: 3px solid #3b82f6; }
    .stat-card--blue .stat-value { color: #2563eb; }
    .stat-card--amber { border-top: 3px solid #f59e0b; }
    .stat-card--amber .stat-value { color: #d97706; }
    .stat-card--green { border-top: 3px solid #10b981; }
    .stat-card--green .stat-value { color: #059669; }
    .stat-card--red { border-top: 3px solid #ef4444; }
    .stat-card--red .stat-value { color: #dc2626; }
    .stat-card--slate { border-top: 3px solid #94a3b8; }
    .stat-card--slate .stat-value { color: #64748b; }

    /* Skeleton */
    .skeleton-card { animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-value { width: 50px; height: 36px; background: #e5e7eb; border-radius: 4px; margin-bottom: 0.5rem; }
    .skeleton-label { width: 80px; height: 12px; background: #e5e7eb; border-radius: 4px; }
    @keyframes pulse { 0%,100% { opacity: 0.7; } 50% { opacity: 1; } }

    /* Error */
    .error-banner {
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      padding: 1rem 1.25rem;
      color: #991b1b;
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-bottom: 2rem;
    }
    .error-banner p { margin: 0; flex: 1; }
    .btn-retry {
      padding: 0.35rem 0.75rem;
      background: white;
      border: 1px solid #fca5a5;
      color: #dc2626;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.85rem;
    }

    /* Quick actions */
    .quick-links h2 {
      font-size: 1rem;
      font-weight: 600;
      color: #374151;
      margin: 0 0 1rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .actions-row {
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .action-card {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.85rem 1.25rem;
      background: white;
      border: 1px solid #e5e7eb;
      border-radius: 10px;
      cursor: pointer;
      transition: background 0.15s, box-shadow 0.15s;
      font-size: 0.9rem;
      font-weight: 500;
      color: #374151;
    }
    .action-card:hover { background: #f9fafb; box-shadow: 0 2px 8px rgba(0,0,0,0.07); }
    .action-icon { font-size: 1.2rem; }
  `]
})
export class DashboardComponent implements OnInit {
  private service = inject(ApplicationService);
  private authState = inject(AuthStateService);
  private router = inject(Router);

  stats: ApplicationStats | null = null;
  isLoading = true;
  errorMessage = '';

  readonly statCards: StatCard[] = [
    { label: 'Total',        key: 'total',        colorClass: 'indigo', icon: '\u{1F4CA}' },
    { label: 'Applied',      key: 'applied',      colorClass: 'blue',   icon: '\u{1F4E4}' },
    { label: 'Interviewing', key: 'interviewing', colorClass: 'amber',  icon: '\u{1F4AC}' },
    { label: 'Offer',        key: 'offer',        colorClass: 'green',  icon: '\u{1F389}' },
    { label: 'Rejected',     key: 'rejected',     colorClass: 'red',    icon: '\u{274C}' },
    { label: 'Withdrawn',    key: 'withdrawn',    colorClass: 'slate',  icon: '\u{23F8}' },
  ];

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  get firstName(): string {
    const email = this.authState.snapshot.user?.email ?? '';
    return email.split('@')[0] || 'there';
  }

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.service.getStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load dashboard stats. Please try again.';
        this.isLoading = false;
      }
    });
  }

  navigateToFilter(key: keyof ApplicationStats): void {
    if (key === 'total') {
      this.router.navigate(['/applications']);
    } else {
      this.router.navigate(['/applications'], { queryParams: { status: key.toUpperCase() } });
    }
  }

  goToApplications(): void {
    this.router.navigate(['/applications']);
  }

  goToNew(): void {
    this.router.navigate(['/applications/new']);
  }
}


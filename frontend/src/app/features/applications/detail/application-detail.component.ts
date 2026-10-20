import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ApplicationService } from '../../../core/services/application.service';
import {
  ALLOWED_TRANSITIONS,
  ApplicationDetail,
  ApplicationStatus
} from '../../../core/models/application.model';

@Component({
  selector: 'app-application-detail',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="detail-container">
      <nav class="nav-back">
        <button class="btn btn-link" (click)="goBack()">← Back to Applications</button>
      </nav>

      <!-- Loading State -->
      <div *ngIf="isLoading" class="state-card loading-card" data-testid="detail-loading">
        <p>Loading application details...</p>
      </div>

      <!-- Error State -->
      <div *ngIf="!isLoading && errorMessage" class="state-card error-card" role="alert">
        <p class="error-text">{{ errorMessage }}</p>
        <button class="btn btn-secondary" (click)="loadDetail()">Retry</button>
      </div>

      <!-- Detail Card -->
      <article *ngIf="!isLoading && application" class="detail-card">
        <header class="detail-header">
          <div>
            <h1 class="company-name">{{ application.company }}</h1>
            <h2 class="role-title">{{ application.roleTitle }}</h2>
          </div>
          <span class="status-badge" [ngClass]="'status-' + application.status.toLowerCase()">
            {{ application.status }}
          </span>
        </header>

        <section class="meta-section">
          <div class="meta-item">
            <span class="meta-label">Applied Date</span>
            <span class="meta-value">{{ application.appliedDate }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Record Version</span>
            <span class="meta-value">v{{ application.version }}</span>
          </div>
        </section>

        <!-- Stale Update Warning Message -->
        <div *ngIf="staleMessage" class="alert-warning" role="alert" data-testid="stale-warning">
          {{ staleMessage }}
        </div>

        <!-- Status Transition Actions -->
        <section class="transitions-section" *ngIf="allowedNextStatuses.length > 0">
          <h3>Update Status</h3>
          <div class="transition-buttons">
            <button
              *ngFor="let nextStatus of allowedNextStatuses"
              class="btn btn-transition"
              [disabled]="isUpdating"
              [class.btn-transition--loading]="isUpdating"
              (click)="updateStatus(nextStatus)">
              <span *ngIf="isUpdating" class="btn-spinner" aria-hidden="true"></span>
              <span>{{ isUpdating ? "Updating..." : "Move to " + nextStatus }}</span>
            </button>
          </div>
        </section>

        <section class="terminal-section" *ngIf="allowedNextStatuses.length === 0">
          <p class="terminal-note">This application is in a terminal state ({{ application.status }}).</p>
        </section>

        <!-- Job Description with Expand/Collapse -->
        <section class="jd-section" *ngIf="application.jdText">
          <h3>Job Description</h3>
          <div class="jd-content" [class.collapsed]="isJdCollapsed && shouldShowJdToggle">
            {{ displayJdText }}
          </div>
          <button
            *ngIf="shouldShowJdToggle"
            class="btn btn-link toggle-btn"
            (click)="toggleJd()">
            {{ isJdCollapsed ? 'Show more...' : 'Show less' }}
          </button>
        </section>

        <!-- Danger Zone: Delete -->
        <footer class="danger-zone">
          <button
            *ngIf="!confirmingDelete"
            class="btn btn-danger"
            (click)="confirmingDelete = true">
            Delete Application
          </button>
          <div *ngIf="confirmingDelete" class="delete-confirmation">
            <span class="confirm-prompt">Are you sure?</span>
            <button class="btn btn-danger-confirm" (click)="deleteApplication()">
              Confirm Delete
            </button>
            <button class="btn btn-secondary" (click)="confirmingDelete = false">
              Cancel
            </button>
          </div>
        </footer>
      </article>
    </div>
  `,
  styles: [`
    .detail-container {
      max-width: 800px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
      font-family: system-ui, -apple-system, sans-serif;
    }
    .nav-back {
      margin-bottom: 1.5rem;
    }
    .btn-link {
      background: none;
      border: none;
      color: #3182ce;
      cursor: pointer;
      font-weight: 500;
      padding: 0;
      font-size: 1rem;
    }
    .btn-link:hover { text-decoration: underline; }
    .detail-card {
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 2rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }
    .detail-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
      border-bottom: 1px solid #edf2f7;
      padding-bottom: 1.5rem;
    }
    .company-name {
      margin: 0;
      font-size: 2rem;
      color: #1a202c;
    }
    .role-title {
      margin: 0.25rem 0 0;
      font-size: 1.25rem;
      color: #4a5568;
      font-weight: 500;
    }
    .status-badge {
      display: inline-block;
      padding: 0.35rem 0.8rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .status-applied { background-color: #ebf8ff; color: #2b6cb0; }
    .status-interviewing { background-color: #fefcbf; color: #b7791f; }
    .status-offer { background-color: #c6f6d5; color: #22543d; }
    .status-rejected { background-color: #fed7d7; color: #9b2c2c; }
    .status-withdrawn { background-color: #edf2f7; color: #718096; }
    .meta-section {
      display: flex;
      gap: 3rem;
      margin-bottom: 2rem;
    }
    .meta-item { display: flex; flex-direction: column; }
    .meta-label { font-size: 0.8rem; color: #a0aec0; text-transform: uppercase; font-weight: 600; }
    .meta-value { font-size: 1.1rem; color: #2d3748; font-weight: 500; margin-top: 0.2rem; }
    .alert-warning {
      background-color: #feebc8;
      color: #7b341e;
      padding: 1rem;
      border-radius: 6px;
      margin-bottom: 1.5rem;
      font-weight: 500;
    }
    .transitions-section {
      background-color: #f7fafc;
      padding: 1.25rem;
      border-radius: 8px;
      margin-bottom: 2rem;
    }
    .transitions-section h3 {
      margin: 0 0 1rem;
      font-size: 1rem;
      color: #4a5568;
    }
    .transition-buttons {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
    .btn {
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      border: none;
      transition: all 0.2s;
    }
    .btn-transition {
      background-color: #3182ce;
      color: white;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
    .btn-transition:hover { background-color: #2b6cb0; }
    .btn-transition--loading { opacity: 0.7; cursor: not-allowed; }
    .btn-spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.4);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
      flex-shrink: 0;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .terminal-note {
      color: #718096;
      font-style: italic;
      margin-bottom: 2rem;
    }
    .jd-section {
      margin-bottom: 2rem;
    }
    .jd-section h3 {
      margin: 0 0 0.75rem;
      font-size: 1.1rem;
      color: #2d3748;
    }
    .jd-content {
      line-height: 1.6;
      color: #4a5568;
      white-space: pre-wrap;
    }
    .jd-content.collapsed {
      max-height: 120px;
      overflow: hidden;
      position: relative;
    }
    .toggle-btn { margin-top: 0.5rem; }
    .danger-zone {
      border-top: 1px solid #edf2f7;
      padding-top: 1.5rem;
      display: flex;
      justify-content: flex-end;
    }
    .btn-danger {
      background-color: #fff5f5;
      color: #e53e3e;
      border: 1px solid #feb2b2;
    }
    .btn-danger:hover {
      background-color: #fed7d7;
    }
    .delete-confirmation {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .confirm-prompt {
      color: #e53e3e;
      font-weight: 600;
    }
    .btn-danger-confirm {
      background-color: #e53e3e;
      color: white;
    }
    .btn-secondary {
      background-color: #e2e8f0;
      color: #4a5568;
    }
    .state-card {
      text-align: center;
      padding: 3rem 1.5rem;
      border-radius: 8px;
    }
    .error-card {
      background-color: #fff5f5;
      color: #c53030;
      border: 1px solid #feb2b2;
    }
  `]
})
export class ApplicationDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(ApplicationService);

  application: ApplicationDetail | null = null;
  isLoading = true;
  isUpdating = false;
  errorMessage = '';
  staleMessage = '';
  confirmingDelete = false;

  isJdCollapsed = true;

  get allowedNextStatuses(): ApplicationStatus[] {
    if (!this.application) return [];
    return ALLOWED_TRANSITIONS[this.application.status] || [];
  }

  get shouldShowJdToggle(): boolean {
    return !!(this.application?.jdText && this.application.jdText.length > 500);
  }

  get displayJdText(): string {
    if (!this.application?.jdText) return '';
    return this.application.jdText;
  }

  ngOnInit() {
    this.loadDetail();
  }

  loadDetail(preserveStale = false) {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage = 'Application ID not provided.';
      this.isLoading = false;
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    if (!preserveStale) {
      this.staleMessage = '';
    }

    this.service.getById(id).subscribe({
      next: (app) => {
        this.application = app;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Application not found or failed to load.';
        this.isLoading = false;
      }
    });
  }

  updateStatus(newStatus: ApplicationStatus) {
    if (!this.application || this.isUpdating) return;

    this.isUpdating = true;
    this.staleMessage = '';

    this.service.updateStatus(this.application.id, {
      status: newStatus,
      version: this.application.version
    }).subscribe({
      next: (updated) => {
        this.application = updated;
        this.isUpdating = false;
      },
      error: (err) => {
        this.isUpdating = false;
        if (err.status === 409) {
          this.staleMessage = 'Updated elsewhere — refreshing...';
          this.loadDetail(true);
        } else {
          this.errorMessage = 'Failed to update status.';
        }
      }
    });
  }

  deleteApplication() {
    if (!this.application) return;

    this.service.delete(this.application.id).subscribe({
      next: () => {
        this.router.navigate(['/applications']);
      },
      error: () => {
        this.errorMessage = 'Failed to delete application.';
      }
    });
  }

  toggleJd() {
    this.isJdCollapsed = !this.isJdCollapsed;
  }

  goBack() {
    this.router.navigate(['/applications']);
  }
}



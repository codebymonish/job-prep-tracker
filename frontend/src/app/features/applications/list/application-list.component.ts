import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApplicationService } from '../../../core/services/application.service';
import { ApplicationStatus, ApplicationSummary } from '../../../core/models/application.model';

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="applications-container">
      <header class="page-header">
        <div class="header-content">
          <h1>Applications</h1>
          <p class="subtitle">Track and manage your job applications</p>
        </div>
        <button id="new-application-btn" class="btn btn-primary" (click)="goToNew()">
          + New Application
        </button>
      </header>

      <!-- Status Filter Tabs -->
      <nav class="filter-tabs" aria-label="Status filters">
        <button
          *ngFor="let s of filterOptions"
          class="tab-btn"
          [class.active]="currentFilter === s"
          (click)="filterByStatus(s)">
          {{ s === 'ALL' ? 'All' : s }}
        </button>
      </nav>

      <!-- Loading State: Skeletons -->
      <div *ngIf="isLoading" class="skeleton-grid" data-testid="loading-skeletons">
        <div *ngFor="let item of [1, 2, 3]" class="skeleton-card">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line role"></div>
          <div class="skeleton-badge"></div>
          <div class="skeleton-line date"></div>
        </div>
      </div>

      <!-- Error State -->
      <div *ngIf="!isLoading && errorMessage" class="state-card error-card" role="alert">
        <p class="error-text">{{ errorMessage }}</p>
        <button class="btn btn-secondary" (click)="retry()">Retry</button>
      </div>

      <!-- Empty State -->
      <div *ngIf="!isLoading && !errorMessage && applications.length === 0" class="state-card empty-card" data-testid="empty-state">
        <p class="empty-title">No applications found</p>
        <p class="empty-subtitle">Track your first job application and monitor your interview progress.</p>
        <button class="btn btn-primary" (click)="goToNew()">Create your first application</button>
      </div>

      <!-- Application Grid -->
      <div *ngIf="!isLoading && !errorMessage && applications.length > 0" class="applications-grid">
        <article
          *ngFor="let app of applications"
          class="application-card"
          (click)="viewDetail(app.id)"
          tabindex="0"
          (keyup.enter)="viewDetail(app.id)">
          <div class="card-header">
            <h2 class="company-name">{{ app.company }}</h2>
            <span class="status-badge" [ngClass]="'status-' + app.status.toLowerCase()">
              {{ app.status }}
            </span>
          </div>
          <p class="role-title">{{ app.roleTitle }}</p>
          <div class="card-footer">
            <span class="applied-date">Applied: {{ app.appliedDate }}</span>
          </div>
        </article>
      </div>

      <!-- Pagination -->
      <footer *ngIf="!isLoading && totalPages > 1" class="pagination-controls">
        <button
          class="btn btn-pagination"
          [disabled]="currentPage === 0"
          (click)="goToPage(currentPage - 1)">
          Previous
        </button>
        <span class="page-indicator">Page {{ currentPage + 1 }} of {{ totalPages }}</span>
        <button
          class="btn btn-pagination"
          [disabled]="currentPage >= totalPages - 1"
          (click)="goToPage(currentPage + 1)">
          Next
        </button>
      </footer>
    </div>
  `,
  styles: [`
    .applications-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
      font-family: system-ui, -apple-system, sans-serif;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
    }
    .page-header h1 {
      margin: 0;
      font-size: 2rem;
      color: #1a202c;
    }
    .subtitle {
      margin: 0.25rem 0 0;
      color: #718096;
    }
    .filter-tabs {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
      overflow-x: auto;
      border-bottom: 2px solid #edf2f7;
      padding-bottom: 0.5rem;
    }
    .tab-btn {
      background: none;
      border: none;
      padding: 0.5rem 1rem;
      border-radius: 9999px;
      cursor: pointer;
      font-weight: 500;
      color: #4a5568;
      transition: all 0.2s;
    }
    .tab-btn:hover {
      background-color: #f7fafc;
      color: #2d3748;
    }
    .tab-btn.active {
      background-color: #3182ce;
      color: white;
    }
    .btn {
      padding: 0.6rem 1.2rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      border: none;
      transition: background 0.2s;
    }
    .btn-primary {
      background-color: #3182ce;
      color: white;
    }
    .btn-primary:hover {
      background-color: #2b6cb0;
    }
    .btn-secondary {
      background-color: #e2e8f0;
      color: #2d3748;
    }
    .btn-secondary:hover {
      background-color: #cbd5e0;
    }
    .applications-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1.5rem;
    }
    .application-card {
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.25rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
      cursor: pointer;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .application-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 6px rgba(0,0,0,0.08);
      border-color: #cbd5e0;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 0.75rem;
    }
    .company-name {
      margin: 0;
      font-size: 1.25rem;
      color: #2d3748;
    }
    .role-title {
      color: #4a5568;
      font-size: 0.95rem;
      margin: 0 0 1rem;
    }
    .card-footer {
      font-size: 0.85rem;
      color: #a0aec0;
      border-top: 1px solid #f7fafc;
      padding-top: 0.75rem;
    }
    .status-badge {
      display: inline-block;
      padding: 0.25rem 0.6rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .status-applied { background-color: #ebf8ff; color: #2b6cb0; }
    .status-interviewing { background-color: #fefcbf; color: #b7791f; }
    .status-offer { background-color: #c6f6d5; color: #22543d; }
    .status-rejected { background-color: #fed7d7; color: #9b2c2c; }
    .status-withdrawn { background-color: #edf2f7; color: #718096; }
    .state-card {
      text-align: center;
      padding: 3rem 1.5rem;
      background: #f7fafc;
      border: 1px dashed #cbd5e0;
      border-radius: 8px;
    }
    .empty-title {
      font-size: 1.25rem;
      font-weight: 600;
      color: #2d3748;
      margin: 0 0 0.5rem;
    }
    .empty-subtitle {
      color: #718096;
      margin: 0 0 1.5rem;
    }
    .error-card {
      border-color: #feb2b2;
      background-color: #fff5f5;
    }
    .error-text {
      color: #c53030;
      font-weight: 500;
      margin-bottom: 1rem;
    }
    .skeleton-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1.5rem;
    }
    .skeleton-card {
      background: #edf2f7;
      border-radius: 8px;
      padding: 1.25rem;
      animation: pulse 1.5s infinite ease-in-out;
      height: 140px;
    }
    .skeleton-line {
      background: #e2e8f0;
      border-radius: 4px;
      margin-bottom: 0.75rem;
    }
    .skeleton-line.title { width: 60%; height: 20px; }
    .skeleton-line.role { width: 80%; height: 16px; }
    .skeleton-line.date { width: 40%; height: 14px; margin-top: 1rem; }
    .skeleton-badge { width: 25%; height: 18px; background: #e2e8f0; border-radius: 4px; }
    @keyframes pulse {
      0%, 100% { opacity: 0.6; }
      50% { opacity: 1; }
    }
    .pagination-controls {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 1rem;
      margin-top: 2rem;
    }
    .btn-pagination {
      background-color: white;
      border: 1px solid #cbd5e0;
      color: #4a5568;
    }
    .btn-pagination:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .page-indicator {
      font-size: 0.9rem;
      color: #718096;
    }
  `]
})
export class ApplicationListComponent implements OnInit {
  private service = inject(ApplicationService);
  private router = inject(Router);

  applications: ApplicationSummary[] = [];
  isLoading = true;
  errorMessage = '';

  filterOptions: (ApplicationStatus | 'ALL')[] = [
    'ALL', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN'
  ];
  currentFilter: ApplicationStatus | 'ALL' = 'ALL';

  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  totalElements = 0;

  ngOnInit() {
    this.loadApplications();
  }

  loadApplications() {
    this.isLoading = true;
    this.errorMessage = '';
    const statusParam = this.currentFilter === 'ALL' ? undefined : this.currentFilter;

    this.service.list(this.currentPage, this.pageSize, statusParam).subscribe({
      next: (response) => {
        this.applications = response.content;
        this.totalPages = response.totalPages;
        this.totalElements = response.totalElements;
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = 'Failed to load applications. Please try again.';
        this.isLoading = false;
      }
    });
  }

  filterByStatus(status: ApplicationStatus | 'ALL') {
    this.currentFilter = status;
    this.currentPage = 0;
    this.loadApplications();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadApplications();
    }
  }

  goToNew() {
    this.router.navigate(['/applications/new']);
  }

  viewDetail(id: string) {
    this.router.navigate(['/applications', id]);
  }

  retry() {
    this.loadApplications();
  }
}

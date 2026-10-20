import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApplicationService } from '../../../core/services/application.service';

function pastOrPresentValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  const selectedDate = new Date(control.value);
  const today = new Date();
  today.setHours(23, 59, 59, 999);
  if (selectedDate > today) {
    return { futureDate: 'Applied date cannot be in the future' };
  }
  return null;
}

@Component({
  selector: 'app-create-application',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="create-container">
      <nav class="nav-back">
        <button class="btn btn-link" (click)="cancel()">← Back to Applications</button>
      </nav>

      <section class="form-card">
        <header class="form-header">
          <h1>Track New Application</h1>
          <p class="subtitle">Record your job details to start tracking its interview stages.</p>
        </header>

        <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <!-- Company -->
          <div class="form-group">
            <label for="company">Company <span class="required">*</span></label>
            <input
              id="company"
              type="text"
              formControlName="company"
              class="form-control"
              [class.is-invalid]="form.get('company')?.invalid && (form.get('company')?.touched || isSubmitted)"
              placeholder="e.g. Acme Corporation" />
            <div *ngIf="form.get('company')?.invalid && (form.get('company')?.touched || isSubmitted)" class="invalid-feedback">
              <span *ngIf="form.get('company')?.errors?.['required']">Company name is required.</span>
              <span *ngIf="form.get('company')?.errors?.['maxlength']">Company name cannot exceed 255 characters.</span>
              <span *ngIf="form.get('company')?.errors?.['serverError']">{{ form.get('company')?.errors?.['serverError'] }}</span>
            </div>
          </div>

          <!-- Role Title -->
          <div class="form-group">
            <label for="roleTitle">Role Title <span class="required">*</span></label>
            <input
              id="roleTitle"
              type="text"
              formControlName="roleTitle"
              class="form-control"
              [class.is-invalid]="form.get('roleTitle')?.invalid && (form.get('roleTitle')?.touched || isSubmitted)"
              placeholder="e.g. Senior Frontend Engineer" />
            <div *ngIf="form.get('roleTitle')?.invalid && (form.get('roleTitle')?.touched || isSubmitted)" class="invalid-feedback">
              <span *ngIf="form.get('roleTitle')?.errors?.['required']">Role title is required.</span>
              <span *ngIf="form.get('roleTitle')?.errors?.['maxlength']">Role title cannot exceed 255 characters.</span>
              <span *ngIf="form.get('roleTitle')?.errors?.['serverError']">{{ form.get('roleTitle')?.errors?.['serverError'] }}</span>
            </div>
          </div>

          <!-- Applied Date -->
          <div class="form-group">
            <label for="appliedDate">Applied Date</label>
            <input
              id="appliedDate"
              type="date"
              formControlName="appliedDate"
              class="form-control"
              [class.is-invalid]="form.get('appliedDate')?.invalid && (form.get('appliedDate')?.touched || isSubmitted)" />
            <div *ngIf="form.get('appliedDate')?.invalid && (form.get('appliedDate')?.touched || isSubmitted)" class="invalid-feedback">
              <span *ngIf="form.get('appliedDate')?.errors?.['futureDate']">Applied date cannot be in the future.</span>
              <span *ngIf="form.get('appliedDate')?.errors?.['serverError']">{{ form.get('appliedDate')?.errors?.['serverError'] }}</span>
            </div>
          </div>

          <!-- Job Description -->
          <div class="form-group">
            <div class="label-row">
              <label for="jdText">Job Description</label>
              <span class="char-counter" [class.over-limit]="jdCharCount > 20000">
                {{ jdCharCount | number }} / 20,000
              </span>
            </div>
            <textarea
              id="jdText"
              formControlName="jdText"
              class="form-control textarea"
              rows="6"
              [class.is-invalid]="form.get('jdText')?.invalid && (form.get('jdText')?.touched || isSubmitted)"
              placeholder="Paste job description, requirements, or qualifications..."></textarea>
            <div *ngIf="form.get('jdText')?.invalid && (form.get('jdText')?.touched || isSubmitted)" class="invalid-feedback">
              <span *ngIf="form.get('jdText')?.errors?.['maxlength']">Job description cannot exceed 20,000 characters.</span>
              <span *ngIf="form.get('jdText')?.errors?.['serverError']">{{ form.get('jdText')?.errors?.['serverError'] }}</span>
            </div>
          </div>

          <!-- General Error -->
          <div *ngIf="generalErrorMessage" class="alert-danger" role="alert">
            {{ generalErrorMessage }}
          </div>

          <!-- Action Buttons -->
          <div class="form-actions">
            <button type="button" class="btn btn-secondary" (click)="cancel()">Cancel</button>
            <button type="submit" class="btn btn-primary" [disabled]="isSubmitting">
              {{ isSubmitting ? 'Saving...' : 'Track Application' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  `,
  styles: [`
    .create-container {
      max-width: 680px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
      font-family: system-ui, -apple-system, sans-serif;
    }
    .nav-back { margin-bottom: 1.5rem; }
    .btn-link {
      background: none;
      border: none;
      color: #3182ce;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 500;
      padding: 0;
    }
    .btn-link:hover { text-decoration: underline; }
    .form-card {
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 2rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }
    .form-header h1 {
      margin: 0;
      font-size: 1.75rem;
      color: #1a202c;
    }
    .subtitle {
      margin: 0.25rem 0 1.5rem;
      color: #718096;
    }
    .form-group {
      margin-bottom: 1.5rem;
    }
    label {
      display: block;
      font-weight: 600;
      font-size: 0.9rem;
      color: #2d3748;
      margin-bottom: 0.4rem;
    }
    .required { color: #e53e3e; }
    .label-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .char-counter {
      font-size: 0.8rem;
      color: #a0aec0;
    }
    .char-counter.over-limit { color: #e53e3e; font-weight: 700; }
    .form-control {
      width: 100%;
      box-sizing: border-box;
      padding: 0.65rem 0.8rem;
      border: 1px solid #cbd5e0;
      border-radius: 6px;
      font-size: 1rem;
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .form-control:focus {
      outline: none;
      border-color: #3182ce;
      box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.2);
    }
    .form-control.is-invalid {
      border-color: #e53e3e;
    }
    .form-control.textarea {
      resize: vertical;
      font-family: inherit;
    }
    .invalid-feedback {
      color: #e53e3e;
      font-size: 0.85rem;
      margin-top: 0.35rem;
    }
    .alert-danger {
      background-color: #fff5f5;
      color: #c53030;
      border: 1px solid #feb2b2;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      margin-bottom: 1.5rem;
    }
    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 1rem;
      border-top: 1px solid #edf2f7;
      padding-top: 1.5rem;
    }
    .btn {
      padding: 0.65rem 1.4rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      border: none;
      font-size: 0.95rem;
      transition: all 0.2s;
    }
    .btn-primary {
      background-color: #3182ce;
      color: white;
    }
    .btn-primary:hover:not(:disabled) {
      background-color: #2b6cb0;
    }
    .btn-primary:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .btn-secondary {
      background-color: #edf2f7;
      color: #4a5568;
    }
    .btn-secondary:hover { background-color: #e2e8f0; }
  `]
})
export class CreateApplicationComponent {
  private fb = inject(FormBuilder);
  private service = inject(ApplicationService);
  private router = inject(Router);

  isSubmitted = false;
  isSubmitting = false;
  generalErrorMessage = '';

  todayDateStr = new Date().toISOString().substring(0, 10);

  form = this.fb.group({
    company: ['', [Validators.required, Validators.maxLength(255)]],
    roleTitle: ['', [Validators.required, Validators.maxLength(255)]],
    appliedDate: [this.todayDateStr, [pastOrPresentValidator]],
    jdText: ['', [Validators.maxLength(20000)]]
  });

  get jdCharCount(): number {
    return this.form.get('jdText')?.value?.length || 0;
  }

  onSubmit() {
    this.isSubmitted = true;
    this.generalErrorMessage = '';

    if (this.form.invalid) {
      return;
    }

    this.isSubmitting = true;
    const val = this.form.value;

    this.service.create({
      company: val.company!.trim(),
      roleTitle: val.roleTitle!.trim(),
      appliedDate: val.appliedDate || null,
      jdText: val.jdText?.trim() || null
    }).subscribe({
      next: (created) => {
        this.isSubmitting = false;
        this.router.navigate(['/applications', created.id]);
      },
      error: (err) => {
        this.isSubmitting = false;
        if (err.status === 400 && err.error?.errors) {
          // Map server validation errors back to controls
          const errors = err.error.errors as Record<string, string>;
          Object.keys(errors).forEach((field) => {
            const control = this.form.get(field);
            if (control) {
              control.setErrors({ serverError: errors[field] });
            }
          });
        } else {
          this.generalErrorMessage = 'Failed to create application. Please check your inputs.';
        }
      }
    });
  }

  cancel() {
    this.router.navigate(['/applications']);
  }
}

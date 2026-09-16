import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  template: `
    <div class="auth-container">
      <h1>Forgot Password</h1>
      
      <p class="description">Enter your email address and we'll send you a link to reset your password.</p>
      
      <form *ngIf="!isSuccess" [formGroup]="forgotForm" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" aria-required="true" />
        </div>

        <div *ngIf="errorMessage" class="error-message" role="alert">
          {{ errorMessage }}
        </div>

        <button type="submit" [disabled]="forgotForm.invalid || isLoading">
          {{ isLoading ? 'Sending...' : 'Send Reset Link' }}
        </button>
      </form>
      
      <div *ngIf="isSuccess" class="success-message" role="status">
        If an account with that email exists, a reset link has been sent.
      </div>

      <div class="auth-links">
        <a routerLink="/auth/login">Back to Log In</a>
      </div>
    </div>
  `
})
export class ForgotPasswordComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

  forgotForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  isLoading = false;
  isSuccess = false;
  errorMessage = '';

  onSubmit() {
    if (this.forgotForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';
    
    const { email } = this.forgotForm.value;
    
    this.authService.requestPasswordReset(email!).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSuccess = true;
      },
      error: (err) => {
        this.isLoading = false;
        // Check for specific error status if needed
        if (err.status === 429) {
          this.errorMessage = 'Too many requests. Please try again later.';
        } else if (err.status >= 500) {
          this.errorMessage = 'Service unavailable. Please try again later.';
        } else if (err.status === 0) {
          this.errorMessage = 'Network error. Please check your connection.';
        } else {
          // Anti-enumeration: For 400/404, we don't disclose account existence
          this.isSuccess = true;
        }
      }
    });
  }
}

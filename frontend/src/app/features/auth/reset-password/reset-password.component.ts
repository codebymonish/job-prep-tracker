import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AuthStateService } from '../../../core/services/auth-state.service';
import { NgIf } from '@angular/common';

export function passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password');
  const confirmPassword = control.get('confirmPassword');
  
  if (password && confirmPassword && password.value !== confirmPassword.value) {
    return { passwordMismatch: true };
  }
  return null;
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  template: `
    <div class="auth-container">
      <h1>Reset Password</h1>
      
      <div *ngIf="status === 'missing_token'" class="error-message" role="alert">
        <p>No reset token provided. Please use the link from your email.</p>
        <a routerLink="/auth/login" class="btn">Back to Login</a>
      </div>

      <form *ngIf="status === 'ready'" [formGroup]="resetForm" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <label for="password">New Password</label>
          <input id="password" type="password" formControlName="password" aria-required="true" />
        </div>

        <div class="form-group">
          <label for="confirmPassword">Confirm Password</label>
          <input id="confirmPassword" type="password" formControlName="confirmPassword" aria-required="true" />
          <div *ngIf="resetForm.errors?.['passwordMismatch'] && resetForm.get('confirmPassword')?.touched" 
               class="field-error" role="alert">
            Passwords do not match.
          </div>
        </div>

        <div *ngIf="errorMessage" class="error-message" role="alert">
          {{ errorMessage }}
        </div>

        <button type="submit" [disabled]="resetForm.invalid || isLoading">
          {{ isLoading ? 'Resetting...' : 'Reset Password' }}
        </button>
      </form>
    </div>
  `
})
export class ResetPasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private authState = inject(AuthStateService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  token: string | null = null;
  status: 'missing_token' | 'ready' = 'ready';
  isLoading = false;
  errorMessage = '';

  resetForm = this.fb.group({
    password: ['', Validators.required],
    confirmPassword: ['', Validators.required]
  }, { validators: passwordMatchValidator });

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token');
    
    // Clean URL immediately
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true,
      preserveFragment: false
    });

    if (!this.token) {
      this.status = 'missing_token';
    }
  }

  onSubmit() {
    if (this.resetForm.invalid || !this.token) return;

    this.isLoading = true;
    this.errorMessage = '';
    
    const { password } = this.resetForm.value;
    
    this.authService.resetPassword(this.token, password!).subscribe({
      next: () => {
        // Backend revokes refresh token families, but stateless JWT might still be valid
        // Force unauthenticated state locally so we don't use it
        this.authState.setUnauthenticated();
        this.router.navigate(['/auth/login'], { 
          state: { message: 'Password has been reset successfully. Please log in.' } 
        });
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 400 || err.status === 404) {
          this.errorMessage = 'The reset link is invalid or has expired.';
        } else {
          this.errorMessage = 'An error occurred. Please try again.';
        }
      }
    });
  }
}

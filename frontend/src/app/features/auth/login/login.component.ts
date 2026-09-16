import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { isSafeInternalUrl } from '../../../core/utils/safe-url.util';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  template: `
    <div class="auth-container">
      <h1>Log In</h1>
      
      <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" 
                 [attr.aria-invalid]="loginForm.get('email')?.invalid && loginForm.get('email')?.touched"
                 aria-required="true" />
        </div>
        
        <div class="form-group">
          <label for="password">Password</label>
          <input id="password" type="password" formControlName="password" 
                 [attr.aria-invalid]="loginForm.get('password')?.invalid && loginForm.get('password')?.touched"
                 aria-required="true" />
        </div>

        <div *ngIf="errorMessage" class="error-message" role="alert">
          {{ errorMessage }}
        </div>

        <button type="submit" [disabled]="loginForm.invalid || isLoading">
          {{ isLoading ? 'Logging in...' : 'Log In' }}
        </button>
      </form>
      
      <div class="auth-links">
        <a routerLink="/auth/forgot-password">Forgot Password?</a>
        <a routerLink="/auth/register">Create an Account</a>
      </div>
    </div>
  `
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  isLoading = false;
  errorMessage = '';

  onSubmit() {
    if (this.loginForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';
    
    const { email, password } = this.loginForm.value;
    
    this.authService.login({ email: email!, password: password! }).subscribe({
      next: () => {
        this.isLoading = false;
        const returnUrl = this.route.snapshot.queryParams['returnUrl'];
        const safeReturnUrl = isSafeInternalUrl(returnUrl) ? returnUrl : '/applications';
        this.router.navigateByUrl(safeReturnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        
        if (err.status === 401) {
          // As specified: "all current non-ACTIVE account failures return 401 from backend"
          // We provide a generic safe authentication message
          this.errorMessage = 'Invalid email or password. Please try again.';
        } else if (err.status === 429) {
          this.errorMessage = 'Too many login attempts. Please try again later.';
        } else {
          this.errorMessage = 'An unexpected error occurred. Please try again.';
        }
      }
    });
  }
}

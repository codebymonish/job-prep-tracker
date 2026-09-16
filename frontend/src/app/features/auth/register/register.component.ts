import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../../../core/models/auth.dto';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  template: `
    <div class="auth-container">
      <h1>Create Account</h1>
      
      <form [formGroup]="registerForm" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <label for="name">Full Name</label>
          <input id="name" type="text" formControlName="name" aria-required="true" />
          <div *ngIf="validationErrors['name']" class="field-error" role="alert">{{ validationErrors['name'] }}</div>
        </div>

        <div class="form-group">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" aria-required="true" />
          <div *ngIf="validationErrors['email']" class="field-error" role="alert">{{ validationErrors['email'] }}</div>
        </div>
        
        <div class="form-group">
          <label for="password">Password</label>
          <input id="password" type="password" formControlName="password" aria-required="true" />
          <div *ngIf="validationErrors['password']" class="field-error" role="alert">{{ validationErrors['password'] }}</div>
        </div>

        <div *ngIf="globalError" class="error-message" role="alert">
          {{ globalError }}
        </div>
        
        <div *ngIf="isSuccess" class="success-message" role="status">
          Registration successful! Please check your email to verify your account.
        </div>

        <button type="submit" [disabled]="registerForm.invalid || isLoading || isSuccess">
          {{ isLoading ? 'Registering...' : 'Register' }}
        </button>
      </form>
      
      <div class="auth-links">
        <a routerLink="/auth/login">Already have an account? Log in</a>
      </div>
    </div>
  `
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

  registerForm = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  isLoading = false;
  isSuccess = false;
  globalError = '';
  validationErrors: Record<string, string> = {};

  onSubmit() {
    if (this.registerForm.invalid) return;

    this.isLoading = true;
    this.globalError = '';
    this.validationErrors = {};
    
    const { name, email, password } = this.registerForm.value;
    
    this.authService.register({ name: name!, email: email!, password: password! }).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSuccess = true;
        this.registerForm.reset();
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        
        if (err.status === 409) {
          const problem = err.error as ProblemDetail;
          if (problem?.type?.endsWith('/email-exists')) {
            this.globalError = 'An account with this email already exists.';
          } else {
            this.globalError = problem?.detail || 'Registration failed due to a conflict.';
          }
        } else if (err.status === 400) {
          const problem = err.error as ProblemDetail;
          if (problem?.errors) {
            this.validationErrors = problem.errors;
          } else {
            this.globalError = problem?.detail || 'Invalid input provided.';
          }
        } else {
          this.globalError = 'An unexpected error occurred. Please try again.';
        }
      }
    });
  }
}

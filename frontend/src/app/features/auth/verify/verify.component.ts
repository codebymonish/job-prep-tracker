import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [NgIf, RouterLink],
  template: `
    <div class="auth-container">
      <h1>Email Verification</h1>
      
      <div *ngIf="status === 'verifying'" role="status">
        <p>Verifying your email address. Please wait...</p>
      </div>

      <div *ngIf="status === 'success'" class="success-message" role="alert">
        <p>Your email has been successfully verified!</p>
        <a routerLink="/auth/login" class="btn">Log In</a>
      </div>

      <div *ngIf="status === 'error'" class="error-message" role="alert">
        <p>{{ errorMessage }}</p>
        <a routerLink="/auth/login" class="btn">Return to Login</a>
      </div>
    </div>
  `
})
export class VerifyComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  
  status: 'verifying' | 'success' | 'error' = 'verifying';
  errorMessage = '';

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    
    // Clean URL immediately
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true,
      preserveFragment: false
    });

    if (!token) {
      this.status = 'error';
      this.errorMessage = 'No verification token provided.';
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: () => {
        this.status = 'success';
      },
      error: () => {
        this.status = 'error';
        this.errorMessage = 'The verification link is invalid, expired, or has already been used.';
      }
    });
  }
}

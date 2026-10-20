import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NavHeaderComponent } from './core/components/nav-header/nav-header.component';
import { AuthStateService } from './core/services/auth-state.service';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule, NavHeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private authState = inject(AuthStateService);
  private authService = inject(AuthService);

  isAuthenticated = false;

  ngOnInit(): void {
    this.authService.initializeAuth().subscribe();
    this.authState.state$.subscribe(s => {
      this.isAuthenticated = s.status === 'AUTHENTICATED';
    });
  }
}

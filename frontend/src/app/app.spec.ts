import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { AuthService } from './core/services/auth.service';
import { AuthStateService } from './core/services/auth-state.service';
import { provideRouter } from '@angular/router';
import { of, BehaviorSubject } from 'rxjs';
import { AuthState } from './core/models/auth-state.model';

describe('App', () => {
  beforeEach(async () => {
    const initialState: AuthState = {
      status: 'UNAUTHENTICATED',
      accessToken: null,
      tokenExpiresAt: null,
      user: null
    };
    const stateSubject = new BehaviorSubject<AuthState>(initialState);

    const mockAuthService = jasmine.createSpyObj('AuthService', ['initializeAuth', 'logout']);
    mockAuthService.initializeAuth.and.returnValue(of(false));
    mockAuthService.logout.and.returnValue(of(undefined));

    const mockAuthState = jasmine.createSpyObj('AuthStateService', [], {
      state$: stateSubject.asObservable(),
      snapshot: initialState
    });

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
        { provide: AuthStateService, useValue: mockAuthState }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});

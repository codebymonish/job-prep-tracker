import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { adminGuard } from './admin.guard';
import { AuthStateService } from '../services/auth-state.service';
import { BehaviorSubject } from 'rxjs';
import { AuthState } from '../models/auth-state.model';
import { runInInjectionContext, EnvironmentInjector } from '@angular/core';

describe('adminGuard', () => {
  let router: Router;
  let authStateServiceSpy: jasmine.SpyObj<AuthStateService>;
  let stateSubject: BehaviorSubject<AuthState>;
  let injector: EnvironmentInjector;

  beforeEach(() => {
    stateSubject = new BehaviorSubject<AuthState>({
      status: 'INITIALIZING',
      accessToken: null,
      tokenExpiresAt: null,
      user: null
    });

    authStateServiceSpy = jasmine.createSpyObj('AuthStateService', [], {
      state$: stateSubject.asObservable()
    });

    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([])],
      providers: [
        { provide: AuthStateService, useValue: authStateServiceSpy }
      ]
    });

    router = TestBed.inject(Router);
    injector = TestBed.inject(EnvironmentInjector);
  });

  it('should wait for INITIALIZING to complete', (done) => {
    runInInjectionContext(injector, () => {
      const guardResult = adminGuard({} as any, { url: '/admin' } as any) as any;
      guardResult.subscribe((result: UrlTree | boolean) => {
        expect(result).toBeTrue();
        done();
      });
      
      stateSubject.next({
        status: 'AUTHENTICATED',
        accessToken: 'token',
        tokenExpiresAt: null,
        user: { role: 'ADMIN', id: '1', email: 'test@test.com' }
      });
    });
  });

  it('should return true if ADMIN', (done) => {
    stateSubject.next({
      status: 'AUTHENTICATED',
      accessToken: 'token',
      tokenExpiresAt: null,
      user: { role: 'ADMIN', id: '1', email: 'test@test.com' }
    });

    runInInjectionContext(injector, () => {
      const guardResult = adminGuard({} as any, { url: '/admin' } as any) as any;
      guardResult.subscribe((result: boolean) => {
        expect(result).toBeTrue();
        done();
      });
    });
  });

  it('should redirect to /applications if USER', (done) => {
    stateSubject.next({
      status: 'AUTHENTICATED',
      accessToken: 'token',
      tokenExpiresAt: null,
      user: { role: 'USER', id: '1', email: 'test@test.com' }
    });

    runInInjectionContext(injector, () => {
      const guardResult = adminGuard({} as any, { url: '/admin' } as any) as any;
      guardResult.subscribe((result: UrlTree) => {
        expect(result instanceof UrlTree).toBeTrue();
        expect(result.toString()).toBe('/applications');
        done();
      });
    });
  });

  it('should redirect to /auth/login with returnUrl if unauthenticated', (done) => {
    stateSubject.next({
      status: 'UNAUTHENTICATED',
      accessToken: null,
      tokenExpiresAt: null,
      user: null
    });

    runInInjectionContext(injector, () => {
      const guardResult = adminGuard({} as any, { url: '/admin' } as any) as any;
      guardResult.subscribe((result: UrlTree) => {
        expect(result instanceof UrlTree).toBeTrue();
        expect(result.toString()).toBe('/auth/login?returnUrl=%2Fadmin');
        done();
      });
    });
  });
});

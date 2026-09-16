import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { guestGuard } from './guest.guard';
import { AuthStateService } from '../services/auth-state.service';
import { BehaviorSubject } from 'rxjs';
import { AuthState } from '../models/auth-state.model';
import { runInInjectionContext, EnvironmentInjector } from '@angular/core';

describe('guestGuard', () => {
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
      const guardResult = guestGuard({} as any, {} as any) as any;
      guardResult.subscribe((result: UrlTree | boolean) => {
        expect(result).toBeTrue();
        done();
      });
      
      stateSubject.next({
        status: 'UNAUTHENTICATED',
        accessToken: null,
        tokenExpiresAt: null,
        user: null
      });
    });
  });

  it('should return true if unauthenticated', (done) => {
    stateSubject.next({
      status: 'UNAUTHENTICATED',
      accessToken: null,
      tokenExpiresAt: null,
      user: null
    });

    runInInjectionContext(injector, () => {
      const guardResult = guestGuard({} as any, {} as any) as any;
      guardResult.subscribe((result: boolean) => {
        expect(result).toBeTrue();
        done();
      });
    });
  });

  it('should redirect to /applications if authenticated', (done) => {
    stateSubject.next({
      status: 'AUTHENTICATED',
      accessToken: 'token',
      tokenExpiresAt: null,
      user: null
    });

    runInInjectionContext(injector, () => {
      const guardResult = guestGuard({} as any, {} as any) as any;
      guardResult.subscribe((result: UrlTree) => {
        expect(result instanceof UrlTree).toBeTrue();
        expect(result.toString()).toBe('/applications');
        done();
      });
    });
  });
});

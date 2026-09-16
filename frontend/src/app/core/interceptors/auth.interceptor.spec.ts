import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { AuthStateService } from '../services/auth-state.service';
import { AuthRefreshCoordinatorService } from './auth-refresh-coordinator.service';
import { withInterceptors } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Auth Interceptor & Single-Flight Refresh', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let authState: AuthStateService;
  let router: Router;
  let coordinator: AuthRefreshCoordinatorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([
        { path: 'auth/login', children: [] }
      ])],
      providers: [
        AuthService,
        AuthStateService,
        AuthRefreshCoordinatorService,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    authState = TestBed.inject(AuthStateService);
    router = TestBed.inject(Router);
    coordinator = TestBed.inject(AuthRefreshCoordinatorService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('Exactly one refresh HTTP call for burst of concurrent 401s', fakeAsync(() => {
    // Given: authState is AUTHENTICATED with token T0
    const mockUser = btoa(JSON.stringify({ sub: '123', email: 'test@test.com', role: 'USER' }));
    const t0 = `header.${mockUser}.signature`;
    authState.setAuthenticated(t0);
    
    spyOn(authService, 'refresh').and.callThrough();

    let r1Success = false;
    let r2Success = false;

    // 1. Issue protected request R1.
    http.get('/api/protected/1').subscribe(() => { r1Success = true; });
    const req1 = httpMock.expectOne('/api/protected/1');
    expect(req1.request.headers.get('Authorization')).toEqual(`Bearer ${t0}`);

    // 2. Issue protected request R2 before either request receives its 401.
    http.get('/api/protected/2').subscribe(() => { r2Success = true; });
    const req2 = httpMock.expectOne('/api/protected/2');
    expect(req2.request.headers.get('Authorization')).toEqual(`Bearer ${t0}`);

    // 4. Produce 401 responses for both while refresh remains unresolved.
    req1.flush({}, { status: 401, statusText: 'Unauthorized' });
    req2.flush({}, { status: 401, statusText: 'Unauthorized' });

    // 5. Verify exactly ONE underlying /auth/refresh HTTP request exists.
    const refreshReq = httpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');
    expect(refreshReq.request.method).toEqual('POST');
    
    // 6. Complete that one refresh request with a new access token.
    const t1 = `header.${btoa(JSON.stringify({ sub: '123', email: 'test@test.com', role: 'USER', exp: 9999999999 }))}.signature`;
    refreshReq.flush({ accessToken: t1, expiresIn: 3600 });
    
    tick();

    // 7. Verify R1 retries with the new token.
    const retry1 = httpMock.expectOne('/api/protected/1');
    expect(retry1.request.headers.get('Authorization')).toEqual(`Bearer ${t1}`);
    retry1.flush({});

    // 8. Verify R2 retries with the same new token.
    const retry2 = httpMock.expectOne('/api/protected/2');
    expect(retry2.request.headers.get('Authorization')).toEqual(`Bearer ${t1}`);
    retry2.flush({});
    
    tick();

    // 9. Verify no second /auth/refresh request occurs.
    httpMock.expectNone('http://localhost:8080/api/v1/auth/refresh');
    expect(authService.refresh).toHaveBeenCalledTimes(1);
    expect(r1Success).toBeTrue();
    expect(r2Success).toBeTrue();
    
    // Check coordinator cleanup
    expect((coordinator as any)._refresh$).toBeNull();
  }));

  it('Refresh endpoint 401 does not loop', fakeAsync(() => {
    spyOn(authService, 'refresh').and.callThrough();
    
    let errorStatus = 0;
    http.post('http://localhost:8080/api/v1/auth/refresh', {}).subscribe({
      error: (err) => { errorStatus = err.status; }
    });

    const refreshReq = httpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');
    refreshReq.flush({}, { status: 401, statusText: 'Unauthorized' });
    
    tick();

    expect(errorStatus).toEqual(401);
    expect(authService.refresh).not.toHaveBeenCalled();
    httpMock.expectNone('http://localhost:8080/api/v1/auth/refresh');
  }));

  it('Logout fires while refresh in flight - throws error and does not retry', fakeAsync(() => {
    const mockUser = btoa(JSON.stringify({ sub: '123' }));
    const t0 = `header.${mockUser}.signature`;
    authState.setAuthenticated(t0);
    
    let errorCaught = false;
    
    http.get('/api/protected/3').subscribe({
      error: () => { errorCaught = true; }
    });
    
    const req = httpMock.expectOne('/api/protected/3');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
    
    const refreshReq = httpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');
    
    // Logout is called BEFORE refresh completes
    authState.setUnauthenticated();
    
    // Refresh completes
    refreshReq.flush({ accessToken: 'T1', expiresIn: 3600 });
    tick();
    
    // Should NOT retry the request
    httpMock.expectNone('/api/protected/3');
    expect(errorCaught).toBeTrue();
  }));
});

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { AuthStateService } from './auth-state.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let authState: AuthStateService;
  const apiUrl = `${environment.apiUrl}/auth`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, AuthStateService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    authState = TestBed.inject(AuthStateService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('initializeAuth calls refresh and sets authenticated on success', () => {
    let result = false;
    service.initializeAuth().subscribe(res => result = res);

    const req = httpMock.expectOne(`${apiUrl}/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');

    const mockUser = btoa(JSON.stringify({ sub: '123' }));
    req.flush({ accessToken: `header.${mockUser}.signature`, expiresIn: 3600 });

    expect(result).toBeTrue();
    expect(authState.snapshot.status).toBe('AUTHENTICATED');
  });

  it('initializeAuth catches error and sets unauthenticated on failure', () => {
    let result = true;
    service.initializeAuth().subscribe(res => result = res);

    const req = httpMock.expectOne(`${apiUrl}/refresh`);
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeFalse();
    expect(authState.snapshot.status).toBe('UNAUTHENTICATED');
  });

  it('login updates auth state', () => {
    service.login({ email: 'a@a.com', password: 'pw' }).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/login`);
    expect(req.request.withCredentials).toBeTrue();

    const mockUser = btoa(JSON.stringify({ sub: '123' }));
    req.flush({ accessToken: `header.${mockUser}.signature`, expiresIn: 3600 });

    expect(authState.snapshot.status).toBe('AUTHENTICATED');
  });

  it('logout clears state and makes HTTP call', () => {
    const mockUser = btoa(JSON.stringify({ sub: '123' }));
    authState.setAuthenticated(`header.${mockUser}.signature`);
    
    service.logout().subscribe();

    // Clears state immediately
    expect(authState.snapshot.status).toBe('UNAUTHENTICATED');

    const req = httpMock.expectOne(`${apiUrl}/logout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({});
  });
});

import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { errorInterceptor } from './error.interceptor';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Error Interceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    spyOn(console, 'error');
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('skips auth endpoint errors', () => {
    let errorCaught = false;
    http.post('/api/v1/auth/login', {}).subscribe({
      error: () => { errorCaught = true; }
    });

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(errorCaught).toBeTrue();
    expect(console.error).not.toHaveBeenCalled();
  });

  it('handles global 500 error', () => {
    let errorCaught = false;
    http.get('/api/protected/data').subscribe({
      error: () => { errorCaught = true; }
    });

    const req = httpMock.expectOne('/api/protected/data');
    req.flush({ correlationId: '1234-abcd' }, { status: 500, statusText: 'Server Error' });

    expect(errorCaught).toBeTrue();
    expect(console.error).toHaveBeenCalledWith('GLOBAL ERROR:', 'Server error. Please try again later. (ID: 1234-abcd)');
  });

  it('handles 429 error', () => {
    http.get('/api/protected/data').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/protected/data');
    req.flush({}, { status: 429, statusText: 'Too Many Requests' });

    expect(console.error).toHaveBeenCalledWith('GLOBAL ERROR:', 'Too many requests. Please try again later.');
  });

  it('handles PENDING_DELETION 403 error', () => {
    http.get('/api/protected/data').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/protected/data');
    req.flush({ type: 'https://api.jobtracker.app/errors/account-pending-deletion' }, { status: 403, statusText: 'Forbidden' });

    expect(console.error).toHaveBeenCalledWith('GLOBAL ERROR:', 'Your account is pending deletion and cannot access this resource.');
  });
});

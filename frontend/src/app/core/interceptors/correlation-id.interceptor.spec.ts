import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { correlationIdInterceptor } from './correlation-id.interceptor';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Correlation ID Interceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationIdInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds X-Correlation-ID header to outgoing requests', () => {
    http.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('X-Correlation-ID')).toBeTrue();
    const id = req.request.headers.get('X-Correlation-ID');
    expect(id).toBeTruthy();
    expect(id?.length).toBeGreaterThan(0);
    req.flush({});
  });

  it('generates unique IDs for each request', () => {
    http.get('/api/test1').subscribe();
    http.get('/api/test2').subscribe();

    const req1 = httpMock.expectOne('/api/test1');
    const req2 = httpMock.expectOne('/api/test2');

    const id1 = req1.request.headers.get('X-Correlation-ID');
    const id2 = req2.request.headers.get('X-Correlation-ID');

    expect(id1).not.toEqual(id2);
    
    req1.flush({});
    req2.flush({});
  });
});

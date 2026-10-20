import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApplicationService } from './application.service';
import { environment } from '../../../environments/environment';
import {
  ApplicationDetail,
  ApplicationStatus,
  ApplicationSummary,
  CreateApplicationRequest,
  PagedResponse,
  UpdateStatusRequest
} from '../models/application.model';

describe('ApplicationService', () => {
  let service: ApplicationService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/applications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApplicationService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ApplicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() sends GET with default pagination params', () => {
    const mockResponse: PagedResponse<ApplicationSummary> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20
    };

    service.list().subscribe((res) => {
      expect(res).toEqual(mockResponse);
    });

    const req = httpMock.expectOne((request) =>
      request.url === apiUrl &&
      request.params.get('page') === '0' &&
      request.params.get('size') === '20' &&
      !request.params.has('status')
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('list() appends status param when provided', () => {
    service.list(1, 10, 'INTERVIEWING').subscribe();

    const req = httpMock.expectOne((request) =>
      request.url === apiUrl &&
      request.params.get('page') === '1' &&
      request.params.get('size') === '10' &&
      request.params.get('status') === 'INTERVIEWING'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 10 });
  });

  it('getById() sends GET to specific application URL', () => {
    const mockDetail: ApplicationDetail = {
      id: 'app-1',
      company: 'Google',
      roleTitle: 'Software Engineer',
      jdText: 'Job details',
      status: 'APPLIED',
      appliedDate: '2026-10-12',
      version: 0,
      createdAt: '2026-10-12T10:00:00Z'
    };

    service.getById('app-1').subscribe((res) => {
      expect(res.company).toBe('Google');
    });

    const req = httpMock.expectOne(`${apiUrl}/app-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('create() sends POST with request body', () => {
    const createReq: CreateApplicationRequest = {
      company: 'Meta',
      roleTitle: 'Product Manager'
    };
    const mockDetail: ApplicationDetail = {
      id: 'app-2',
      company: 'Meta',
      roleTitle: 'Product Manager',
      jdText: null,
      status: 'APPLIED',
      appliedDate: '2026-10-12',
      version: 0,
      createdAt: '2026-10-12T10:00:00Z'
    };

    service.create(createReq).subscribe((res) => {
      expect(res.id).toBe('app-2');
    });

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(createReq);
    req.flush(mockDetail);
  });

  it('updateStatus() sends PATCH to status endpoint with {status, version}', () => {
    const updateReq: UpdateStatusRequest = {
      status: 'INTERVIEWING',
      version: 0
    };

    service.updateStatus('app-1', updateReq).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/app-1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(updateReq);
    req.flush({});
  });

  it('delete() sends DELETE request', () => {
    service.delete('app-1').subscribe();

    const req = httpMock.expectOne(`${apiUrl}/app-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('getStats() sends GET request to /stats', () => {
    const mockStats = { total: 5, applied: 2, interviewing: 1, offer: 1, rejected: 1, withdrawn: 0 };

    service.getStats().subscribe(stats => {
      expect(stats).toEqual(mockStats);
    });

    const req = httpMock.expectOne(`${apiUrl}/stats`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStats);
  });
});

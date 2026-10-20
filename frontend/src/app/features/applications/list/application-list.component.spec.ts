import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NEVER, of, throwError } from 'rxjs';
import { ApplicationListComponent } from './application-list.component';
import { ApplicationService } from '../../../core/services/application.service';
import { ApplicationSummary, PagedResponse } from '../../../core/models/application.model';

describe('ApplicationListComponent', () => {
  let component: ApplicationListComponent;
  let fixture: ComponentFixture<ApplicationListComponent>;
  let serviceSpy: jasmine.SpyObj<ApplicationService>;
  let router: Router;

  const mockApps: ApplicationSummary[] = [
    {
      id: 'app-1',
      company: 'Netflix',
      roleTitle: 'Senior Engineer',
      status: 'APPLIED',
      appliedDate: '2026-10-10',
      version: 0,
      createdAt: '2026-10-10T12:00:00Z'
    },
    {
      id: 'app-2',
      company: 'Apple',
      roleTitle: 'iOS Dev',
      status: 'INTERVIEWING',
      appliedDate: '2026-10-09',
      version: 1,
      createdAt: '2026-10-09T12:00:00Z'
    }
  ];

  const emptyResponse: PagedResponse<ApplicationSummary> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 10
  };

  const populatedResponse: PagedResponse<ApplicationSummary> = {
    content: mockApps,
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 10
  };

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('ApplicationService', ['list']);

    await TestBed.configureTestingModule({
      imports: [ApplicationListComponent, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: ApplicationService, useValue: serviceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.stub();
    fixture = TestBed.createComponent(ApplicationListComponent);
    component = fixture.componentInstance;
  });

  it('renders loading skeletons while service.list() is pending', () => {
    serviceSpy.list.and.returnValue(NEVER);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="loading-skeletons"]')).toBeTruthy();
  });

  it('renders application cards after service.list() succeeds with items', () => {
    serviceSpy.list.and.returnValue(of(populatedResponse));
    fixture.detectChanges(); // triggers ngOnInit

    expect(component.isLoading).toBeFalse();
    expect(component.applications.length).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.application-card');
    expect(cards.length).toBe(2);
    expect(compiled.textContent).toContain('Netflix');
    expect(compiled.textContent).toContain('Apple');
  });

  it('renders empty state when no applications are returned', () => {
    serviceSpy.list.and.returnValue(of(emptyResponse));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="empty-state"]')).toBeTruthy();
    expect(compiled.querySelectorAll('.application-card').length).toBe(0);
  });

  it('renders error message and retry button when service.list() fails', () => {
    serviceSpy.list.and.returnValue(throwError(() => new Error('Server error')));
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(component.errorMessage).toBeTruthy();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error-card')).toBeTruthy();
  });

  it('calls service.list() with status filter when filter tab is clicked', () => {
    serviceSpy.list.and.returnValue(of(populatedResponse));
    fixture.detectChanges();

    component.filterByStatus('INTERVIEWING');
    expect(serviceSpy.list).toHaveBeenCalledWith(0, 10, 'INTERVIEWING');
  });

  it('navigates to /applications/new on goToNew()', () => {
    component.goToNew();
    expect(router.navigate).toHaveBeenCalledWith(['/applications/new']);
  });

  it('navigates to /applications/:id when viewDetail is called', () => {
    component.viewDetail('app-1');
    expect(router.navigate).toHaveBeenCalledWith(['/applications', 'app-1']);
  });
});

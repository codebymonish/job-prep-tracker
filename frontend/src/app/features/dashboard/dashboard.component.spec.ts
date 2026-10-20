import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { ApplicationService } from '../../core/services/application.service';
import { AuthStateService } from '../../core/services/auth-state.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApplicationStats } from '../../core/models/application.model';

const mockStats: ApplicationStats = {
  total: 10, applied: 4, interviewing: 3, offer: 1, rejected: 1, withdrawn: 1
};

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let mockService: jasmine.SpyObj<ApplicationService>;
  let mockAuthState: jasmine.SpyObj<AuthStateService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    mockService = jasmine.createSpyObj('ApplicationService', ['getStats']);
    mockService.getStats.and.returnValue(of(mockStats));

    mockAuthState = jasmine.createSpyObj('AuthStateService', [], {
      snapshot: { status: 'AUTHENTICATED', user: { id: 'u1', email: 'alice@example.com', role: 'USER' }, accessToken: 'tok', tokenExpiresAt: null }
    });

    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: ApplicationService, useValue: mockService },
        { provide: AuthStateService, useValue: mockAuthState },
        { provide: Router, useValue: mockRouter }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call getStats on init', () => {
    expect(mockService.getStats).toHaveBeenCalled();
  });

  it('should set stats after successful load', () => {
    expect(component.stats).toEqual(mockStats);
    expect(component.isLoading).toBeFalse();
    expect(component.errorMessage).toBe('');
  });

  it('should derive firstName from email', () => {
    expect(component.firstName).toBe('alice');
  });

  it('should set errorMessage on failure', () => {
    mockService.getStats.and.returnValue(throwError(() => new Error('fail')));
    component.loadStats();
    fixture.detectChanges();
    expect(component.errorMessage).toBeTruthy();
    expect(component.isLoading).toBeFalse();
  });

  it('should navigate to /applications on goToApplications()', () => {
    component.goToApplications();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/applications']);
  });

  it('should navigate to /applications/new on goToNew()', () => {
    component.goToNew();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/applications/new']);
  });

  it('navigateToFilter with "total" navigates to /applications without query params', () => {
    component.navigateToFilter('total');
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/applications']);
  });

  it('navigateToFilter with "applied" navigates with status query param', () => {
    component.navigateToFilter('applied');
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/applications'], { queryParams: { status: 'APPLIED' } });
  });

  it('should have 6 stat cards defined', () => {
    expect(component.statCards.length).toBe(6);
  });

  it('should render new application button', () => {
    const btn = fixture.nativeElement.querySelector('#dashboard-new-app-btn');
    expect(btn).toBeTruthy();
  });
});

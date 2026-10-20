import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavHeaderComponent } from './nav-header.component';
import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

describe('NavHeaderComponent', () => {
  let component: NavHeaderComponent;
  let fixture: ComponentFixture<NavHeaderComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockAuthState: jasmine.SpyObj<AuthStateService>;
  let router: Router;

  beforeEach(async () => {
    mockAuthService = jasmine.createSpyObj('AuthService', ['logout']);
    mockAuthService.logout.and.returnValue(of(undefined));

    mockAuthState = jasmine.createSpyObj('AuthStateService', [], {
      snapshot: {
        status: 'AUTHENTICATED',
        user: { id: 'u1', email: 'test@example.com', role: 'USER' },
        accessToken: 'tok',
        tokenExpiresAt: null
      }
    });

    await TestBed.configureTestingModule({
      imports: [NavHeaderComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
        { provide: AuthStateService, useValue: mockAuthState }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NavHeaderComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display user email from auth state', () => {
    expect(component.userEmail).toBe('test@example.com');
  });

  it('should call logout and navigate on logout()', () => {
    const navigateSpy = spyOn(router, 'navigate').and.stub();
    component.logout();
    expect(mockAuthService.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should render Dashboard and Applications nav links', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const links = compiled.querySelectorAll('.nav-link');
    const texts = Array.from(links).map(l => l.textContent?.trim());
    expect(texts).toContain('Dashboard');
    expect(texts).toContain('Applications');
  });

  it('should render logout button with correct id', () => {
    const btn = fixture.nativeElement.querySelector('#logout-btn');
    expect(btn).toBeTruthy();
  });
});

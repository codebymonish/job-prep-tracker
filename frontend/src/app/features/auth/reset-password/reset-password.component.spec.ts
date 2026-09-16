import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/services/auth.service';
import { AuthStateService } from '../../../core/services/auth-state.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder } from '@angular/forms';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['resetPassword']);
    const authStateSpy = jasmine.createSpyObj('AuthStateService', ['setUnauthenticated']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree']);

    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        FormBuilder,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AuthStateService, useValue: authStateSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => key === 'token' ? 'test-token' : null
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
  });

  it('cleans up URL using Angular Router replaceUrl', () => {
    fixture.detectChanges(); // calls ngOnInit
    
    expect(routerSpy.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      replaceUrl: true,
      queryParams: {},
      preserveFragment: false
    }));
  });
});

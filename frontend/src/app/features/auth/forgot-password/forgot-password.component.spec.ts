import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/services/auth.service';
import { RouterTestingModule } from '@angular/router/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['requestPasswordReset']);

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent, ReactiveFormsModule, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('anti-enumeration: shows success message on 200 OK', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(of(void 0));
    component.forgotForm.controls['email'].setValue('test@test.com');
    component.onSubmit();
    expect(component.isSuccess).toBeTrue();
  });

  it('anti-enumeration: shows success message on 404', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    component.forgotForm.controls['email'].setValue('test@test.com');
    component.onSubmit();
    expect(component.isSuccess).toBeTrue();
  });

  it('shows 429 error message', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.forgotForm.controls['email'].setValue('test@test.com');
    component.onSubmit();
    expect(component.isSuccess).toBeFalse();
    expect(component.errorMessage).toContain('Too many requests');
  });

  it('shows generic 5xx error message', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.forgotForm.controls['email'].setValue('test@test.com');
    component.onSubmit();
    expect(component.isSuccess).toBeFalse();
    expect(component.errorMessage).toContain('Service unavailable');
  });
});

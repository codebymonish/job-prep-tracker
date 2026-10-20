import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { CreateApplicationComponent } from './create-application.component';
import { ApplicationService } from '../../../core/services/application.service';
import { ApplicationDetail } from '../../../core/models/application.model';

describe('CreateApplicationComponent', () => {
  let component: CreateApplicationComponent;
  let fixture: ComponentFixture<CreateApplicationComponent>;
  let serviceSpy: jasmine.SpyObj<ApplicationService>;
  let router: Router;

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('ApplicationService', ['create']);

    await TestBed.configureTestingModule({
      imports: [CreateApplicationComponent, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: ApplicationService, useValue: serviceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.stub();
    fixture = TestBed.createComponent(CreateApplicationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('initializes form with empty company, roleTitle and today appliedDate', () => {
    expect(component.form.get('company')?.value).toBe('');
    expect(component.form.get('roleTitle')?.value).toBe('');
    expect(component.form.get('appliedDate')?.value).toBeTruthy();
    expect(component.form.valid).toBeFalse();
  });

  it('rejects submission with empty required fields and does not call service', () => {
    component.onSubmit();
    expect(component.isSubmitted).toBeTrue();
    expect(component.form.invalid).toBeTrue();
    expect(serviceSpy.create).not.toHaveBeenCalled();
  });

  it('validates against future appliedDate', () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 2);
    const tomorrowStr = tomorrow.toISOString().substring(0, 10);

    component.form.patchValue({ appliedDate: tomorrowStr });
    expect(component.form.get('appliedDate')?.hasError('futureDate')).toBeTrue();
  });

  it('calculates jdCharCount accurately', () => {
    expect(component.jdCharCount).toBe(0);
    component.form.patchValue({ jdText: 'Hello World' });
    expect(component.jdCharCount).toBe(11);
  });

  it('navigates to /applications/:id on successful creation', () => {
    const createdApp: ApplicationDetail = {
      id: 'new-app-123',
      company: 'Amazon',
      roleTitle: 'SDE II',
      jdText: 'Job description text',
      status: 'APPLIED',
      appliedDate: '2026-10-12',
      version: 0,
      createdAt: '2026-10-12T12:00:00Z'
    };

    serviceSpy.create.and.returnValue(of(createdApp));

    component.form.patchValue({
      company: 'Amazon',
      roleTitle: 'SDE II',
      appliedDate: '2026-10-12',
      jdText: 'Job description text'
    });

    component.onSubmit();

    expect(serviceSpy.create).toHaveBeenCalledWith({
      company: 'Amazon',
      roleTitle: 'SDE II',
      appliedDate: '2026-10-12',
      jdText: 'Job description text'
    });
    expect(router.navigate).toHaveBeenCalledWith(['/applications', 'new-app-123']);
  });

  it('maps server field errors back to form controls on 400 response', () => {
    const errorResponse = {
      status: 400,
      error: {
        title: 'Validation Error',
        errors: {
          company: 'must not be blank',
          roleTitle: 'length must be between 1 and 255'
        }
      }
    };

    serviceSpy.create.and.returnValue(throwError(() => errorResponse));

    component.form.patchValue({
      company: 'Test Corp',
      roleTitle: 'Test Role'
    });

    component.onSubmit();

    expect(component.form.get('company')?.errors?.['serverError']).toBe('must not be blank');
    expect(component.form.get('roleTitle')?.errors?.['serverError']).toBe('length must be between 1 and 255');
  });

  it('navigates back to /applications on cancel()', () => {
    component.cancel();
    expect(router.navigate).toHaveBeenCalledWith(['/applications']);
  });
});

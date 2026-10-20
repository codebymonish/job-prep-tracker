import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { ApplicationDetailComponent } from './application-detail.component';
import { ApplicationService } from '../../../core/services/application.service';
import { ApplicationDetail } from '../../../core/models/application.model';

describe('ApplicationDetailComponent', () => {
  let component: ApplicationDetailComponent;
  let fixture: ComponentFixture<ApplicationDetailComponent>;
  let serviceSpy: jasmine.SpyObj<ApplicationService>;
  let router: Router;

  const mockDetail: ApplicationDetail = {
    id: 'app-100',
    company: 'Stripe',
    roleTitle: 'Infrastructure Engineer',
    jdText: 'A'.repeat(600), // > 500 chars to test expand/collapse
    status: 'APPLIED',
    appliedDate: '2026-10-11',
    version: 0,
    createdAt: '2026-10-11T12:00:00Z'
  };

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('ApplicationService', ['getById', 'updateStatus', 'delete']);

    await TestBed.configureTestingModule({
      imports: [ApplicationDetailComponent, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: ApplicationService, useValue: serviceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'id' ? 'app-100' : null
              }
            }
          }
        }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.stub();
    fixture = TestBed.createComponent(ApplicationDetailComponent);
    component = fixture.componentInstance;
  });

  it('loads and renders application details on init', () => {
    serviceSpy.getById.and.returnValue(of(mockDetail));
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(component.application?.company).toBe('Stripe');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Stripe');
    expect(compiled.textContent).toContain('Infrastructure Engineer');
    expect(compiled.textContent).toContain('APPLIED');
  });

  it('renders transition buttons only for allowed next statuses', () => {
    serviceSpy.getById.and.returnValue(of(mockDetail)); // APPLIED: next are INTERVIEWING, REJECTED, WITHDRAWN
    fixture.detectChanges();

    expect(component.allowedNextStatuses).toEqual(['INTERVIEWING', 'REJECTED', 'WITHDRAWN']);
    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = compiled.querySelectorAll('.btn-transition');
    expect(buttons.length).toBe(3);
  });

  it('renders terminal notice and no transition buttons when status is OFFER', () => {
    const offerApp: ApplicationDetail = { ...mockDetail, status: 'OFFER' };
    serviceSpy.getById.and.returnValue(of(offerApp));
    fixture.detectChanges();

    expect(component.allowedNextStatuses.length).toBe(0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.btn-transition').length).toBe(0);
    expect(compiled.querySelector('.terminal-note')).toBeTruthy();
  });

  it('triggers refresh and sets stale message when updateStatus returns 409', () => {
    serviceSpy.getById.and.returnValue(of(mockDetail));
    fixture.detectChanges();

    serviceSpy.updateStatus.and.returnValue(throwError(() => ({ status: 409 })));

    component.updateStatus('INTERVIEWING');

    expect(component.staleMessage).toContain('Updated elsewhere');
    // Service getById called on init (1) and reload on 409 (2)
    expect(serviceSpy.getById).toHaveBeenCalledTimes(2);
  });

  it('requires confirmation before executing delete', () => {
    serviceSpy.getById.and.returnValue(of(mockDetail));
    serviceSpy.delete.and.returnValue(of(void 0));
    fixture.detectChanges();

    expect(component.confirmingDelete).toBeFalse();

    // First click: sets confirmingDelete = true
    const compiled = fixture.nativeElement as HTMLElement;
    const deleteBtn = compiled.querySelector('.btn-danger') as HTMLButtonElement;
    deleteBtn.click();
    fixture.detectChanges();

    expect(component.confirmingDelete).toBeTrue();
    expect(serviceSpy.delete).not.toHaveBeenCalled();

    // Second click: executes delete
    component.deleteApplication();
    expect(serviceSpy.delete).toHaveBeenCalledWith('app-100');
    expect(router.navigate).toHaveBeenCalledWith(['/applications']);
  });
});

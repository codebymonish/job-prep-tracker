import { TestBed, ComponentFixture } from '@angular/core/testing';
import { VerifyComponent } from './verify.component';
import { AuthService } from '../../../core/services/auth.service';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

describe('VerifyComponent', () => {
  let component: VerifyComponent;
  let fixture: ComponentFixture<VerifyComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['verifyEmail']);
    await TestBed.configureTestingModule({
      imports: [VerifyComponent, RouterTestingModule.withRoutes([])],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
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

    fixture = TestBed.createComponent(VerifyComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.stub();
  });

  it('cleans up URL using Angular Router replaceUrl', () => {
    authServiceSpy.verifyEmail.and.returnValue(of(void 0));
    fixture.detectChanges(); // calls ngOnInit
    
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      replaceUrl: true,
      queryParams: {},
      preserveFragment: false
    }));
  });
});

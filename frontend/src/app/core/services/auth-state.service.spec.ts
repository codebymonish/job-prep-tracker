import { TestBed } from '@angular/core/testing';
import { AuthStateService } from './auth-state.service';

describe('AuthStateService', () => {
  let service: AuthStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthStateService);
  });

  it('should have initial state INITIALIZING', () => {
    expect(service.snapshot.status).toEqual('INITIALIZING');
    expect(service.snapshot.accessToken).toBeNull();
  });

  it('should set state to AUTHENTICATED on valid token', () => {
    // sub: '123', email: 'test@test.com', role: 'ADMIN', exp: 9999999999
    const payload = btoa(JSON.stringify({ sub: '123', email: 'test@test.com', role: 'ADMIN', exp: 9999999999 }));
    const token = `header.${payload}.signature`;

    service.setAuthenticated(token);

    expect(service.snapshot.status).toEqual('AUTHENTICATED');
    expect(service.snapshot.accessToken).toEqual(token);
    expect(service.snapshot.user?.id).toEqual('123');
    expect(service.snapshot.user?.email).toEqual('test@test.com');
    expect(service.snapshot.user?.role).toEqual('ADMIN');
    expect(service.snapshot.tokenExpiresAt).toEqual(9999999999 * 1000);
  });

  it('should set state to UNAUTHENTICATED on invalid token', () => {
    service.setAuthenticated('invalid.token');

    expect(service.snapshot.status).toEqual('UNAUTHENTICATED');
    expect(service.snapshot.accessToken).toBeNull();
    expect(service.snapshot.user).toBeNull();
  });

  it('should handle unauthenticated state', () => {
    service.setUnauthenticated();

    expect(service.snapshot.status).toEqual('UNAUTHENTICATED');
    expect(service.snapshot.accessToken).toBeNull();
    expect(service.snapshot.user).toBeNull();
  });
});

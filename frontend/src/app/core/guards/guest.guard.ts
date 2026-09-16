import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { filter, map, take } from 'rxjs/operators';
import { AuthStateService } from '../services/auth-state.service';

export const guestGuard: CanActivateFn = () => {
  const authState = inject(AuthStateService);
  const router = inject(Router);

  return authState.state$.pipe(
    filter(s => s.status !== 'INITIALIZING'),
    take(1),
    map(s => {
      if (s.status === 'UNAUTHENTICATED') {
        return true;
      }
      
      // Already authenticated, redirect away from guest routes
      return router.createUrlTree(['/applications']);
    })
  );
};

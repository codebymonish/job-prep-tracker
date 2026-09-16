import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { throwError, catchError } from 'rxjs';
import { ProblemDetail } from '../models/auth.dto';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Auth endpoint errors are handled by components — skip
      if (req.url.includes('/api/v1/auth/')) {
        return throwError(() => error);
      }

      // Handle global errors below
      handleGlobalError(error);
      return throwError(() => error);
    })
  );
};

function handleGlobalError(error: HttpErrorResponse): void {
  if (error.status === 0) {
    showToast('Network error: Unable to connect to the server.');
    return;
  }

  if (error.status >= 500) {
    const correlationId = getCorrelationId(error);
    showToast(`Server error. Please try again later. (ID: ${correlationId})`);
    return;
  }

  if (error.status === 429) {
    showToast('Too many requests. Please try again later.');
    return;
  }

  if (error.status === 403) {
    const problem = error.error as ProblemDetail;
    if (problem && problem.type && problem.type.endsWith('/account-pending-deletion')) {
      showToast('Your account is pending deletion and cannot access this resource.');
    } else {
      showToast('You do not have permission to access this resource.');
    }
    return;
  }
}

function getCorrelationId(error: HttpErrorResponse): string {
  const problem = error.error as ProblemDetail;
  return problem?.correlationId || 'unknown';
}

function showToast(message: string): void {
  // In a real app, this would use a toast service like MatSnackBar.
  // Since we're keeping dependencies minimal, we'll just log and use alert for critical MVP.
  // Wait, alert is blocking. We can use console.error for now, but a simple DOM toast is better.
  console.error('GLOBAL ERROR:', message);
  // A very simple vanilla JS toast
  const toast = document.createElement('div');
  toast.textContent = message;
  Object.assign(toast.style, {
    position: 'fixed',
    bottom: '20px',
    left: '50%',
    transform: 'translateX(-50%)',
    backgroundColor: '#f44336',
    color: 'white',
    padding: '16px',
    borderRadius: '4px',
    zIndex: '10000',
    boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
    fontFamily: 'sans-serif'
  });
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 5000);
}

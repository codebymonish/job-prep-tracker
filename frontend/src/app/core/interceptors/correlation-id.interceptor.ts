import { HttpInterceptorFn } from '@angular/common/http';

export const correlationIdInterceptor: HttpInterceptorFn = (req, next) => {
  let correlationId: string;
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    correlationId = crypto.randomUUID();
  } else {
    // Fallback for environments without crypto.randomUUID
    correlationId = '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, c =>
      (+c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> +c / 4).toString(16)
    );
  }

  const modifiedReq = req.clone({
    setHeaders: {
      'X-Correlation-ID': correlationId
    }
  });
  return next(modifiedReq);
};

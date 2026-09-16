import { Injectable } from '@angular/core';
import { Observable, finalize, shareReplay } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthRefreshCoordinatorService {
  private _refresh$: Observable<string> | null = null;

  getOrCreateRefresh(createFn: () => Observable<string>): Observable<string> {
    if (!this._refresh$) {
      this._refresh$ = createFn().pipe(
        finalize(() => { this._refresh$ = null; }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this._refresh$;
  }

  // No clear() method. Cleanup is exclusively self-contained via finalize() on the
  // source observable. An external clear() would be unsafe: if called while a refresh
  // is still in flight, the next 401 would create a second concurrent refresh,
  // breaking the single-flight guarantee. The finalize() path is the only owner.
}

import { Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-auth-test-harness',
  standalone: true,
  template: `<p data-testid="harness-ready">E2E Auth Test Harness</p>`
})
export class AuthTestHarnessComponent implements OnInit {
  private http = inject(HttpClient);

  ngOnInit(): void {
    const healthUrl = environment.apiUrl.replace('/api/v1', '') + '/actuator/health/liveness';
    forkJoin([
      this.http.get(healthUrl).pipe(catchError(() => of(null))),
      this.http.get(healthUrl).pipe(catchError(() => of(null)))
    ]).subscribe();
  }
}

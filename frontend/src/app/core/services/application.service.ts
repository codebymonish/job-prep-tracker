import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApplicationDetail,
  ApplicationStats,
  ApplicationStatus,
  ApplicationSummary,
  CreateApplicationRequest,
  PagedResponse,
  UpdateStatusRequest
} from '../models/application.model';

@Injectable({
  providedIn: 'root'
})
export class ApplicationService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/applications`;

  list(page: number = 0, size: number = 20, status?: ApplicationStatus): Observable<PagedResponse<ApplicationSummary>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PagedResponse<ApplicationSummary>>(this.apiUrl, { params });
  }

  getById(id: string): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`${this.apiUrl}/${id}`);
  }

  create(request: CreateApplicationRequest): Observable<ApplicationDetail> {
    return this.http.post<ApplicationDetail>(this.apiUrl, request);
  }

  updateStatus(id: string, request: UpdateStatusRequest): Observable<ApplicationDetail> {
    return this.http.patch<ApplicationDetail>(`${this.apiUrl}/${id}/status`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getStats(): Observable<ApplicationStats> {
    return this.http.get<ApplicationStats>(`${this.apiUrl}/stats`);
  }
}

import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Thin wrapper so feature code never hardcodes environment.apiBaseUrl. */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(`${environment.apiBaseUrl}${path}`);
  }

  /** BRIKKA V2 I5: authenticated binary GET (the case documents ZIP) — the auth interceptor still
   * attaches the bearer token, unlike a raw window.open on a presigned URL. */
  getBlob(path: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${environment.apiBaseUrl}${path}`, {
      responseType: 'blob',
      observe: 'response',
    });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(`${environment.apiBaseUrl}${path}`, body);
  }

  patch<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.patch<T>(`${environment.apiBaseUrl}${path}`, body);
  }

  put<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.put<T>(`${environment.apiBaseUrl}${path}`, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${environment.apiBaseUrl}${path}`);
  }
}

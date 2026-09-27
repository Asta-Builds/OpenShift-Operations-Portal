import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  FleetOverview,
  ClusterSummary,
  ClusterDetail,
  LicenseAudit,
  ForecastingProjection,
  SnapshotTriggerResult,
  ReportDefinition,
  AcmHubSummary,
  SimulatorStatus
} from '../models/portal.models';

@Injectable({
  providedIn: 'root'
})
export class PortalService {
  private http = inject(HttpClient);
  private baseUrl = '/api/v1';

  getFleetOverview(): Observable<FleetOverview> {
    return this.http.get<FleetOverview>(`${this.baseUrl}/fleet/overview`);
  }

  getHubs(): Observable<AcmHubSummary[]> {
    return this.http.get<AcmHubSummary[]>(`${this.baseUrl}/hubs`);
  }

  getClusters(): Observable<ClusterSummary[]> {
    return this.http.get<ClusterSummary[]>(`${this.baseUrl}/clusters`);
  }

  getClusterById(id: string): Observable<ClusterDetail> {
    return this.http.get<ClusterDetail>(`${this.baseUrl}/clusters/${id}`);
  }

  triggerCollection(): Observable<SnapshotTriggerResult> {
    return this.http.post<SnapshotTriggerResult>(`${this.baseUrl}/clusters/collect`, {});
  }

  getLicenseAudit(): Observable<LicenseAudit> {
    return this.http.get<LicenseAudit>(`${this.baseUrl}/licensing/audit`);
  }

  getForecastingProjection(horizonDays: number = 30, clusterId?: string): Observable<ForecastingProjection> {
    let url = `${this.baseUrl}/forecasting/projection?horizonDays=${horizonDays}`;
    if (clusterId) {
      url += `&clusterId=${clusterId}`;
    }
    return this.http.get<ForecastingProjection>(url);
  }

  getReports(): Observable<ReportDefinition[]> {
    return this.http.get<ReportDefinition[]>(`${this.baseUrl}/reports`);
  }

  exportReportCsvUrl(type: string): string {
    return `${this.baseUrl}/reports/export?type=${type}`;
  }

  exportReportPdfUrl(type: string): string {
    return `${this.baseUrl}/reports/export/pdf?type=${type}`;
  }

  getSavedReports(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/reports/saved`);
  }

  saveReport(title: string, reportType: string, parametersJson: string = '{}'): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/reports/saved`, {
      title,
      reportType,
      parametersJson
    });
  }

  injectSimulatorFault(fail: boolean): Observable<{ simulateFailure: boolean; message: string }> {
    return this.http.post<{ simulateFailure: boolean; message: string }>(
      `${this.baseUrl}/simulator/fault?fail=${fail}`,
      {}
    );
  }

  getSimulatorStatus(): Observable<SimulatorStatus> {
    return this.http.get<SimulatorStatus>(`${this.baseUrl}/simulator/status`);
  }

  setHubOutage(hub: string, down: boolean): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/simulator/outage`, {}, { params: { hub, down } });
  }

  seedFleet(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/simulator/seed`, {});
  }
}

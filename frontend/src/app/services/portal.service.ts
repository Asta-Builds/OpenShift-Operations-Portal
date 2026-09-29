import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CurrentUser,
  PortalAuthConfig,
  FleetOverview,
  ClusterSummary,
  ClusterDetail,
  LicenseAudit,
  ForecastingProjection,
  SnapshotTriggerResult,
  ReportSchedule,
  NewReportSchedule,
  PortalNotification,
  AcmHubSummary,
  HubConnectionTest,
  HubSettings,
  HubSyncRun,
  SimulatorStatus,
  AttributionReport,
  Environment,
  Team,
  InfrastructureTopology,
  InventoryRow,
  InventoryImportResult,
  NodeAgentStatus,
  FinOpsOverview,
  FinOpsNamespaceRecommendation,
  FinOpsPricingConfig
} from '../models/portal.models';

@Injectable({
  providedIn: 'root'
})
export class PortalService {
  private http = inject(HttpClient);
  private baseUrl = '/api/v1';

  getAuthConfig(): Observable<PortalAuthConfig> {
    return this.http.get<PortalAuthConfig>(`${this.baseUrl}/auth/config`);
  }

  getCurrentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.baseUrl}/auth/me`);
  }

  getFleetOverview(): Observable<FleetOverview> {
    return this.http.get<FleetOverview>(`${this.baseUrl}/fleet/overview`);
  }

  getHubs(): Observable<AcmHubSummary[]> {
    return this.http.get<AcmHubSummary[]>(`${this.baseUrl}/hubs`);
  }

  /** Admin only. Empty optional endpoints are sent as absent. */
  registerHub(settings: HubSettings): Observable<AcmHubSummary> {
    return this.http.post<AcmHubSummary>(`${this.baseUrl}/hubs`, this.hubBody(settings));
  }

  /** Admin only. The name cannot change; an empty optional endpoint removes it. */
  updateHub(id: string, settings: HubSettings): Observable<AcmHubSummary> {
    return this.http.patch<AcmHubSummary>(`${this.baseUrl}/hubs/${id}`, {
      apiUrl: settings.apiUrl.trim(),
      credentialsSecretRef: settings.credentialsSecretRef.trim(),
      observabilityUrl: settings.observabilityUrl.trim(),
      searchUrl: settings.searchUrl.trim()
    });
  }

  /** Admin only: also deletes the hub's clusters and their history. */
  deleteHub(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/hubs/${id}`);
  }

  /** Admin only: tests settings before they are saved. */
  testHubSettings(settings: HubSettings): Observable<HubConnectionTest> {
    return this.http.post<HubConnectionTest>(`${this.baseUrl}/hubs/test`, this.hubBody(settings));
  }

  /** Operator: tests a registered hub. */
  testHub(id: string): Observable<HubConnectionTest> {
    return this.http.post<HubConnectionTest>(`${this.baseUrl}/hubs/${id}/test`, {});
  }

  /** Operator: collects one hub now; 409 while another collection is running. */
  collectHub(id: string): Observable<HubSyncRun> {
    return this.http.post<HubSyncRun>(`${this.baseUrl}/hubs/${id}/collect`, {});
  }

  getHubSyncRuns(id: string, limit = 20): Observable<HubSyncRun[]> {
    return this.http.get<HubSyncRun[]>(`${this.baseUrl}/hubs/${id}/sync-runs`, { params: { limit } });
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

  /** Latest report of each cluster's node agent. */
  getNodeAgents(): Observable<NodeAgentStatus[]> {
    return this.http.get<NodeAgentStatus[]>(`${this.baseUrl}/node-reports`);
  }

  getForecastingProjection(horizonDays: number = 30, clusterId?: string): Observable<ForecastingProjection> {
    let url = `${this.baseUrl}/forecasting/projection?horizonDays=${horizonDays}`;
    if (clusterId) {
      url += `&clusterId=${clusterId}`;
    }
    return this.http.get<ForecastingProjection>(url);
  }

  /** Dates are inclusive ISO days (yyyy-MM-dd); the backend defaults to the last 30 days. */
  getAttribution(from?: string, to?: string, environment?: Environment | ''): Observable<AttributionReport> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (environment) params['environment'] = environment;
    return this.http.get<AttributionReport>(`${this.baseUrl}/attribution/teams`, { params });
  }

  getFinOpsOverview(from?: string, to?: string, environment?: Environment | ''): Observable<FinOpsOverview> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (environment) params['environment'] = environment;
    return this.http.get<FinOpsOverview>(`${this.baseUrl}/finops/overview`, { params });
  }

  getFinOpsRecommendations(
    from?: string,
    to?: string,
    environment?: Environment | '',
    rating?: string,
    teamName?: string,
    minWaste?: number
  ): Observable<FinOpsNamespaceRecommendation[]> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (environment) params['environment'] = environment;
    if (rating) params['rating'] = rating;
    if (teamName) params['teamName'] = teamName;
    if (minWaste !== undefined) params['minWaste'] = minWaste.toString();
    return this.http.get<FinOpsNamespaceRecommendation[]>(`${this.baseUrl}/finops/recommendations`, { params });
  }

  getFinOpsPricing(): Observable<FinOpsPricingConfig> {
    return this.http.get<FinOpsPricingConfig>(`${this.baseUrl}/finops/pricing`);
  }

  updateFinOpsPricing(config: FinOpsPricingConfig): Observable<FinOpsPricingConfig> {
    return this.http.put<FinOpsPricingConfig>(`${this.baseUrl}/finops/pricing`, config);
  }

  downloadFinOpsCsv(from?: string, to?: string, environment?: Environment | ''): Observable<HttpResponse<Blob>> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    if (environment) params['environment'] = environment;
    return this.http.get(`${this.baseUrl}/finops/export`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  getTeams(): Observable<Team[]> {
    return this.http.get<Team[]>(`${this.baseUrl}/teams`);
  }

  /** Admin only: maps an owner label value to the team; matching namespaces move right away. */
  addTeamAlias(teamId: string, alias: string): Observable<Team> {
    return this.http.post<Team>(`${this.baseUrl}/teams/${teamId}/aliases`, { alias });
  }

  removeTeamAlias(teamId: string, alias: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/teams/${teamId}/aliases/${encodeURIComponent(alias)}`);
  }

  createTeam(name: string, costCenter?: string): Observable<Team> {
    return this.http.post<Team>(`${this.baseUrl}/teams`, { name, costCenter: costCenter || null });
  }

  getInfrastructureTopology(): Observable<InfrastructureTopology> {
    return this.http.get<InfrastructureTopology>(`${this.baseUrl}/infrastructure/topology`);
  }

  getInventory(): Observable<InventoryRow[]> {
    return this.http.get<InventoryRow[]>(`${this.baseUrl}/inventory`);
  }

  /** Admin only. With replace, the file is a full export of the source and rows missing from it are removed. */
  importInventory(csv: string, source: string, replace: boolean): Observable<InventoryImportResult> {
    return this.http.post<InventoryImportResult>(`${this.baseUrl}/inventory/import`, csv, {
      params: { source, replace },
      headers: { 'Content-Type': 'text/csv' }
    });
  }

  getReports(): Observable<ReportSchedule[]> {
    return this.http.get<ReportSchedule[]>(`${this.baseUrl}/reports`);
  }

  /** Admin only; cron and recipients are validated by the API, which explains what is wrong. */
  createReportSchedule(schedule: NewReportSchedule): Observable<ReportSchedule> {
    return this.http.post<ReportSchedule>(`${this.baseUrl}/reports`, schedule);
  }

  /** Admin only; only the fields given are changed. */
  updateReportSchedule(id: string, changes: Partial<NewReportSchedule> & { enabled?: boolean }): Observable<ReportSchedule> {
    return this.http.patch<ReportSchedule>(`${this.baseUrl}/reports/${id}`, changes);
  }

  deleteReportSchedule(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/reports/${id}`);
  }

  /** Admin only: emails the report now; the outcome shows up as the schedule's last delivery. */
  runReportSchedule(id: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/reports/${id}/run`, {});
  }

  /** Operator: every report email and alert, newest first. */
  getNotifications(limit = 50): Observable<PortalNotification[]> {
    return this.http.get<PortalNotification[]>(`${this.baseUrl}/notifications`, { params: { limit } });
  }

  /** Fetched through HttpClient so the bearer token is sent; a plain link would not carry it. */
  downloadReport(type: string, format: 'csv' | 'pdf'): Observable<HttpResponse<Blob>> {
    const path = format === 'pdf' ? 'reports/export/pdf' : 'reports/export';
    return this.http.get(`${this.baseUrl}/${path}`, { params: { type }, responseType: 'blob', observe: 'response' });
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

  private hubBody(settings: HubSettings): Record<string, string | null> {
    return {
      name: settings.name.trim(),
      apiUrl: settings.apiUrl.trim(),
      credentialsSecretRef: settings.credentialsSecretRef.trim(),
      observabilityUrl: settings.observabilityUrl.trim() || null,
      searchUrl: settings.searchUrl.trim() || null
    };
  }
}

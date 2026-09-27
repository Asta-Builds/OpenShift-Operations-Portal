import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { FleetOverview, ClusterSummary } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-fleet-overview',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Fleet Overview</h1>
          <p class="page-subtitle">Real-time capacity, subscription cores, and cluster health across all ACM Hubs</p>
        </div>
        <div class="actions" *ngIf="auth.hasRole('OPERATOR')">
          <button class="btn btn-primary" (click)="triggerSnapshotCollection()" [disabled]="collecting">
            <app-icon name="refresh" [size]="16" [className]="collecting ? 'spin' : ''"></app-icon>
            {{ collecting ? 'Collecting Fleet Snapshot...' : 'Trigger Snapshot Collection' }}
          </button>
        </div>
      </div>

      <!-- Notification Banner if Snapshot Triggered -->
      <div *ngIf="lastActionMessage" class="alert-banner">
        <app-icon name="check-circle" [size]="18"></app-icon>
        <span>{{ lastActionMessage }}</span>
      </div>

      <!-- Key Fleet Metrics Cards -->
      <div class="metrics-grid">
        <div class="card metric-card">
          <div class="metric-header">
            <span class="metric-title">Managed Clusters</span>
            <div class="metric-icon-box blue">
              <app-icon name="server" [size]="20"></app-icon>
            </div>
          </div>
          <div class="metric-value">{{ overview?.totalClusters || 0 }}</div>
          <div class="metric-footer">
            <span class="highlight">{{ overview?.activeAcmHubs || 0 }}</span> ACM Hubs Connected
          </div>
        </div>

        <div class="card metric-card">
          <div class="metric-header">
            <span class="metric-title">License Cores</span>
            <div class="metric-icon-box red">
              <app-icon name="shield-check" [size]="20"></app-icon>
            </div>
          </div>
          <div class="metric-value">{{ overview?.totalLicenseCores || 0 }}</div>
          <div class="metric-footer">
            <span>Billable worker cores across fleet</span>
          </div>
        </div>

        <div class="card metric-card">
          <div class="metric-header">
            <span class="metric-title">CPU Capacity</span>
            <div class="metric-icon-box purple">
              <app-icon name="cpu" [size]="20"></app-icon>
            </div>
          </div>
          <div class="metric-value">
            {{ overview?.allocatedCpuCores || 0 }} <span class="metric-unit">/ {{ overview?.totalCpuCores || 0 }} Cores</span>
          </div>
          <div class="progress-bar-bg">
            <div class="progress-bar-fill" [style.width.%]="overview?.cpuUtilizationPercent || 0"></div>
          </div>
          <div class="metric-footer">
            <span>{{ overview?.cpuUtilizationPercent || 0 }}% Fleet Utilization</span>
          </div>
        </div>

        <div class="card metric-card">
          <div class="metric-header">
            <span class="metric-title">Memory Allocation</span>
            <div class="metric-icon-box amber">
              <app-icon name="hard-drive" [size]="20"></app-icon>
            </div>
          </div>
          <div class="metric-value">
            {{ overview?.allocatedMemoryGb || 0 }} <span class="metric-unit">/ {{ overview?.totalMemoryGb || 0 }} GB</span>
          </div>
          <div class="progress-bar-bg">
            <div class="progress-bar-fill" [style.width.%]="overview?.memoryUtilizationPercent || 0"></div>
          </div>
          <div class="metric-footer">
            <span>{{ overview?.memoryUtilizationPercent || 0 }}% Fleet Utilization</span>
          </div>
        </div>

        <div class="card metric-card">
          <div class="metric-header">
            <span class="metric-title">Storage Allocation</span>
            <div class="metric-icon-box blue">
              <app-icon name="layers" [size]="20"></app-icon>
            </div>
          </div>
          <div class="metric-value">
            {{ overview?.allocatedStorageGb || 0 }} <span class="metric-unit">/ {{ overview?.totalStorageGb || 0 }} GB</span>
          </div>
          <div class="progress-bar-bg">
            <div class="progress-bar-fill" [style.width.%]="overview?.storageUtilizationPercent || 0"></div>
          </div>
          <div class="metric-footer">
            <span>{{ overview?.storageUtilizationPercent || 0 }}% Fleet Storage Utilization</span>
          </div>
        </div>
      </div>

      <!-- Environment & Infrastructure Breakdown -->
      <div class="charts-grid">
        <div class="card">
          <h2 class="card-title">Fleet by Environment</h2>
          <div class="breakdown-list">
            <div *ngFor="let item of envBreakdown" class="breakdown-item">
              <div class="breakdown-label">
                <span class="badge" [ngClass]="getEnvBadgeClass(item.name)">{{ item.name }}</span>
                <span class="count">{{ item.count }} clusters</span>
              </div>
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" [style.width.%]="(item.count / (overview?.totalClusters || 1)) * 100"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <h2 class="card-title">Fleet by Infrastructure</h2>
          <div class="breakdown-list">
            <div *ngFor="let item of infraBreakdown" class="breakdown-item">
              <div class="breakdown-label">
                <span class="infra-name">{{ item.name }}</span>
                <span class="count">{{ item.count }} clusters</span>
              </div>
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" [style.width.%]="(item.count / (overview?.totalClusters || 1)) * 100"></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Fleet Clusters Quick Overview -->
      <div class="card section-margin">
        <div class="section-header">
          <h2 class="card-title">Cluster Status & Latest Snapshots</h2>
          <a routerLink="/clusters" class="btn btn-secondary">View All Clusters</a>
        </div>

        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Cluster Name</th>
                <th>Environment</th>
                <th>Owner Team</th>
                <th>Infrastructure</th>
                <th>Cores (Alloc / Total)</th>
                <th>Memory (Alloc / Total)</th>
                <th>License Cores</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let cluster of recentClusters">
                <td>
                  <a [routerLink]="['/clusters', cluster.id]" class="cluster-link">{{ cluster.clusterName }}</a>
                </td>
                <td>
                  <span class="badge" [ngClass]="getEnvBadgeClass(cluster.environment)">{{ cluster.environment }}</span>
                </td>
                <td>{{ cluster.ownerTeamName }}</td>
                <td>{{ cluster.infrastructureType }}</td>
                <td>{{ cluster.allocatedCores }} / {{ cluster.totalCores }}</td>
                <td>{{ cluster.allocatedMemoryGb }} / {{ cluster.totalMemoryGb }} GB</td>
                <td><strong>{{ cluster.licenseCores }}</strong></td>
                <td>
                  <span class="badge badge-ready">{{ cluster.status }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 1.5rem 2rem;
    }
    .header-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .page-title {
      font-size: 1.75rem;
      color: #111827;
      margin-bottom: 0.25rem;
    }
    .page-subtitle {
      color: #6B7280;
      font-size: 0.875rem;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .metric-card {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .metric-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .metric-title {
      font-size: 0.875rem;
      font-weight: 500;
      color: #6B7280;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .metric-icon-box {
      width: 36px;
      height: 36px;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .metric-icon-box.blue { background-color: #EFF6FF; color: #1D4ED8; }
    .metric-icon-box.red { background-color: #FEF2F2; color: #DC2626; }
    .metric-icon-box.purple { background-color: #F5F3FF; color: #7C3AED; }
    .metric-icon-box.amber { background-color: #FFFBEB; color: #D97706; }

    .metric-value {
      font-size: 1.75rem;
      font-weight: 700;
      color: #111827;
    }
    .metric-unit {
      font-size: 0.875rem;
      font-weight: normal;
      color: #6B7280;
    }
    .metric-footer {
      font-size: 0.8125rem;
      color: #6B7280;
    }
    .metric-footer .highlight {
      font-weight: 600;
      color: #111827;
    }
    .progress-bar-bg {
      width: 100%;
      height: 6px;
      background-color: #E5E7EB;
      border-radius: 9999px;
      overflow: hidden;
      margin-top: 0.25rem;
    }
    .progress-bar-fill {
      height: 100%;
      background-color: #EE0000;
      border-radius: 9999px;
      transition: width 0.3s ease;
    }

    .charts-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      margin-bottom: 1rem;
      color: #111827;
    }
    .breakdown-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .breakdown-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.875rem;
      margin-bottom: 0.25rem;
    }
    .breakdown-label .count {
      color: #6B7280;
      font-weight: 500;
    }
    .section-margin {
      margin-top: 1.5rem;
    }
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }
    .cluster-link {
      font-weight: 600;
      color: #0066CC;
      text-decoration: none;
    }
    .cluster-link:hover {
      text-decoration: underline;
    }
    .alert-banner {
      background-color: #ECFDF5;
      border: 1px solid #A7F3D0;
      color: #065F46;
      padding: 0.75rem 1rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 1.25rem;
      font-size: 0.875rem;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    .spin {
      animation: spin 1s linear infinite;
    }
  `]
})
export class FleetOverviewComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  overview: FleetOverview | null = null;
  recentClusters: ClusterSummary[] = [];
  collecting = false;
  lastActionMessage = '';

  envBreakdown: { name: string; count: number }[] = [];
  infraBreakdown: { name: string; count: number }[] = [];

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.portalService.getFleetOverview().subscribe({
      next: (res) => {
        this.overview = res;
        this.envBreakdown = Object.entries(res.clustersByEnvironment || {}).map(([name, count]) => ({ name, count }));
        this.infraBreakdown = Object.entries(res.clustersByInfrastructure || {}).map(([name, count]) => ({ name, count }));
      },
      error: (err) => console.error('Failed to load fleet overview', err)
    });

    this.portalService.getClusters().subscribe({
      next: (res) => {
        this.recentClusters = res.slice(0, 5);
      },
      error: (err) => console.error('Failed to load clusters', err)
    });
  }

  triggerSnapshotCollection(): void {
    this.collecting = true;
    this.portalService.triggerCollection().subscribe({
      next: (res) => {
        this.collecting = false;
        this.lastActionMessage = res.message;
        this.loadData();
      },
      error: (err) => {
        this.collecting = false;
        this.lastActionMessage = 'Error executing collection: ' + (err.error?.message || err.message);
      }
    });
  }

  getEnvBadgeClass(env: string): string {
    switch (env?.toUpperCase()) {
      case 'PRODUCTION': return 'badge-prod';
      case 'STAGING': return 'badge-staging';
      case 'DEVELOPMENT': return 'badge-dev';
      default: return 'badge-ready';
    }
  }
}

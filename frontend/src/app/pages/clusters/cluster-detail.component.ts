import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import { ClusterDetail } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-cluster-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  template: `
    <div class="page-container" *ngIf="cluster">
      <div class="header-nav">
        <a routerLink="/clusters" class="back-link">
          &larr; Back to Cluster Inventory
        </a>
      </div>

      <div class="header-row">
        <div>
          <div class="title-with-badge">
            <h1 class="page-title">{{ cluster.clusterName }}</h1>
            <span class="badge" [ngClass]="getEnvBadgeClass(cluster.environment)">{{ cluster.environment }}</span>
            <span class="badge badge-ready">{{ cluster.status }}</span>
          </div>
          <p class="page-subtitle">
            Connected to Hub: <code>{{ cluster.acmHubName }}</code> | Region: {{ cluster.region || 'Default' }}
          </p>
        </div>
      </div>

      <!-- Cluster Metadata Cards -->
      <div class="metrics-grid">
        <div class="card">
          <div class="meta-label">Owner Team & Cost Center</div>
          <div class="meta-value">{{ cluster.ownerTeamName }}</div>
          <div class="meta-sub">Cost Center: {{ cluster.costCenter }}</div>
        </div>

        <div class="card">
          <div class="meta-label">Infrastructure & Version</div>
          <div class="meta-value">{{ cluster.infrastructureType }}</div>
          <div class="meta-sub">OpenShift {{ cluster.openshiftVersion }}</div>
        </div>

        <div class="card">
          <div class="meta-label">License Cores (Worker)</div>
          <div class="meta-value text-red">{{ cluster.latestSnapshot?.licenseCoresCount || 0 }} Cores</div>
          <div class="meta-sub">{{ cluster.latestSnapshot?.workerNodes || 0 }} Worker Nodes</div>
        </div>

        <div class="card">
          <div class="meta-label">Allocation (CPU / Mem / Storage)</div>
          <div class="meta-value">
            {{ cluster.latestSnapshot?.allocatedCpuCores || 0 }} Cores / {{ cluster.latestSnapshot?.allocatedMemoryGb || 0 }} GB
          </div>
          <div class="meta-sub">
            Storage: {{ cluster.latestSnapshot?.allocatedStorageGb || 0 }} / {{ cluster.latestSnapshot?.totalStorageGb || 0 }} GB
          </div>
        </div>
      </div>

      <!-- Namespace Breakdown Table (Owner-Aware Reporting) -->
      <div class="card section-margin">
        <h2 class="card-title">Namespace-Level Ownership & Quota Allocation</h2>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Namespace</th>
                <th>Owner Team</th>
                <th>Cost Center</th>
                <th>CPU Request (Cores)</th>
                <th>Memory Request (GB)</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let ns of cluster.namespaces">
                <td><code>{{ ns.namespaceName }}</code></td>
                <td>{{ ns.ownerTeamName }}</td>
                <td><span class="badge badge-dev">{{ ns.costCenter }}</span></td>
                <td>{{ ns.cpuRequestCores }} Cores</td>
                <td>{{ ns.memoryRequestGb }} GB</td>
              </tr>
              <tr *ngIf="!cluster.namespaces || cluster.namespaces.length === 0">
                <td colspan="5" class="text-center py-4">No namespaces recorded for this cluster.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Node Breakdown Table (Underlying Infrastructure Correlation) -->
      <div class="card section-margin">
        <h2 class="card-title">Node Inventory & Hypervisor Correlation (spec.providerID)</h2>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Node Name</th>
                <th>Role</th>
                <th>Host Type</th>
                <th>CPU Cores</th>
                <th>Memory</th>
                <th>Hypervisor / VM Host</th>
                <th>Provider ID</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let node of cluster.nodeMetrics">
                <td><code>{{ node.nodeName }}</code></td>
                <td>
                  <span class="badge" [ngClass]="node.role === 'WORKER' ? 'badge-dev' : 'badge-staging'">
                    {{ node.role }}
                  </span>
                </td>
                <td>{{ node.hostType }}</td>
                <td>{{ node.cpuCores }}</td>
                <td>{{ node.memoryGb }} GB</td>
                <td><strong>{{ node.hypervisorHost || node.underlyingHostId || 'N/A' }}</strong></td>
                <td><code class="provider-id">{{ node.providerId || 'N/A' }}</code></td>
              </tr>
              <tr *ngIf="cluster.nodeMetrics.length === 0">
                <td colspan="7" class="text-center py-4">No node metrics recorded for this cluster.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Snapshot History Table -->
      <div class="card section-margin">
        <h2 class="card-title">Historical Snapshots</h2>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Snapshot Time</th>
                <th>Total Cores</th>
                <th>Allocated Cores</th>
                <th>Total Mem (GB)</th>
                <th>Allocated Mem (GB)</th>
                <th>License Cores</th>
                <th>Total Nodes</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let snap of cluster.recentSnapshots">
                <td>{{ snap.snapshotTimestamp }}</td>
                <td>{{ snap.totalCpuCores }}</td>
                <td>{{ snap.allocatedCpuCores }}</td>
                <td>{{ snap.totalMemoryGb }}</td>
                <td>{{ snap.allocatedMemoryGb }}</td>
                <td><strong>{{ snap.licenseCoresCount }}</strong></td>
                <td>{{ snap.totalNodes }} ({{ snap.workerNodes }} workers)</td>
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
    .header-nav {
      margin-bottom: 1rem;
    }
    .back-link {
      color: #0066CC;
      font-size: 0.875rem;
      text-decoration: none;
      font-weight: 500;
    }
    .title-with-badge {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .page-title {
      font-size: 1.75rem;
      color: #111827;
    }
    .page-subtitle {
      color: #6B7280;
      font-size: 0.875rem;
      margin-top: 0.25rem;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1.25rem;
      margin-top: 1.25rem;
    }
    .meta-label {
      font-size: 0.75rem;
      color: #6B7280;
      text-transform: uppercase;
      font-weight: 600;
      letter-spacing: 0.05em;
    }
    .meta-value {
      font-size: 1.25rem;
      font-weight: 700;
      color: #111827;
      margin-top: 0.25rem;
    }
    .meta-sub {
      font-size: 0.8125rem;
      color: #6B7280;
      margin-top: 0.25rem;
    }
    .text-red {
      color: #EE0000;
    }
    .section-margin {
      margin-top: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      margin-bottom: 1rem;
      color: #111827;
    }
    code {
      background: #F3F4F6;
      padding: 0.2rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.8125rem;
    }
    .text-center { text-align: center; }
    .py-4 { padding: 1.5rem; color: #6B7280; }
  `]
})
export class ClusterDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private portalService = inject(PortalService);

  cluster: ClusterDetail | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.portalService.getClusterById(id).subscribe({
        next: (res) => (this.cluster = res),
        error: (err) => console.error('Failed to load cluster details', err)
      });
    }
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

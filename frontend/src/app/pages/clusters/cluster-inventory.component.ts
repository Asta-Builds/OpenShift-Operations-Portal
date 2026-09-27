import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PortalService } from '../../services/portal.service';
import { ClusterSummary } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-cluster-inventory',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, IconComponent],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Cluster Inventory</h1>
          <p class="page-subtitle">Full catalog of all OpenShift clusters managed via ACM Hubs</p>
        </div>
        <div>
          <button class="btn btn-primary" (click)="loadClusters()">
            <app-icon name="refresh" [size]="16"></app-icon> Refresh
          </button>
        </div>
      </div>

      <!-- Filters Toolbar -->
      <div class="card filter-card">
        <div class="filter-group">
          <input
            type="text"
            class="input-search"
            placeholder="Search by cluster name or owner team..."
            [(ngModel)]="searchQuery"
          />
        </div>
        <div class="filter-group">
          <select class="select-filter" [(ngModel)]="selectedEnv">
            <option value="">All Environments</option>
            <option value="PRODUCTION">Production</option>
            <option value="STAGING">Staging</option>
            <option value="DEVELOPMENT">Development</option>
          </select>
        </div>
        <div class="filter-group">
          <select class="select-filter" [(ngModel)]="selectedInfra">
            <option value="">All Infrastructures</option>
            <option value="BARE_METAL">Bare Metal</option>
            <option value="VMWARE">VMware vSphere</option>
            <option value="AWS">Amazon Web Services</option>
          </select>
        </div>
      </div>

      <!-- Clusters Table -->
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Cluster Name</th>
              <th>ACM Hub</th>
              <th>Environment</th>
              <th>Owner Team</th>
              <th>Infrastructure</th>
              <th>OpenShift Ver</th>
              <th>Cores (Alloc / Total)</th>
              <th>Memory (Alloc / Total)</th>
              <th>License Cores</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let cluster of filteredClusters">
              <td>
                <a [routerLink]="['/clusters', cluster.id]" class="cluster-link">{{ cluster.clusterName }}</a>
              </td>
              <td>
                <span class="hub-text">{{ cluster.acmHubName }}</span>
              </td>
              <td>
                <span class="badge" [ngClass]="getEnvBadgeClass(cluster.environment)">{{ cluster.environment }}</span>
              </td>
              <td>{{ cluster.ownerTeamName }}</td>
              <td>
                <span class="infra-badge">{{ cluster.infrastructureType }}</span>
              </td>
              <td>{{ cluster.openshiftVersion || 'N/A' }}</td>
              <td>{{ cluster.allocatedCores }} / {{ cluster.totalCores }}</td>
              <td>{{ cluster.allocatedMemoryGb }} / {{ cluster.totalMemoryGb }} GB</td>
              <td><strong>{{ cluster.licenseCores }}</strong></td>
              <td>
                <span class="badge badge-ready">{{ cluster.status }}</span>
              </td>
              <td>
                <a [routerLink]="['/clusters', cluster.id]" class="btn btn-secondary btn-sm">Details</a>
              </td>
            </tr>
            <tr *ngIf="filteredClusters.length === 0">
              <td colspan="11" class="text-center py-4">No clusters match your current search and filter criteria.</td>
            </tr>
          </tbody>
        </table>
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
    .filter-card {
      display: flex;
      gap: 1rem;
      margin-bottom: 1.5rem;
      padding: 0.875rem 1.25rem;
      align-items: center;
    }
    .input-search {
      padding: 0.5rem 0.75rem;
      border: 1px solid #D1D5DB;
      border-radius: 0.375rem;
      font-size: 0.875rem;
      min-width: 280px;
    }
    .select-filter {
      padding: 0.5rem 0.75rem;
      border: 1px solid #D1D5DB;
      border-radius: 0.375rem;
      font-size: 0.875rem;
      background: white;
    }
    .cluster-link {
      font-weight: 600;
      color: #0066CC;
      text-decoration: none;
    }
    .cluster-link:hover {
      text-decoration: underline;
    }
    .hub-text {
      color: #4B5563;
      font-family: monospace;
      font-size: 0.8125rem;
    }
    .infra-badge {
      background: #F3F4F6;
      padding: 0.2rem 0.5rem;
      border-radius: 0.25rem;
      font-size: 0.75rem;
      font-weight: 500;
    }
    .btn-sm {
      padding: 0.25rem 0.625rem;
      font-size: 0.75rem;
    }
    .text-center {
      text-align: center;
    }
    .py-4 {
      padding-top: 1.5rem;
      padding-bottom: 1.5rem;
      color: #6B7280;
    }
  `]
})
export class ClusterInventoryComponent implements OnInit {
  private portalService = inject(PortalService);

  clusters: ClusterSummary[] = [];
  searchQuery = '';
  selectedEnv = '';
  selectedInfra = '';

  ngOnInit(): void {
    this.loadClusters();
  }

  loadClusters(): void {
    this.portalService.getClusters().subscribe({
      next: (res) => (this.clusters = res),
      error: (err) => console.error('Failed to load clusters', err)
    });
  }

  get filteredClusters(): ClusterSummary[] {
    return this.clusters.filter((c) => {
      const matchesSearch =
        !this.searchQuery ||
        c.clusterName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.ownerTeamName.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchesEnv = !this.selectedEnv || c.environment === this.selectedEnv;
      const matchesInfra = !this.selectedInfra || c.infrastructureType === this.selectedInfra;
      return matchesSearch && matchesEnv && matchesInfra;
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

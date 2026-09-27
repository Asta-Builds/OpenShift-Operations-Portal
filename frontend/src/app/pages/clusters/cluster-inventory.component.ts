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
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Cluster Inventory</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              {{ filteredClusters.length }} Clusters
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">Full catalog of all OpenShift clusters managed via ACM Hubs</p>
        </div>

        <div>
          <button
            type="button"
            (click)="loadClusters()"
            class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold px-4 py-2"
          >
            <app-icon name="refresh" [size]="15"></app-icon>
            <span>Refresh Inventory</span>
          </button>
        </div>
      </div>

      <!-- HeroUI Filters Toolbar -->
      <div class="heroui-card p-4 flex flex-col md:flex-row items-stretch md:items-center gap-3">
        <div class="flex-1 relative">
          <app-icon name="search" [size]="16" className="absolute left-3.5 top-1/2 -translate-y-1/2 text-default-400"></app-icon>
          <input
            type="text"
            class="w-full pl-10 pr-4 py-2 rounded-xl bg-content2 border border-divider text-xs text-foreground placeholder:text-default-400 outline-none focus:border-primary transition-colors"
            placeholder="Search by cluster name, hub, or owner team..."
            [(ngModel)]="searchQuery"
          />
        </div>

        <div class="flex items-center gap-3">
          <select
            [(ngModel)]="selectedEnv"
            class="px-3 py-2 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All Environments</option>
            <option value="PRODUCTION">Production</option>
            <option value="STAGING">Staging</option>
            <option value="DEVELOPMENT">Development</option>
          </select>

          <select
            [(ngModel)]="selectedInfra"
            class="px-3 py-2 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All Infrastructures</option>
            <option value="BARE_METAL">Bare Metal</option>
            <option value="VMWARE">VMware vSphere</option>
            <option value="AWS">Amazon Web Services</option>
          </select>
        </div>
      </div>

      <!-- Clusters HeroUI Data Table -->
      <div class="heroui-card p-5 overflow-hidden">
        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Cluster Name</th>
                <th class="py-3 px-3">ACM Hub</th>
                <th class="py-3 px-3">Environment</th>
                <th class="py-3 px-3">Owner Team</th>
                <th class="py-3 px-3">Infrastructure</th>
                <th class="py-3 px-3">Version</th>
                <th class="py-3 px-3">Cores (Alloc / Total)</th>
                <th class="py-3 px-3">Memory (Alloc / Total)</th>
                <th class="py-3 px-3">License Cores</th>
                <th class="py-3 px-3">Status</th>
                <th class="py-3 px-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let cluster of filteredClusters" class="hover:bg-content2/50 transition-colors">
                <td class="py-3.5 px-3">
                  <a [routerLink]="['/clusters', cluster.id]" class="font-bold text-primary hover:underline flex items-center gap-1.5">
                    <span class="w-2 h-2 rounded-full bg-success"></span>
                    {{ cluster.clusterName }}
                  </a>
                </td>
                <td class="py-3.5 px-3 text-default-500 font-medium">
                  {{ cluster.acmHubName }}
                </td>
                <td class="py-3.5 px-3">
                  <span class="heroui-badge text-[10px]" [ngClass]="getEnvBadgeClass(cluster.environment)">
                    {{ cluster.environment }}
                  </span>
                </td>
                <td class="py-3.5 px-3 text-default-600 font-medium">
                  {{ cluster.ownerTeamName }}
                </td>
                <td class="py-3.5 px-3">
                  <span class="inline-flex items-center gap-1.5 text-default-600 font-medium">
                    <app-icon [name]="getInfraIcon(cluster.infrastructureType)" [size]="13" className="text-default-400"></app-icon>
                    {{ cluster.infrastructureType }}
                  </span>
                </td>
                <td class="py-3.5 px-3 text-default-500 font-mono text-[11px]">
                  {{ cluster.openshiftVersion || 'N/A' }}
                </td>
                <td class="py-3.5 px-3">
                  <span class="font-bold text-foreground">{{ cluster.allocatedCores }}</span>
                  <span class="text-default-400"> / {{ cluster.totalCores }}</span>
                </td>
                <td class="py-3.5 px-3">
                  <span class="font-bold text-foreground">{{ cluster.allocatedMemoryGb | number:'1.0-0' }}</span>
                  <span class="text-default-400"> / {{ cluster.totalMemoryGb | number:'1.0-0' }} GB</span>
                </td>
                <td class="py-3.5 px-3">
                  <span class="px-2 py-0.5 rounded-md bg-content3 text-foreground font-mono font-bold text-xs">
                    {{ cluster.licenseCores }}
                  </span>
                </td>
                <td class="py-3.5 px-3">
                  <span class="heroui-badge bg-success/15 text-success text-[10px]">
                    {{ cluster.status }}
                  </span>
                </td>
                <td class="py-3.5 px-3 text-right">
                  <a
                    [routerLink]="['/clusters', cluster.id]"
                    class="heroui-btn bg-content2 hover:bg-content3 text-foreground text-xs font-semibold px-2.5 py-1 border border-divider"
                  >
                    Details
                  </a>
                </td>
              </tr>
              <tr *ngIf="filteredClusters.length === 0">
                <td colspan="11" class="text-center py-8 text-default-400 text-xs">
                  No clusters match your current search and filter criteria.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class ClusterInventoryComponent implements OnInit {
  private portalService = inject(PortalService);

  clusters: ClusterSummary[] = [];
  searchQuery: string = '';
  selectedEnv: string = '';
  selectedInfra: string = '';

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
      const matchQuery =
        !this.searchQuery ||
        c.clusterName?.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.ownerTeamName?.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.acmHubName?.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchEnv = !this.selectedEnv || c.environment === this.selectedEnv;
      const matchInfra = !this.selectedInfra || c.infrastructureType === this.selectedInfra;

      return matchQuery && matchEnv && matchInfra;
    });
  }

  getEnvBadgeClass(env: string): string {
    switch (env?.toUpperCase()) {
      case 'PRODUCTION': return 'bg-danger/15 text-danger border border-danger/25';
      case 'STAGING': return 'bg-warning/15 text-warning border border-warning/25';
      case 'DEVELOPMENT': return 'bg-primary/15 text-primary border border-primary/25';
      default: return 'bg-success/15 text-success border border-success/25';
    }
  }

  getInfraIcon(infra: string): string {
    switch (infra?.toUpperCase()) {
      case 'BARE_METAL': return 'server';
      case 'VMWARE': return 'database';
      case 'AWS': return 'cloud';
      default: return 'layers';
    }
  }
}

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
    <div class="space-y-6" *ngIf="cluster">
      
      <!-- Back Navigation Link -->
      <div>
        <a routerLink="/clusters" class="inline-flex items-center gap-1.5 text-xs font-semibold text-primary hover:underline">
          <app-icon name="chevron-right" [size]="14" className="rotate-180"></app-icon>
          <span>Back to Cluster Inventory</span>
        </a>
      </div>

      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-divider">
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">{{ cluster.clusterName }}</h1>
            <span class="heroui-badge text-xs" [ngClass]="getEnvBadgeClass(cluster.environment)">
              {{ cluster.environment }}
            </span>
            <span class="heroui-badge bg-success/15 text-success text-xs">
              {{ cluster.status }}
            </span>
          </div>
          <div class="flex items-center gap-3 text-xs text-default-500 mt-1">
            <span>ACM Hub: <code class="px-1.5 py-0.5 rounded bg-content2 text-foreground font-mono text-[11px]">{{ cluster.acmHubName }}</code></span>
            <span>•</span>
            <span>Region: <strong class="text-foreground">{{ cluster.region || 'Default' }}</strong></span>
            <span>•</span>
            <span>OpenShift <strong class="text-foreground">v{{ cluster.openshiftVersion }}</strong></span>
          </div>
        </div>
      </div>

      <!-- Cluster Metadata Cards (4-Column Grid) -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        
        <div class="heroui-card p-5 space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Owner Team</span>
            <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="users" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-lg font-bold text-foreground">{{ cluster.ownerTeamName }}</div>
          <div class="text-xs text-default-400">Cost Center: <strong class="text-foreground">{{ cluster.costCenter }}</strong></div>
        </div>

        <div class="heroui-card p-5 space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Infrastructure</span>
            <div class="w-8 h-8 rounded-xl bg-secondary/10 text-secondary flex items-center justify-center">
              <app-icon [name]="getInfraIcon(cluster.infrastructureType)" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-lg font-bold text-foreground">{{ cluster.infrastructureType }}</div>
          <div class="text-xs text-default-400">Platform: OpenShift {{ cluster.openshiftVersion }}</div>
        </div>

        <div class="heroui-card p-5 space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">License Cores</span>
            <div class="w-8 h-8 rounded-xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="shield-check" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-lg font-bold text-danger">{{ cluster.latestSnapshot?.licenseCoresCount || 0 }} Cores</div>
          <div class="text-xs text-default-400">{{ cluster.latestSnapshot?.workerNodes || 0 }} Worker Nodes Billable</div>
        </div>

        <div class="heroui-card p-5 space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">CPU & RAM Allocation</span>
            <div class="w-8 h-8 rounded-xl bg-warning/10 text-warning flex items-center justify-center">
              <app-icon name="cpu" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-lg font-bold text-foreground">
            {{ cluster.latestSnapshot?.allocatedCpuCores || 0 }} Cores
          </div>
          <div class="text-xs text-default-400">{{ cluster.latestSnapshot?.allocatedMemoryGb | number:'1.0-0' }} GB RAM allocated</div>
        </div>

      </div>

      <!-- Namespace Ownership Table -->
      <div class="heroui-card p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-foreground">Namespace Quotas & Team Attribution</h3>
          <p class="text-xs text-default-400 mt-0.5">
            Owner attribution resolved via <code>{{ cluster.ownerLabelKey }}</code> namespace label. Namespaces without a recognised owner are marked as Unattributed.
          </p>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Namespace</th>
                <th class="py-3 px-3">Owner Team</th>
                <th class="py-3 px-3">Owner Label</th>
                <th class="py-3 px-3">Cost Center</th>
                <th class="py-3 px-3 text-right">CPU Req.</th>
                <th class="py-3 px-3 text-right">CPU Used</th>
                <th class="py-3 px-3 text-right">Mem Req.</th>
                <th class="py-3 px-3 text-right">Mem Used</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let ns of cluster.namespaces" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3">
                  <code class="px-2 py-0.5 rounded bg-content2 text-foreground font-mono text-xs">{{ ns.namespaceName }}</code>
                </td>
                <td class="py-3 px-3">
                  <span *ngIf="ns.attributed" class="font-medium text-foreground">{{ ns.ownerTeamName }}</span>
                  <span *ngIf="!ns.attributed" class="heroui-badge bg-warning/15 text-warning text-[10px]">Unattributed</span>
                </td>
                <td class="py-3 px-3">
                  <code *ngIf="ns.ownerLabelValue" class="text-default-500 font-mono text-[11px]">{{ ns.ownerLabelValue }}</code>
                  <span *ngIf="!ns.ownerLabelValue" class="text-default-400 text-[11px]">none</span>
                </td>
                <td class="py-3 px-3">
                  <span *ngIf="ns.costCenter" class="heroui-badge bg-primary/10 text-primary text-[10px]">{{ ns.costCenter }}</span>
                  <span *ngIf="!ns.costCenter" class="text-default-400 text-[11px]">N/A</span>
                </td>
                <td class="py-3 px-3 text-right font-medium">{{ ns.cpuRequestCores | number:'1.2-2' }} Cores</td>
                <td class="py-3 px-3 text-right text-default-500">{{ ns.cpuUsageCores !== null ? (ns.cpuUsageCores | number:'1.2-2') : 'n/a' }}</td>
                <td class="py-3 px-3 text-right font-medium">{{ ns.memoryRequestGb | number:'1.2-2' }} GB</td>
                <td class="py-3 px-3 text-right text-default-500">{{ ns.memoryUsageGb !== null ? (ns.memoryUsageGb | number:'1.2-2') + ' GB' : 'n/a' }}</td>
              </tr>
              <tr *ngIf="!cluster.namespaces || cluster.namespaces.length === 0">
                <td colspan="8" class="text-center py-6 text-default-400 text-xs">No namespace data recorded for this cluster.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Node Inventory & ProviderID Hypervisor Correlation -->
      <div class="heroui-card p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-foreground">Node Topology & Hypervisor Correlation (spec.providerID)</h3>
          <p class="text-xs text-default-400 mt-0.5">
            Decodes underlying hardware sockets, ESXi/AWS host IDs, and worker vs. master subscription eligibility
          </p>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Node Name</th>
                <th class="py-3 px-3">Role</th>
                <th class="py-3 px-3">Host Type</th>
                <th class="py-3 px-3">CPU Cores</th>
                <th class="py-3 px-3">Memory</th>
                <th class="py-3 px-3">Hypervisor / Underlying Host</th>
                <th class="py-3 px-3">Provider ID</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let node of cluster.nodeMetrics" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3 font-mono font-bold text-foreground">{{ node.nodeName }}</td>
                <td class="py-3 px-3">
                  <span class="heroui-badge text-[10px]" [ngClass]="node.role === 'WORKER' ? 'bg-primary/15 text-primary' : 'bg-content3 text-default-600'">
                    {{ node.role }}
                  </span>
                </td>
                <td class="py-3 px-3 text-default-600 font-medium">{{ node.hostType }}</td>
                <td class="py-3 px-3 font-bold text-foreground">{{ node.cpuCores }} Cores</td>
                <td class="py-3 px-3 text-default-600">{{ node.memoryGb }} GB</td>
                <td class="py-3 px-3 font-semibold text-foreground">{{ node.hypervisorHost || node.underlyingHostId || 'N/A' }}</td>
                <td class="py-3 px-3">
                  <code class="text-[11px] text-default-500 font-mono">{{ node.providerId || 'N/A' }}</code>
                </td>
              </tr>
              <tr *ngIf="cluster.nodeMetrics.length === 0">
                <td colspan="7" class="text-center py-6 text-default-400 text-xs">No node metrics recorded.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Historical Snapshots Table -->
      <div class="heroui-card p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-foreground">Recent Telemetry Snapshots</h3>
          <p class="text-xs text-default-400 mt-0.5">Chronological record of capacity metrics captured from ACM Hub</p>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Timestamp</th>
                <th class="py-3 px-3">Total Cores</th>
                <th class="py-3 px-3">Allocated Cores</th>
                <th class="py-3 px-3">Total RAM</th>
                <th class="py-3 px-3">Allocated RAM</th>
                <th class="py-3 px-3">License Cores</th>
                <th class="py-3 px-3">Total Nodes</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let snap of cluster.recentSnapshots" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3 text-default-500 font-mono text-[11px]">{{ snap.snapshotTimestamp }}</td>
                <td class="py-3 px-3 font-bold text-foreground">{{ snap.totalCpuCores }}</td>
                <td class="py-3 px-3 text-default-600">{{ snap.allocatedCpuCores }}</td>
                <td class="py-3 px-3 text-default-600">{{ snap.totalMemoryGb | number:'1.0-0' }} GB</td>
                <td class="py-3 px-3 text-default-600">{{ snap.allocatedMemoryGb | number:'1.0-0' }} GB</td>
                <td class="py-3 px-3">
                  <span class="px-2 py-0.5 rounded bg-content3 text-foreground font-mono font-bold text-xs">{{ snap.licenseCoresCount }}</span>
                </td>
                <td class="py-3 px-3 text-default-500">{{ snap.totalNodes }} ({{ snap.workerNodes }} workers)</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
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
        error: (err) => console.error('Failed to load cluster detail', err)
      });
    }
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

import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { FleetOverview, ClusterSummary, ForecastingProjection, LicenseAudit } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-fleet-overview',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Hero Header Bar -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Fleet Dashboard</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              Live Telemetry
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Real-time multi-cluster health, capacity runway depletion, and subscription watermark audit
          </p>
        </div>

        <div class="flex items-center gap-2.5">
          <button
            type="button"
            (click)="triggerSnapshotCollection()"
            [disabled]="collecting"
            class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 shadow-glow-primary text-xs font-semibold px-4 py-2"
          >
            <app-icon name="refresh" [size]="15" [className]="collecting ? 'animate-spin' : ''"></app-icon>
            <span>{{ collecting ? 'Collecting Fleet Snapshot...' : 'Trigger Sync Collection' }}</span>
          </button>
        </div>
      </div>

      <!-- Action Feedback Banner -->
      <div *ngIf="lastActionMessage" class="p-3 rounded-2xl bg-success/10 border border-success/30 text-success flex items-center justify-between text-xs font-medium animate-in fade-in">
        <div class="flex items-center gap-2">
          <app-icon name="check-circle" [size]="16"></app-icon>
          <span>{{ lastActionMessage }}</span>
        </div>
        <button (click)="lastActionMessage = ''" class="text-success hover:opacity-75">✕</button>
      </div>

      <!-- Hero KPI Metrics Grid (HeroUI 4-Column Stat Cards) -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        
        <!-- Card 1: Managed Fleet -->
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Total Clusters</span>
            <div class="w-9 h-9 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="server" [size]="18"></app-icon>
            </div>
          </div>
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-3xl font-extrabold tracking-tight text-foreground">{{ overview?.totalClusters || 0 }}</span>
              <span class="text-xs text-default-500">Clusters</span>
            </div>
            <div class="flex items-center gap-1.5 mt-2">
              <span class="heroui-badge bg-success/15 text-success text-[10px]">
                <app-icon name="check-circle" [size]="11"></app-icon>
                {{ overview?.activeAcmHubs || 0 }} Hubs Active
              </span>
              <span class="text-[11px] text-default-400">100% Synced</span>
            </div>
          </div>
        </div>

        <!-- Card 2: CPU Capacity & Allocation -->
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">CPU Allocation</span>
            <div class="w-9 h-9 rounded-2xl bg-warning/10 text-warning flex items-center justify-center">
              <app-icon name="cpu" [size]="18"></app-icon>
            </div>
          </div>
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-3xl font-extrabold tracking-tight text-foreground">
                {{ overview?.allocatedCpuCores || 0 }}
              </span>
              <span class="text-xs text-default-500">/ {{ overview?.totalCpuCores || 0 }} Cores</span>
            </div>
            <!-- Progress Bar -->
            <div class="w-full h-1.5 rounded-full bg-content3 mt-3 overflow-hidden">
              <div 
                class="h-full rounded-full bg-gradient-to-r from-primary to-warning transition-all duration-500"
                [style.width.%]="overview?.cpuUtilizationPercent || 0"
              ></div>
            </div>
            <div class="flex items-center justify-between text-[11px] text-default-400 mt-1.5">
              <span class="font-medium text-foreground">{{ overview?.cpuUtilizationPercent || 0 }}% Fleet Utilized</span>
              <span>{{ (overview?.totalCpuCores || 0) - (overview?.allocatedCpuCores || 0) }} Cores Free</span>
            </div>
          </div>
        </div>

        <!-- Card 3: Memory Footprint -->
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Memory Allocation</span>
            <div class="w-9 h-9 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
              <app-icon name="hard-drive" [size]="18"></app-icon>
            </div>
          </div>
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-3xl font-extrabold tracking-tight text-foreground">
                {{ overview?.allocatedMemoryGb || 0 | number:'1.0-0' }}
              </span>
              <span class="text-xs text-default-500">/ {{ overview?.totalMemoryGb || 0 | number:'1.0-0' }} GB</span>
            </div>
            <!-- Progress Bar -->
            <div class="w-full h-1.5 rounded-full bg-content3 mt-3 overflow-hidden">
              <div 
                class="h-full rounded-full bg-gradient-to-r from-secondary to-success transition-all duration-500"
                [style.width.%]="overview?.memoryUtilizationPercent || 0"
              ></div>
            </div>
            <div class="flex items-center justify-between text-[11px] text-default-400 mt-1.5">
              <span class="font-medium text-foreground">{{ overview?.memoryUtilizationPercent || 0 }}% Memory Utilized</span>
              <span>{{ ((overview?.totalMemoryGb || 0) - (overview?.allocatedMemoryGb || 0)) | number:'1.0-0' }} GB Free</span>
            </div>
          </div>
        </div>

        <!-- Card 4: Licensing High Watermark -->
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Subscription Watermark</span>
            <div class="w-9 h-9 rounded-2xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="shield-alert" [size]="18"></app-icon>
            </div>
          </div>
          <div>
            <div class="flex items-baseline gap-2">
              <span class="text-3xl font-extrabold tracking-tight text-foreground">
                {{ overview?.totalLicenseCores || 0 }}
              </span>
              <span class="text-xs text-default-500">Billable Cores</span>
            </div>
            <div class="flex items-center gap-1.5 mt-2">
              <span class="heroui-badge bg-danger/15 text-danger text-[10px]">
                <app-icon name="alert-triangle" [size]="11"></app-icon>
                Cap: {{ licenseAudit?.licensedCapCores || 500 }} Cores
              </span>
              <span class="text-[11px] text-danger font-medium">Breach Active</span>
            </div>
          </div>
        </div>

      </div>

      <!-- Capacity Runway & Linear Projection Hero Card (Signature Ops Feature) -->
      <div class="heroui-card p-6 space-y-5">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-divider">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-2xl bg-warning/10 text-warning flex items-center justify-center">
              <app-icon name="trending-up" [size]="20"></app-icon>
            </div>
            <div>
              <h2 class="text-base font-bold text-foreground">Capacity Runway Depletion Engine</h2>
              <p class="text-xs text-default-400">Rolling regression model predicting CPU core capacity exhaustion</p>
            </div>
          </div>

          <!-- Horizon Selector Segmented Tabs -->
          <div class="flex items-center p-1 rounded-xl bg-content2 border border-divider">
            <button
              type="button"
              (click)="changeHorizon(30)"
              [ngClass]="selectedHorizon === 30 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
              class="px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
            >
              30 Days
            </button>
            <button
              type="button"
              (click)="changeHorizon(60)"
              [ngClass]="selectedHorizon === 60 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
              class="px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
            >
              60 Days
            </button>
            <button
              type="button"
              (click)="changeHorizon(90)"
              [ngClass]="selectedHorizon === 90 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
              class="px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
            >
              90 Days
            </button>
          </div>
        </div>

        <!-- Runway Alert Banner & Stat Pills -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          
          <!-- Exhaustion Countdown Box -->
          <div class="p-4 rounded-2xl bg-content2 border border-divider flex items-center gap-3.5">
            <div class="w-11 h-11 rounded-2xl bg-danger/10 text-danger flex items-center justify-center flex-shrink-0">
              <app-icon name="alert-triangle" [size]="22"></app-icon>
            </div>
            <div>
              <span class="text-[10px] uppercase font-bold text-default-400 block tracking-wider">Days to Exhaustion</span>
              <div class="flex items-baseline gap-1.5 mt-0.5">
                <span class="text-2xl font-black text-danger">{{ projection?.runwayDaysCores || 15 }}</span>
                <span class="text-xs text-default-500 font-medium">Days remaining</span>
              </div>
              <span class="text-[10px] text-default-400">Target exhaustion: {{ projection?.exhaustionDateCores || '2026-10-12' }}</span>
            </div>
          </div>

          <!-- Growth Rate Box -->
          <div class="p-4 rounded-2xl bg-content2 border border-divider flex items-center gap-3.5">
            <div class="w-11 h-11 rounded-2xl bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
              <app-icon name="trending-up" [size]="22"></app-icon>
            </div>
            <div>
              <span class="text-[10px] uppercase font-bold text-default-400 block tracking-wider">Daily Growth Rate</span>
              <div class="flex items-baseline gap-1.5 mt-0.5">
                <span class="text-2xl font-black text-foreground">+{{ projection?.dailyGrowthRateCores || 17.0 }}</span>
                <span class="text-xs text-default-500 font-medium">Cores / Day</span>
              </div>
              <span class="text-[10px] text-success font-medium">Confidence Score: {{ (projection?.coresRSquared || 0.92) * 100 | number:'1.0-0' }}%</span>
            </div>
          </div>

          <!-- Total Horizon Projection Box -->
          <div class="p-4 rounded-2xl bg-content2 border border-divider flex items-center gap-3.5">
            <div class="w-11 h-11 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center flex-shrink-0">
              <app-icon name="calendar" [size]="22"></app-icon>
            </div>
            <div>
              <span class="text-[10px] uppercase font-bold text-default-400 block tracking-wider">{{ selectedHorizon }}-Day Projected Demand</span>
              <div class="flex items-baseline gap-1.5 mt-0.5">
                <span class="text-2xl font-black text-foreground">{{ projection?.projectedCores || 1514 }}</span>
                <span class="text-xs text-default-500 font-medium">Cores required</span>
              </div>
              <span class="text-[10px] text-default-400">+{{ projection?.estimatedGrowthPercent || 50.8 }}% Growth expected</span>
            </div>
          </div>

        </div>

        <!-- Projection Timeline Sparkline / Data Points -->
        <div class="p-4 rounded-2xl bg-content2/60 border border-divider">
          <span class="text-xs font-bold text-foreground block mb-3">Projected Telemetry Milestones</span>
          <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3">
            <div *ngFor="let point of projection?.projectedPoints" class="p-3 rounded-xl bg-content1 border border-divider">
              <span class="text-[10px] text-default-400 block">{{ point.date }}</span>
              <span class="text-sm font-bold text-foreground block mt-1">{{ point.cores }} Cores</span>
              <span class="text-[10px] text-default-500">{{ point.memoryGb | number:'1.0-0' }} GB RAM</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Environment & Infrastructure Topology Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        
        <!-- Environment Distribution Card -->
        <div class="heroui-card p-5 space-y-4">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <h3 class="text-sm font-bold text-foreground">Fleet Distribution by Environment</h3>
            <span class="text-xs text-default-400">{{ overview?.totalClusters || 0 }} Clusters Total</span>
          </div>

          <div class="space-y-3">
            <div *ngFor="let item of envBreakdown" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full" [ngClass]="getEnvDotClass(item.name)"></span>
                  <span class="font-medium text-foreground">{{ item.name }}</span>
                </div>
                <span class="text-default-500 font-semibold">{{ item.count }} clusters</span>
              </div>
              <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                <div 
                  class="h-full rounded-full transition-all duration-300"
                  [ngClass]="getEnvBarClass(item.name)"
                  [style.width.%]="(item.count / (overview?.totalClusters || 1)) * 100"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Infrastructure Topology Card -->
        <div class="heroui-card p-5 space-y-4">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <h3 class="text-sm font-bold text-foreground">Fleet Infrastructure Architecture</h3>
            <span class="text-xs text-default-400">Hybrid Cloud Multi-Tier</span>
          </div>

          <div class="space-y-3">
            <div *ngFor="let item of infraBreakdown" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-2">
                  <app-icon [name]="getInfraIcon(item.name)" [size]="14" className="text-default-400"></app-icon>
                  <span class="font-medium text-foreground">{{ item.name }}</span>
                </div>
                <span class="text-default-500 font-semibold">{{ item.count }} clusters</span>
              </div>
              <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                <div 
                  class="h-full rounded-full bg-primary transition-all duration-300"
                  [style.width.%]="(item.count / (overview?.totalClusters || 1)) * 100"
                ></div>
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- HeroUI Cluster Inventory Data Table -->
      <div class="heroui-card p-6 space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Active Clusters Telemetry</h3>
            <p class="text-xs text-default-400">Overview of managed OpenShift control planes and worker workloads</p>
          </div>
          <a routerLink="/clusters" class="heroui-btn bg-content2 hover:bg-content3 text-foreground text-xs font-semibold px-3 py-1.5 border border-divider">
            <span>Explore All Clusters</span>
            <app-icon name="chevron-right" [size]="14"></app-icon>
          </a>
        </div>

        <!-- Table Container -->
        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Cluster Name</th>
                <th class="py-3 px-3">Environment</th>
                <th class="py-3 px-3">Owner Team</th>
                <th class="py-3 px-3">Infrastructure</th>
                <th class="py-3 px-3">CPU Cores (Alloc / Total)</th>
                <th class="py-3 px-3">Memory (Alloc / Total)</th>
                <th class="py-3 px-3">License Cores</th>
                <th class="py-3 px-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let cluster of recentClusters" class="hover:bg-content2/50 transition-colors">
                <td class="py-3.5 px-3">
                  <a [routerLink]="['/clusters', cluster.id]" class="font-bold text-primary hover:underline flex items-center gap-1.5">
                    <span class="w-2 h-2 rounded-full bg-success"></span>
                    {{ cluster.clusterName }}
                  </a>
                  <span class="text-[10px] text-default-400 block mt-0.5">{{ cluster.acmHubName }} • OCP {{ cluster.openshiftVersion }}</span>
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
                <td class="py-3.5 px-3 text-right">
                  <span class="heroui-badge bg-success/15 text-success text-[10px]">
                    {{ cluster.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class FleetOverviewComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  overview: FleetOverview | null = null;
  recentClusters: ClusterSummary[] = [];
  projection: ForecastingProjection | null = null;
  licenseAudit: LicenseAudit | null = null;
  
  collecting = false;
  lastActionMessage = '';
  selectedHorizon = 30;

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

    this.loadForecasting(this.selectedHorizon);

    this.portalService.getLicenseAudit().subscribe({
      next: (res) => (this.licenseAudit = res),
      error: (err) => console.error('Failed to load license audit', err)
    });
  }

  loadForecasting(days: number): void {
    this.portalService.getForecastingProjection(days).subscribe({
      next: (res) => (this.projection = res),
      error: (err) => console.error('Failed to load projection', err)
    });
  }

  changeHorizon(days: number): void {
    this.selectedHorizon = days;
    this.loadForecasting(days);
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
      case 'PRODUCTION': return 'bg-danger/15 text-danger border border-danger/25';
      case 'STAGING': return 'bg-warning/15 text-warning border border-warning/25';
      case 'DEVELOPMENT': return 'bg-primary/15 text-primary border border-primary/25';
      default: return 'bg-success/15 text-success border border-success/25';
    }
  }

  getEnvDotClass(env: string): string {
    switch (env?.toUpperCase()) {
      case 'PRODUCTION': return 'bg-danger';
      case 'STAGING': return 'bg-warning';
      case 'DEVELOPMENT': return 'bg-primary';
      default: return 'bg-success';
    }
  }

  getEnvBarClass(env: string): string {
    switch (env?.toUpperCase()) {
      case 'PRODUCTION': return 'bg-danger';
      case 'STAGING': return 'bg-warning';
      case 'DEVELOPMENT': return 'bg-primary';
      default: return 'bg-success';
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

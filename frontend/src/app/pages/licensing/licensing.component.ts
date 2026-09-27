import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { LicenseAudit } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-licensing',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">License & Subscription Audit</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-danger/10 text-danger border border-danger/20">
              Core Watermark Audit
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Red Hat OpenShift core counting compliance, physical sockets, and vCPU attribution
          </p>
        </div>
      </div>

      <!-- Compliance Info Banner -->
      <div class="p-4 rounded-2xl bg-content2 border border-divider flex items-start gap-3.5">
        <div class="w-9 h-9 rounded-xl bg-primary/10 text-primary flex items-center justify-center flex-shrink-0 mt-0.5">
          <app-icon name="shield-check" [size]="18"></app-icon>
        </div>
        <div class="text-xs text-default-500 leading-relaxed">
          <strong class="text-foreground">Red Hat OpenShift Licensing Rule:</strong> Only <em class="text-foreground font-semibold">Worker nodes</em> count toward subscription licensing.
          Control plane (Master) nodes and dedicated Infrastructure nodes are exempt. Bare-metal nodes are counted by physical sockets/cores (16-core socket factor), and virtualized nodes by assigned vCPUs.
        </div>
      </div>

      <!-- Core Metrics Grid (4-Column HeroUI Cards) -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" *ngIf="audit">
        
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Active Billable Cores</span>
            <div class="w-8 h-8 rounded-xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="cpu" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-3xl font-extrabold text-danger">{{ audit.totalLicenseCores }}</div>
          <div class="text-xs text-default-400">Current active worker cores across fleet</div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Peak High-Watermark</span>
            <div class="w-8 h-8 rounded-xl bg-warning/10 text-warning flex items-center justify-center">
              <app-icon name="trending-up" [size]="16"></app-icon>
            </div>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="text-3xl font-extrabold text-foreground">{{ audit.highWatermarkCores }}</span>
            <span class="text-xs text-default-400">Peak Cores</span>
          </div>
          <div class="text-xs text-default-400">Contracted Cap: <strong class="text-foreground">{{ audit.licensedCapCores }} Cores</strong></div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Compliance Status</span>
            <div class="w-8 h-8 rounded-xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="shield-alert" [size]="16"></app-icon>
            </div>
          </div>
          <div>
            <span class="heroui-badge text-xs" [ngClass]="audit.complianceBreach ? 'bg-danger/15 text-danger border border-danger/30' : 'bg-success/15 text-success'">
              {{ audit.complianceBreach ? 'CAP EXCEEDED' : 'COMPLIANT' }}
            </span>
          </div>
          <div class="text-xs text-default-400">
            {{ audit.complianceBreach ? 'Audit notice sent to administrator' : 'Within contractual allowance' }}
          </div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Hardware Allocation</span>
            <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="server" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-xl font-bold text-foreground">
            {{ audit.bareMetalCores }} <span class="text-xs text-default-400 font-normal">BM</span> / {{ audit.virtualCores }} <span class="text-xs text-default-400 font-normal">VM</span>
          </div>
          <div class="text-xs text-default-400">Physical Sockets vs. Virtualized vCPUs</div>
        </div>

      </div>

      <!-- Attribution Breakdowns (2 Columns) -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6" *ngIf="audit">
        
        <!-- Team Attribution Breakdown -->
        <div class="heroui-card p-6 space-y-4">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <h3 class="text-base font-bold text-foreground">License Cores by Owner Team</h3>
            <span class="text-xs text-default-400">Chargeback Allocation</span>
          </div>

          <div class="space-y-4">
            <div *ngFor="let item of teamBreakdown" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <span class="font-medium text-foreground">{{ item.name }}</span>
                <span class="font-bold text-foreground">{{ item.cores }} Cores</span>
              </div>
              <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                <div 
                  class="h-full rounded-full bg-primary transition-all duration-300"
                  [style.width.%]="(item.cores / (audit.totalLicenseCores || 1)) * 100"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Infrastructure & Environment Split -->
        <div class="heroui-card p-6 space-y-4">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <h3 class="text-base font-bold text-foreground">Infrastructure Tier Breakdown</h3>
            <span class="text-xs text-default-400">Physical vs Cloud Distribution</span>
          </div>

          <div class="space-y-4">
            <div *ngFor="let item of infraBreakdown" class="space-y-1.5">
              <div class="flex items-center justify-between text-xs">
                <span class="font-medium text-foreground">{{ item.name }}</span>
                <span class="font-bold text-foreground">{{ item.cores }} Cores</span>
              </div>
              <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                <div 
                  class="h-full rounded-full bg-secondary transition-all duration-300"
                  [style.width.%]="(item.cores / (audit.totalLicenseCores || 1)) * 100"
                ></div>
              </div>
            </div>
          </div>
        </div>

      </div>

    </div>
  `
})
export class LicensingComponent implements OnInit {
  private portalService = inject(PortalService);

  audit: LicenseAudit | null = null;
  teamBreakdown: { name: string; cores: number }[] = [];
  infraBreakdown: { name: string; cores: number }[] = [];

  ngOnInit(): void {
    this.portalService.getLicenseAudit().subscribe({
      next: (res) => {
        this.audit = res;
        this.teamBreakdown = Object.entries(res.coresByOwnerTeam || {}).map(([name, cores]) => ({ name, cores }));
        this.infraBreakdown = Object.entries(res.coresByInfrastructure || {}).map(([name, cores]) => ({ name, cores }));
      },
      error: (err) => console.error('Failed to load license audit', err)
    });
  }
}

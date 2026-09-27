import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { AttributionReport, AttributionRow, Environment, Team } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { saveDownload } from '../../shared/download';

function isoDay(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoDay(date);
}

@Component({
  selector: 'app-attribution',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Namespace Cost Attribution</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              Chargeback Engine
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1" *ngIf="report">
            Resource consumption attribution resolved via <code>{{ report.ownerLabelKey }}</code> and <code>{{ report.costCenterLabelKey }}</code> labels
          </p>
        </div>

        <button
          type="button"
          *ngIf="auth.hasRole('OPERATOR')"
          (click)="exportCsv()"
          class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold px-4 py-2"
        >
          <app-icon name="download" [size]="15"></app-icon>
          <span>Export 30-Day CSV</span>
        </button>
      </div>

      <!-- HeroUI Filters Toolbar -->
      <div class="heroui-card p-4 flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
        <!-- Quick Range Buttons -->
        <div class="flex items-center gap-1.5 p-1 rounded-xl bg-content2 border border-divider">
          <button
            *ngFor="let days of quickRanges"
            (click)="useRange(days)"
            [ngClass]="activeRange === days ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
            class="px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
          >
            Last {{ days }}d
          </button>
        </div>

        <!-- Date Range Pickers & Environment -->
        <div class="flex flex-wrap items-center gap-3">
          <div class="flex items-center gap-2 text-xs text-default-400">
            <span>From:</span>
            <input
              type="date"
              [(ngModel)]="from"
              [max]="to"
              (change)="activeRange = null; load()"
              class="px-2.5 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
            />
          </div>

          <div class="flex items-center gap-2 text-xs text-default-400">
            <span>To:</span>
            <input
              type="date"
              [(ngModel)]="to"
              [min]="from"
              (change)="activeRange = null; load()"
              class="px-2.5 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
            />
          </div>

          <select
            [(ngModel)]="environment"
            (change)="load()"
            class="px-3 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All Environments</option>
            <option *ngFor="let env of environments" [value]="env">{{ env }}</option>
          </select>
        </div>
      </div>

      <div *ngIf="error" class="p-3 rounded-2xl bg-danger/10 border border-danger/30 text-danger text-xs font-medium">
        {{ error }}
      </div>

      <!-- Cost Attribution Data Table -->
      <div class="heroui-card p-6 space-y-4" *ngIf="report">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Resource Attribution by Team</h3>
            <p class="text-xs text-default-400 mt-0.5">{{ report.teams.length }} teams identified across fleet</p>
          </div>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Team Name</th>
                <th class="py-3 px-3">Cost Center</th>
                <th class="py-3 px-3 text-right">Namespaces</th>
                <th class="py-3 px-3 text-right">CPU Req. (Cores)</th>
                <th class="py-3 px-3 text-right">CPU Used (Cores)</th>
                <th class="py-3 px-3 text-right">Mem Req. (GB)</th>
                <th class="py-3 px-3 text-right">Mem Used (GB)</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let row of report.teams" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3 font-semibold text-foreground">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full" [ngClass]="row.teamName === 'Unattributed' ? 'bg-warning' : 'bg-primary'"></span>
                    {{ row.teamName }}
                  </div>
                </td>
                <td class="py-3 px-3">
                  <span class="heroui-badge bg-content2 text-default-600 text-[10px]">{{ row.costCenter || 'N/A' }}</span>
                </td>
                <td class="py-3 px-3 text-right text-default-600">{{ row.namespaceCount }}</td>
                <td class="py-3 px-3 text-right font-bold text-foreground">{{ row.cpuRequestCores | number:'1.2-2' }}</td>
                <td class="py-3 px-3 text-right text-default-500">{{ row.cpuUsageCores !== null ? (row.cpuUsageCores | number:'1.2-2') : 'n/a' }}</td>
                <td class="py-3 px-3 text-right font-bold text-foreground">{{ row.memoryRequestGb | number:'1.2-2' }}</td>
                <td class="py-3 px-3 text-right text-default-500">{{ row.memoryUsageGb !== null ? (row.memoryUsageGb | number:'1.2-2') : 'n/a' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class AttributionComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  readonly quickRanges = [7, 30, 90];
  activeRange: number | null = 30;
  from = daysAgo(30);
  to = isoDay(new Date());
  environment: Environment | '' = '';
  environments: Environment[] = ['PRODUCTION', 'STAGING', 'DEVELOPMENT'];

  report: AttributionReport | null = null;
  error: string | null = null;

  ngOnInit(): void {
    this.load();
  }

  useRange(days: number): void {
    this.activeRange = days;
    this.from = daysAgo(days);
    this.to = isoDay(new Date());
    this.load();
  }

  load(): void {
    this.error = null;
    this.portalService.getAttribution(this.from, this.to, this.environment || undefined).subscribe({
      next: (res: AttributionReport) => (this.report = res),
      error: (err: any) => {
        this.error = 'Failed to load attribution report: ' + (err.error?.message || err.message);
      }
    });
  }

  exportCsv(): void {
    this.portalService.downloadReport('COST_ATTRIBUTION', 'csv').subscribe({
      next: (res: HttpResponse<Blob>) => saveDownload(res, 'cost-attribution.csv'),
      error: (err: any) => {
        this.error = 'Export failed: ' + (err.error?.message || err.message);
      }
    });
  }
}

import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { ReportDefinition } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { saveDownload } from '../../shared/download';

interface ExportCard {
  title: string;
  type: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-report-generator',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Report Generator & Scheduled Dispatch</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              CSV & PDF Engines
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Export compliance spreadsheets, licensing audits, and owner cost attribution
          </p>
        </div>
      </div>

      <div *ngIf="exportError" class="p-3 rounded-2xl bg-danger/10 border border-danger/30 text-danger text-xs font-medium">
        {{ exportError }}
      </div>

      <!-- Quick Export HeroUI Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div *ngFor="let card of exportCards" class="heroui-card p-6 flex flex-col justify-between space-y-4">
          <div class="space-y-3">
            <div class="w-10 h-10 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon [name]="card.icon" [size]="20"></app-icon>
            </div>
            <div>
              <h3 class="text-base font-bold text-foreground">{{ card.title }}</h3>
              <p class="text-xs text-default-400 mt-1 leading-relaxed">{{ card.description }}</p>
            </div>
          </div>

          <div class="flex items-center gap-2 pt-2 border-t border-divider" *ngIf="auth.hasRole('OPERATOR'); else exportsNeedOperator">
            <button
              type="button"
              (click)="download(card.type, 'csv')"
              class="flex-1 heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 shadow-glow-primary text-xs font-semibold py-2"
            >
              <app-icon name="download" [size]="14"></app-icon>
              <span>CSV</span>
            </button>
            <button
              type="button"
              (click)="download(card.type, 'pdf')"
              class="flex-1 heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold py-2"
            >
              <app-icon name="file-text" [size]="14"></app-icon>
              <span>PDF</span>
            </button>
          </div>

          <ng-template #exportsNeedOperator>
            <div class="text-[11px] text-default-400 italic pt-2 border-t border-divider">
              Exports require operator role.
            </div>
          </ng-template>
        </div>
      </div>

      <!-- Scheduled Reports Table -->
      <div class="heroui-card p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Automated Scheduled Reports</h3>
            <p class="text-xs text-default-400 mt-0.5">Recurring email and RabbitMQ dispatch schedules</p>
          </div>
          <span class="text-xs text-default-400">{{ scheduledReports.length }} Active Schedules</span>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Report Title</th>
                <th class="py-3 px-3">Type</th>
                <th class="py-3 px-3">Cron Schedule</th>
                <th class="py-3 px-3">Recipients</th>
                <th class="py-3 px-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let rep of scheduledReports" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3 font-semibold text-foreground">{{ rep.title }}</td>
                <td class="py-3 px-3">
                  <span class="heroui-badge bg-primary/10 text-primary text-[10px]">{{ rep.reportType }}</span>
                </td>
                <td class="py-3 px-3 font-mono text-[11px] text-default-500">{{ rep.cronSchedule || 'Manual' }}</td>
                <td class="py-3 px-3 text-default-600">{{ rep.recipients || 'N/A' }}</td>
                <td class="py-3 px-3 text-right">
                  <span class="heroui-badge text-[10px]" [ngClass]="rep.isEnabled ? 'bg-success/15 text-success' : 'bg-content3 text-default-500'">
                    {{ rep.isEnabled ? 'ACTIVE' : 'PAUSED' }}
                  </span>
                </td>
              </tr>
              <tr *ngIf="scheduledReports.length === 0">
                <td colspan="5" class="text-center py-6 text-default-400 text-xs">
                  No automated schedules configured yet.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class ReportGeneratorComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  exportError = '';
  scheduledReports: ReportDefinition[] = [];

  readonly exportCards: ExportCard[] = [
    {
      title: 'Fleet Capacity Snapshot',
      type: 'FLEET_CAPACITY',
      description: 'Tabulates CPU, memory, storage allocations, node counts, and versions across all clusters.',
      icon: 'server'
    },
    {
      title: 'License & Subscription Audit',
      type: 'LICENSE_AUDIT',
      description: 'Audits billable worker cores, bare-metal physical socket conversion, and watermark peaks.',
      icon: 'shield-check'
    },
    {
      title: 'Cost Attribution Report',
      type: 'COST_ATTRIBUTION',
      description: 'Breaks down namespace resource consumption, cost centers, and chargeback teams.',
      icon: 'users'
    }
  ];

  ngOnInit(): void {
    this.portalService.getReports().subscribe({
      next: (res: ReportDefinition[]) => (this.scheduledReports = res),
      error: (err: any) => console.error('Failed to load scheduled reports', err)
    });
  }

  download(type: string, format: 'csv' | 'pdf'): void {
    this.exportError = '';
    this.portalService.downloadReport(type, format).subscribe({
      next: (res: HttpResponse<Blob>) => {
        const ext = format === 'pdf' ? 'pdf' : 'csv';
        saveDownload(res, `openshift-${type.toLowerCase()}.${ext}`);
      },
      error: (err: any) => {
        this.exportError = 'Export failed: ' + (err.error?.message || err.message || 'unknown');
      }
    });
  }
}

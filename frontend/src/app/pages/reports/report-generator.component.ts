import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { NewReportSchedule, NotificationKind, NotificationStatus, PortalNotification, ReportSchedule } from '../../models/portal.models';
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
  imports: [CommonModule, FormsModule, IconComponent],
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

      <!-- Report schedules: emailed to their recipients -->
      <div class="heroui-card p-6 space-y-4" data-testid="schedules">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Scheduled Reports</h3>
            <p class="text-xs text-default-400 mt-0.5">Emailed to their recipients on a cron schedule, in the portal's time zone</p>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-xs text-default-400">{{ scheduledReports.length }} {{ scheduledReports.length === 1 ? 'schedule' : 'schedules' }}</span>
            <button *ngIf="auth.hasRole('ADMIN')" type="button" (click)="toggleForm()" data-testid="new-schedule"
                    class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 text-xs font-semibold py-1.5">
              {{ showForm ? 'Cancel' : 'New schedule' }}
            </button>
          </div>
        </div>

        <div *ngIf="message" class="p-3 rounded-xl text-xs font-medium"
             [ngClass]="messageIsError ? 'bg-danger/10 border border-danger/30 text-danger' : 'bg-success/10 border border-success/30 text-success'"
             data-testid="schedule-message">
          {{ message }}
        </div>

        <!-- New schedule (admins) -->
        <form *ngIf="showForm" (ngSubmit)="createSchedule()" class="grid grid-cols-1 md:grid-cols-2 gap-3 p-4 rounded-2xl bg-content2/60 border border-divider" data-testid="schedule-form">
          <label class="text-xs text-default-500 space-y-1">
            <span>Title</span>
            <input name="title" [(ngModel)]="form.title" required maxlength="255" placeholder="Weekly license audit" class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground outline-none focus:border-primary" />
          </label>
          <label class="text-xs text-default-500 space-y-1">
            <span>Recipients</span>
            <input name="recipients" [(ngModel)]="form.recipients" required placeholder="finops@example.com, cio@example.com" class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground outline-none focus:border-primary" />
          </label>
          <div class="grid grid-cols-2 gap-3">
            <label class="text-xs text-default-500 space-y-1">
              <span>Report</span>
              <select name="reportType" [(ngModel)]="form.reportType" class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground outline-none focus:border-primary">
                <option *ngFor="let card of exportCards" [value]="card.type">{{ card.title }}</option>
              </select>
            </label>
            <label class="text-xs text-default-500 space-y-1">
              <span>Attachment</span>
              <select name="format" [(ngModel)]="form.format" class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground outline-none focus:border-primary">
                <option value="PDF">PDF</option>
                <option value="CSV">CSV</option>
              </select>
            </label>
          </div>
          <label class="text-xs text-default-500 space-y-1">
            <span>Schedule (cron: minute hour day month weekday)</span>
            <input name="cronSchedule" [(ngModel)]="form.cronSchedule" required placeholder="0 7 * * MON" class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-primary" />
            <span class="flex flex-wrap gap-1.5 pt-1">
              <button *ngFor="let preset of cronPresets" type="button" (click)="form.cronSchedule = preset.cron"
                      class="px-2 py-0.5 rounded-lg bg-content3 hover:bg-content4 text-[10px] text-default-600">{{ preset.label }}</button>
            </span>
          </label>
          <div class="md:col-span-2 flex justify-end">
            <button type="submit" [disabled]="saving" class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 text-xs font-semibold py-2 disabled:opacity-50">
              {{ saving ? 'Saving…' : 'Create schedule' }}
            </button>
          </div>
        </form>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Report</th>
                <th class="py-3 px-3">Schedule</th>
                <th class="py-3 px-3">Recipients</th>
                <th class="py-3 px-3">Last delivery</th>
                <th class="py-3 px-3">Status</th>
                <th class="py-3 px-3 text-right" *ngIf="auth.hasRole('ADMIN')">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let rep of scheduledReports" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3">
                  <div class="font-semibold text-foreground">{{ rep.title }}</div>
                  <div class="flex gap-1 mt-1">
                    <span class="heroui-badge bg-primary/10 text-primary text-[10px]">{{ rep.reportType }}</span>
                    <span class="heroui-badge bg-content3 text-default-600 text-[10px]">{{ rep.format }}</span>
                  </div>
                </td>
                <td class="py-3 px-3">
                  <div class="font-mono text-[11px] text-default-600">{{ displayCron(rep.cronSchedule) }}</div>
                  <div class="text-[11px] text-default-400 mt-0.5">
                    {{ rep.nextRunAt ? 'Next: ' + (rep.nextRunAt | date: 'yyyy-MM-dd HH:mm') : 'Not scheduled' }}
                  </div>
                </td>
                <td class="py-3 px-3 text-default-600 max-w-[220px] break-words">{{ rep.recipients }}</td>
                <td class="py-3 px-3">
                  <ng-container *ngIf="rep.lastDelivery; else neverSent">
                    <span class="heroui-badge text-[10px]" [ngClass]="statusClass(rep.lastDelivery.status)" [title]="rep.lastDelivery.detail || ''">
                      {{ statusLabel(rep.lastDelivery.status) }}
                    </span>
                    <div class="text-[11px] text-default-400 mt-0.5">{{ rep.lastDelivery.at | date: 'yyyy-MM-dd HH:mm' }}</div>
                    <div *ngIf="rep.lastDelivery.detail" class="text-[11px] text-default-500 mt-0.5 max-w-[240px]">{{ rep.lastDelivery.detail }}</div>
                  </ng-container>
                  <ng-template #neverSent><span class="text-[11px] text-default-400">Not sent yet</span></ng-template>
                </td>
                <td class="py-3 px-3">
                  <span class="heroui-badge text-[10px]" [ngClass]="rep.enabled ? 'bg-success/15 text-success' : 'bg-content3 text-default-500'">
                    {{ rep.enabled ? 'ACTIVE' : 'PAUSED' }}
                  </span>
                </td>
                <td class="py-3 px-3 text-right whitespace-nowrap space-x-1" *ngIf="auth.hasRole('ADMIN')">
                  <button type="button" (click)="runNow(rep)" [disabled]="busyId === rep.id" data-testid="send-now"
                          class="px-2.5 py-1 rounded-lg bg-primary/10 text-primary hover:bg-primary/20 text-[11px] font-semibold disabled:opacity-50">Send now</button>
                  <button type="button" (click)="toggleEnabled(rep)" [disabled]="busyId === rep.id"
                          class="px-2.5 py-1 rounded-lg bg-content3 hover:bg-content4 text-default-600 text-[11px] font-semibold disabled:opacity-50">{{ rep.enabled ? 'Pause' : 'Resume' }}</button>
                  <button type="button" (click)="remove(rep)" [disabled]="busyId === rep.id"
                          class="px-2.5 py-1 rounded-lg bg-danger/10 text-danger hover:bg-danger/20 text-[11px] font-semibold disabled:opacity-50">Delete</button>
                </td>
              </tr>
              <tr *ngIf="scheduledReports.length === 0">
                <td colspan="6" class="text-center py-6 text-default-400 text-xs">
                  No report schedules yet.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Delivery history: every report email and alert (operators) -->
      <div class="heroui-card p-6 space-y-4" *ngIf="auth.hasRole('OPERATOR')" data-testid="deliveries">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Delivery History</h3>
            <p class="text-xs text-default-400 mt-0.5">Scheduled reports and license or capacity alerts, newest first</p>
          </div>
          <button type="button" (click)="loadNotifications()" class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs py-1.5">
            <app-icon name="refresh" [size]="13"></app-icon>
            <span>Refresh</span>
          </button>
        </div>
        <p *ngIf="!notifications.length" class="text-xs text-default-500">Nothing has been sent yet.</p>
        <div class="w-full overflow-x-auto" *ngIf="notifications.length">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-2.5 px-3">When</th>
                <th class="py-2.5 px-3">Kind</th>
                <th class="py-2.5 px-3">Subject</th>
                <th class="py-2.5 px-3">Recipients</th>
                <th class="py-2.5 px-3">Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let n of notifications">
                <td class="py-2.5 px-3 text-default-500 whitespace-nowrap">{{ n.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                <td class="py-2.5 px-3">
                  <span class="heroui-badge text-[10px]" [ngClass]="n.kind === 'SCHEDULED_REPORT' ? 'bg-primary/10 text-primary' : 'bg-warning/15 text-warning'">{{ kindLabel(n.kind) }}</span>
                </td>
                <td class="py-2.5 px-3 text-foreground">{{ n.subject }}</td>
                <td class="py-2.5 px-3 text-default-500 max-w-[220px] break-words">{{ n.recipients }}</td>
                <td class="py-2.5 px-3">
                  <span class="heroui-badge text-[10px]" [ngClass]="statusClass(n.status)">{{ statusLabel(n.status) }}</span>
                  <div *ngIf="n.detail" class="text-[11px] text-default-500 mt-0.5 max-w-[260px]">{{ n.detail }}</div>
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
  scheduledReports: ReportSchedule[] = [];
  notifications: PortalNotification[] = [];

  showForm = false;
  saving = false;
  busyId: string | null = null;
  message = '';
  messageIsError = false;
  form: NewReportSchedule = this.emptyForm();

  readonly cronPresets = [
    { label: 'Daily 07:00', cron: '0 7 * * *' },
    { label: 'Mondays 07:00', cron: '0 7 * * MON' },
    { label: '1st of the month', cron: '0 7 1 * *' }
  ];

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
    this.loadSchedules();
    if (this.auth.hasRole('OPERATOR')) {
      this.loadNotifications();
    }
  }

  loadSchedules(): void {
    this.portalService.getReports().subscribe({
      next: (res) => (this.scheduledReports = res),
      error: (err) => console.error('Failed to load report schedules', err)
    });
  }

  loadNotifications(): void {
    this.portalService.getNotifications().subscribe({
      next: (res) => (this.notifications = res),
      error: (err) => console.error('Failed to load delivery history', err)
    });
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    this.message = '';
    if (this.showForm) {
      this.form = this.emptyForm();
    }
  }

  createSchedule(): void {
    this.saving = true;
    this.portalService.createReportSchedule(this.form).subscribe({
      next: (created) => {
        this.saving = false;
        this.showForm = false;
        this.show(`Schedule "${created.title}" created.`, false);
        this.loadSchedules();
      },
      error: (err) => {
        this.saving = false;
        this.show(this.errorText(err), true);
      }
    });
  }

  runNow(schedule: ReportSchedule): void {
    this.busyId = schedule.id;
    this.portalService.runReportSchedule(schedule.id).subscribe({
      next: () => {
        this.busyId = null;
        this.show(`"${schedule.title}" was started; its last delivery shows whether it was sent.`, false);
        this.refreshAfterDelivery();
      },
      error: (err) => {
        this.busyId = null;
        this.show(this.errorText(err), true);
      }
    });
  }

  toggleEnabled(schedule: ReportSchedule): void {
    this.busyId = schedule.id;
    this.portalService.updateReportSchedule(schedule.id, { enabled: !schedule.enabled }).subscribe({
      next: () => {
        this.busyId = null;
        this.loadSchedules();
      },
      error: (err) => {
        this.busyId = null;
        this.show(this.errorText(err), true);
      }
    });
  }

  remove(schedule: ReportSchedule): void {
    if (!confirm(`Delete the schedule "${schedule.title}"? Its delivery history is kept.`)) {
      return;
    }
    this.busyId = schedule.id;
    this.portalService.deleteReportSchedule(schedule.id).subscribe({
      next: () => {
        this.busyId = null;
        this.loadSchedules();
      },
      error: (err) => {
        this.busyId = null;
        this.show(this.errorText(err), true);
      }
    });
  }

  /** Stored cron has a seconds field first; show the five fields people write. */
  displayCron(cron: string): string {
    const fields = cron.trim().split(/\s+/);
    return fields.length === 6 && fields[0] === '0' ? fields.slice(1).join(' ') : cron;
  }

  statusLabel(status: NotificationStatus): string {
    switch (status) {
      case 'SENT': return 'Sent';
      case 'NOT_SENT': return 'Not sent';
      case 'FAILED': return 'Failed';
      case 'QUEUED': return 'Queued';
    }
  }

  statusClass(status: NotificationStatus): string {
    switch (status) {
      case 'SENT': return 'bg-success/15 text-success';
      case 'NOT_SENT': return 'bg-warning/15 text-warning';
      case 'FAILED': return 'bg-danger/15 text-danger';
      case 'QUEUED': return 'bg-content3 text-default-600';
    }
  }

  kindLabel(kind: NotificationKind): string {
    switch (kind) {
      case 'SCHEDULED_REPORT': return 'Report';
      case 'LICENSE_BREACH': return 'License alert';
      case 'CAPACITY_RUNWAY': return 'Capacity alert';
    }
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

  /** Without RabbitMQ the email is sent before the call returns; with it, a moment later. */
  private refreshAfterDelivery(): void {
    this.loadSchedules();
    this.loadNotifications();
    setTimeout(() => {
      this.loadSchedules();
      this.loadNotifications();
    }, 3000);
  }

  private show(text: string, isError: boolean): void {
    this.message = text;
    this.messageIsError = isError;
  }

  private errorText(err: any): string {
    return err?.error?.message || err?.message || 'The request failed';
  }

  private emptyForm(): NewReportSchedule {
    return { title: '', reportType: 'LICENSE_AUDIT', format: 'PDF', cronSchedule: '0 7 * * MON', recipients: '' };
  }
}

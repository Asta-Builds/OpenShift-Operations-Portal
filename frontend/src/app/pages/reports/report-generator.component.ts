import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { ReportDefinition } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { saveDownload } from '../../shared/download';

@Component({
  selector: 'app-report-generator',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Report Generator & Scheduled Dispatch</h1>
          <p class="page-subtitle">Export compliance spreadsheets, licensing audits, and owner cost attribution</p>
        </div>
      </div>

      <div *ngIf="exportError" class="error-banner">{{ exportError }}</div>

      <!-- Quick Export Cards -->
      <div class="cards-grid">
        <div class="card export-card" *ngFor="let card of exportCards">
          <div class="export-icon-box">
            <app-icon [name]="card.icon" [size]="24"></app-icon>
          </div>
          <h3 class="export-title">{{ card.title }}</h3>
          <p class="export-desc">{{ card.description }}</p>
          <div class="btn-group" *ngIf="auth.hasRole('OPERATOR'); else exportsNeedOperator">
            <button class="btn btn-primary" (click)="download(card.type, 'csv')">
              <app-icon name="download" [size]="16"></app-icon> CSV
            </button>
            <button class="btn btn-secondary" (click)="download(card.type, 'pdf')">
              <app-icon name="file-text" [size]="16"></app-icon> PDF
            </button>
          </div>
        </div>
      </div>
      <ng-template #exportsNeedOperator>
        <p class="export-note">Exports need the operator role.</p>
      </ng-template>

      <!-- Scheduled Reports Table -->
      <div class="card section-margin">
        <h2 class="card-title">Automated Scheduled Reports</h2>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Report Title</th>
                <th>Type</th>
                <th>Cron Schedule</th>
                <th>Recipients</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let report of reports">
                <td><strong>{{ report.title }}</strong></td>
                <td>{{ report.reportType }}</td>
                <td><code>{{ report.cronSchedule || 'N/A' }}</code></td>
                <td>{{ report.recipients || 'None' }}</td>
                <td>
                  <span class="badge" [ngClass]="report.isEnabled ? 'badge-ready' : 'badge-staging'">
                    {{ report.isEnabled ? 'ACTIVE' : 'PAUSED' }}
                  </span>
                </td>
              </tr>
              <tr *ngIf="reports.length === 0">
                <td colspan="5" class="empty-state">No scheduled reports are configured.</td>
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
    .cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .export-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.75rem;
    }
    .export-icon-box {
      width: 44px;
      height: 44px;
      background: #FEE2E2;
      color: #DC2626;
      border-radius: 0.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .export-title {
      font-size: 1.125rem;
      color: #111827;
    }
    .export-desc {
      color: #6B7280;
      font-size: 0.875rem;
      line-height: 1.4;
      flex-grow: 1;
    }
    .btn-group {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .section-margin {
      margin-top: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      color: #111827;
      margin-bottom: 1.25rem;
    }
    code {
      background: #F3F4F6;
      padding: 0.2rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.8125rem;
    }
    .empty-state {
      text-align: center;
      color: #6B7280;
      padding: 1.5rem;
    }
    .export-note {
      color: #6B7280;
      font-size: 0.8125rem;
      font-style: italic;
    }
    .error-banner {
      background: #FEF2F2;
      border: 1px solid #FECACA;
      color: #991B1B;
      padding: 0.75rem 1rem;
      border-radius: 0.375rem;
      margin-bottom: 1.25rem;
      font-size: 0.875rem;
    }
  `]
})
export class ReportGeneratorComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  readonly exportCards = [
    {
      type: 'FLEET_CAPACITY',
      icon: 'file-text',
      title: 'Fleet Capacity & Utilization',
      description: 'Complete inventory of all managed clusters, total/allocated CPU cores, memory GB, and node counts.'
    },
    {
      type: 'LICENSE_AUDIT',
      icon: 'shield-check',
      title: 'License & Subscription Audit',
      description: 'Detailed breakdown of billable worker cores, bare-metal physical sockets vs virtual machine vCPUs.'
    },
    {
      type: 'COST_ATTRIBUTION',
      icon: 'users',
      title: 'Owner Cost Attribution',
      description: 'Granular mapping of infrastructure capacity and core consumption to business units and cost centers.'
    }
  ];

  reports: ReportDefinition[] = [];
  exportError = '';

  ngOnInit(): void {
    this.portalService.getReports().subscribe({
      next: (res) => (this.reports = res),
      error: (err) => console.error('Failed to load reports', err)
    });
  }

  download(type: string, format: 'csv' | 'pdf'): void {
    this.exportError = '';
    this.portalService.downloadReport(type, format).subscribe({
      next: (response) => saveDownload(response, `openshift-${type.toLowerCase()}.${format}`),
      error: (err) => (this.exportError = `The ${format.toUpperCase()} export failed (HTTP ${err.status}).`)
    });
  }
}

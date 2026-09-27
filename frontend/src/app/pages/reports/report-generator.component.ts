import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { ReportDefinition } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

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

      <!-- Quick Export Cards -->
      <div class="cards-grid">
        <div class="card export-card">
          <div class="export-icon-box">
            <app-icon name="file-text" [size]="24"></app-icon>
          </div>
          <h3 class="export-title">Fleet Capacity & Utilization</h3>
          <p class="export-desc">
            Complete inventory of all managed clusters, total/allocated CPU cores, memory GB, and node counts.
          </p>
          <div class="btn-group">
            <a [href]="portalService.exportReportCsvUrl('FLEET_CAPACITY')" class="btn btn-primary" download>
              <app-icon name="download" [size]="16"></app-icon> CSV
            </a>
            <a [href]="portalService.exportReportPdfUrl('FLEET_CAPACITY')" class="btn btn-secondary" target="_blank">
              <app-icon name="file-text" [size]="16"></app-icon> PDF
            </a>
          </div>
        </div>

        <div class="card export-card">
          <div class="export-icon-box">
            <app-icon name="shield-check" [size]="24"></app-icon>
          </div>
          <h3 class="export-title">License & Subscription Audit</h3>
          <p class="export-desc">
            Detailed breakdown of billable worker cores, bare-metal physical sockets vs virtual machine vCPUs.
          </p>
          <div class="btn-group">
            <a [href]="portalService.exportReportCsvUrl('LICENSE_AUDIT')" class="btn btn-primary" download>
              <app-icon name="download" [size]="16"></app-icon> CSV
            </a>
            <a [href]="portalService.exportReportPdfUrl('LICENSE_AUDIT')" class="btn btn-secondary" target="_blank">
              <app-icon name="file-text" [size]="16"></app-icon> PDF
            </a>
          </div>
        </div>

        <div class="card export-card">
          <div class="export-icon-box">
            <app-icon name="users" [size]="24"></app-icon>
          </div>
          <h3 class="export-title">Owner Cost Attribution</h3>
          <p class="export-desc">
            Granular mapping of infrastructure capacity and core consumption to business units and cost centers.
          </p>
          <div class="btn-group">
            <a [href]="portalService.exportReportCsvUrl('COST_ATTRIBUTION')" class="btn btn-primary" download>
              <app-icon name="download" [size]="16"></app-icon> CSV
            </a>
            <a [href]="portalService.exportReportPdfUrl('COST_ATTRIBUTION')" class="btn btn-secondary" target="_blank">
              <app-icon name="file-text" [size]="16"></app-icon> PDF
            </a>
          </div>
        </div>
      </div>

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
                <td>{{ report.recipients || 'operations@enterprise.internal' }}</td>
                <td>
                  <span class="badge" [ngClass]="report.isEnabled ? 'badge-ready' : 'badge-staging'">
                    {{ report.isEnabled ? 'ACTIVE' : 'PAUSED' }}
                  </span>
                </td>
              </tr>
              <tr *ngIf="reports.length === 0">
                <td><strong>Weekly Executive Fleet Capacity</strong></td>
                <td>FLEET_CAPACITY</td>
                <td><code>0 8 * * 1 (Every Monday 08:00)</code></td>
                <td>infra-executives&#64;enterprise.internal</td>
                <td><span class="badge badge-ready">ACTIVE</span></td>
              </tr>
              <tr *ngIf="reports.length === 0">
                <td><strong>Monthly Red Hat License Audit</strong></td>
                <td>LICENSE_AUDIT</td>
                <td><code>0 0 1 * * (1st of Month)</code></td>
                <td>compliance-team&#64;enterprise.internal</td>
                <td><span class="badge badge-ready">ACTIVE</span></td>
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
  `]
})
export class ReportGeneratorComponent implements OnInit {
  portalService = inject(PortalService);

  reports: ReportDefinition[] = [];

  ngOnInit(): void {
    this.portalService.getReports().subscribe({
      next: (res) => (this.reports = res),
      error: (err) => console.error('Failed to load reports', err)
    });
  }
}

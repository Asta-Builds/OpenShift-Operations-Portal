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
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">License & Subscription Audit</h1>
          <p class="page-subtitle">Red Hat OpenShift core counting compliance, physical sockets, and vCPU attribution</p>
        </div>
      </div>

      <!-- Compliance Info Banner -->
      <div class="info-card">
        <app-icon name="shield-check" [size]="20" className="info-icon"></app-icon>
        <div>
          <strong>Red Hat OpenShift Licensing Rule:</strong> Only <em>Worker nodes</em> count toward subscription licensing.
          Control plane (Master) nodes and dedicated Infrastructure nodes are exempt. Bare-metal nodes are counted by physical sockets/cores, and virtualized nodes by assigned vCPUs.
        </div>
      </div>

      <!-- Core Metrics Grid -->
      <div class="metrics-grid" *ngIf="audit">
        <div class="card">
          <div class="metric-title">Active Billable Cores</div>
          <div class="metric-value text-red">{{ audit.totalLicenseCores }}</div>
          <div class="metric-footer">Current active cores</div>
        </div>

        <div class="card">
          <div class="metric-title">Historical High-Watermark</div>
          <div class="metric-value">{{ audit.highWatermarkCores }} <span class="metric-unit">Peak Cores</span></div>
          <div class="metric-footer">Contracted Cap: <strong>{{ audit.licensedCapCores }} Cores</strong></div>
        </div>

        <div class="card">
          <div class="metric-title">Compliance Status</div>
          <div class="metric-value">
            <span class="badge" [ngClass]="audit.complianceBreach ? 'badge-prod' : 'badge-ready'">
              {{ audit.complianceBreach ? 'CAP EXCEEDED' : 'COMPLIANT' }}
            </span>
          </div>
          <div class="metric-footer">
            {{ audit.complianceBreach ? 'Audit notice sent to administrator' : 'Within contractual allowance' }}
          </div>
        </div>

        <div class="card">
          <div class="metric-title">Bare-Metal vs Virtual Cores</div>
          <div class="metric-value">{{ audit.bareMetalCores }} <span class="metric-unit">BM</span> / {{ audit.virtualCores }} <span class="metric-unit">VM</span></div>
          <div class="metric-footer">Physical vs Virtualized allocation</div>
        </div>
      </div>

      <!-- Team & Environment Attributions -->
      <div class="grids-two-col" *ngIf="audit">
        <div class="card">
          <h2 class="card-title">Cores by Owner Team (Cost Attribution)</h2>
          <div class="breakdown-list">
            <div *ngFor="let item of teamCores" class="breakdown-item">
              <div class="breakdown-label">
                <span class="font-medium">{{ item.team }}</span>
                <strong>{{ item.cores }} Cores</strong>
              </div>
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" [style.width.%]="(item.cores / (audit.totalLicenseCores || 1)) * 100"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <h2 class="card-title">Cores by Environment</h2>
          <div class="breakdown-list">
            <div *ngFor="let item of envCores" class="breakdown-item">
              <div class="breakdown-label">
                <span class="font-medium">{{ item.env }}</span>
                <strong>{{ item.cores }} Cores</strong>
              </div>
              <div class="progress-bar-bg">
                <div class="progress-bar-fill" [style.width.%]="(item.cores / (audit.totalLicenseCores || 1)) * 100"></div>
              </div>
            </div>
          </div>
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
    .info-card {
      background: #EFF6FF;
      border: 1px solid #BFDBFE;
      color: #1E40AF;
      padding: 1rem 1.25rem;
      border-radius: 0.5rem;
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
      font-size: 0.875rem;
      line-height: 1.4;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .metric-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #6B7280;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .metric-value {
      font-size: 1.75rem;
      font-weight: 700;
      color: #111827;
      margin: 0.25rem 0;
    }
    .metric-unit {
      font-size: 0.875rem;
      color: #6B7280;
      font-weight: normal;
    }
    .text-red { color: #EE0000; }
    .metric-footer {
      font-size: 0.8125rem;
      color: #6B7280;
    }
    .grids-two-col {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
    }
    .card-title {
      font-size: 1.125rem;
      color: #111827;
      margin-bottom: 1.25rem;
    }
    .breakdown-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .breakdown-label {
      display: flex;
      justify-content: space-between;
      font-size: 0.875rem;
      margin-bottom: 0.25rem;
    }
    .font-medium { font-weight: 500; }
    .progress-bar-bg {
      width: 100%;
      height: 6px;
      background-color: #E5E7EB;
      border-radius: 9999px;
      overflow: hidden;
    }
    .progress-bar-fill {
      height: 100%;
      background-color: #EE0000;
      border-radius: 9999px;
    }
  `]
})
export class LicensingComponent implements OnInit {
  private portalService = inject(PortalService);

  audit: LicenseAudit | null = null;
  teamCores: { team: string; cores: number }[] = [];
  envCores: { env: string; cores: number }[] = [];

  ngOnInit(): void {
    this.portalService.getLicenseAudit().subscribe({
      next: (res) => {
        this.audit = res;
        this.teamCores = Object.entries(res.coresByOwnerTeam || {}).map(([team, cores]) => ({ team, cores }));
        this.envCores = Object.entries(res.coresByEnvironment || {}).map(([env, cores]) => ({ env, cores }));
      },
      error: (err) => console.error('Failed to load license audit', err)
    });
  }
}

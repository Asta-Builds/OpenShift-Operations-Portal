import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { AttributionReport, AttributionRow, Environment, Team } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { saveDownload } from '../../shared/download';

/** yyyy-MM-dd in the browser's time zone. */
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
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Cost Attribution</h1>
          <p class="page-subtitle" *ngIf="report">
            Namespace requests and usage by owning team, read from the <code>{{ report.ownerLabelKey }}</code> and
            <code>{{ report.costCenterLabelKey }}</code> namespace labels
          </p>
        </div>
        <button class="btn btn-secondary" *ngIf="auth.hasRole('OPERATOR')" (click)="exportCsv()">
          <app-icon name="download" [size]="16"></app-icon>
          Export last 30 days (CSV)
        </button>
      </div>

      <!-- Filters -->
      <div class="filters card">
        <div class="quick-ranges">
          <button *ngFor="let days of quickRanges" class="range-btn" [class.active]="activeRange === days"
                  (click)="useRange(days)">Last {{ days }} days</button>
        </div>
        <label>From <input type="date" [(ngModel)]="from" [max]="to" (change)="activeRange = null; load()"></label>
        <label>To <input type="date" [(ngModel)]="to" [min]="from" (change)="activeRange = null; load()"></label>
        <label>Environment
          <select [(ngModel)]="environment" (change)="load()">
            <option value="">All environments</option>
            <option *ngFor="let env of environments" [value]="env">{{ env }}</option>
          </select>
        </label>
      </div>

      <div class="error-banner" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="report as r">
        <!-- Summary -->
        <div class="metrics-grid">
          <div class="card">
            <div class="metric-title">Attributed CPU requests</div>
            <div class="metric-value">{{ attributedCpu(r) | number: '1.0-2' }} <span class="metric-unit">cores</span></div>
            <div class="metric-footer">{{ r.teams.length }} team / cost-center rows</div>
          </div>
          <div class="card" [class.card-warn]="r.unattributed.cpuRequestCores > 0">
            <div class="metric-title">Unattributed CPU requests</div>
            <div class="metric-value">{{ r.unattributed.cpuRequestCores | number: '1.0-2' }} <span class="metric-unit">cores</span></div>
            <div class="metric-footer">{{ r.unattributed.cpuSharePercent | number: '1.0-1' }}% in {{ r.unattributed.namespaceCount }} namespaces</div>
          </div>
          <div class="card">
            <div class="metric-title">Reconciliation</div>
            <div class="metric-value">
              {{ r.cpuCoveragePercent !== null ? (r.cpuCoveragePercent | number: '1.0-1') + '%' : 'n/a' }}
            </div>
            <div class="metric-footer">
              {{ r.total.cpuRequestCores | number: '1.0-2' }} of {{ r.clusterCpuRequestCores | number: '1.0-2' }} cluster-level cores
            </div>
          </div>
          <div class="card">
            <div class="metric-title">Memory requests</div>
            <div class="metric-value">{{ r.total.memoryRequestGb | number: '1.0-0' }} <span class="metric-unit">GB</span></div>
            <div class="metric-footer">of {{ r.clusterMemoryRequestGb | number: '1.0-0' }} GB at cluster level</div>
          </div>
        </div>

        <div class="info-card" *ngIf="r.collectionsWithNamespaceData < r.collections">
          <app-icon name="alert-triangle" [size]="18"></app-icon>
          <div>
            {{ r.collectionsWithNamespaceData }} of {{ r.collections }} cluster collections in this period carried namespace
            data, and the averages below use only those. The others were taken before namespace collection started, or on
            hubs without an ACM Observability or Search endpoint.
          </div>
        </div>

        <!-- Team table -->
        <div class="card section">
          <h2 class="card-title">By team and cost center</h2>
          <p class="card-note">
            Averages over the {{ r.collectionsWithNamespaceData }} collections with namespace data from {{ r.from }} to {{ r.to }}.
            A namespace that existed for part of the period counts for that part.
          </p>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Team</th>
                  <th>Cost center</th>
                  <th class="num">Namespaces</th>
                  <th class="num">Clusters</th>
                  <th class="num">CPU req.</th>
                  <th class="num">Mem req.</th>
                  <th class="num">CPU used</th>
                  <th class="num">Mem used</th>
                  <th class="num">PVC req.</th>
                  <th class="share-col">CPU share</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of r.teams">
                  <td class="font-medium nowrap">{{ row.teamName }}</td>
                  <td><span class="badge badge-dev" *ngIf="row.costCenter">{{ row.costCenter }}</span></td>
                  <ng-container *ngTemplateOutlet="values; context: { $implicit: row }"></ng-container>
                </tr>
                <tr class="unattributed-row">
                  <td class="font-medium nowrap">
                    <app-icon name="alert-triangle" [size]="14"></app-icon>
                    Unattributed
                  </td>
                  <td></td>
                  <ng-container *ngTemplateOutlet="values; context: { $implicit: r.unattributed }"></ng-container>
                </tr>
                <tr class="total-row">
                  <td>Total</td>
                  <td></td>
                  <ng-container *ngTemplateOutlet="values; context: { $implicit: r.total }"></ng-container>
                </tr>
                <tr class="cluster-row">
                  <td colspan="4">Cluster-level requests (should match the total)</td>
                  <td class="num">{{ r.clusterCpuRequestCores | number: '1.2-2' }}</td>
                  <td class="num">{{ r.clusterMemoryRequestGb | number: '1.2-2' }} GB</td>
                  <td colspan="4"></td>
                </tr>
                <tr *ngIf="r.teams.length === 0 && r.unattributed.namespaceCount === 0">
                  <td colspan="10" class="empty">No namespace data was collected in this period.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <ng-template #values let-row>
          <td class="num">{{ row.namespaceCount }}</td>
          <td class="num">{{ row.clusterCount }}</td>
          <td class="num">{{ row.cpuRequestCores | number: '1.2-2' }}</td>
          <td class="num">{{ row.memoryRequestGb | number: '1.2-2' }} GB</td>
          <td class="num">{{ row.cpuUsageCores !== null ? (row.cpuUsageCores | number: '1.2-2') : 'n/a' }}</td>
          <td class="num">{{ row.memoryUsageGb !== null ? (row.memoryUsageGb | number: '1.2-2') + ' GB' : 'n/a' }}</td>
          <td class="num">{{ row.pvcRequestGb !== null ? (row.pvcRequestGb | number: '1.0-0') + ' GB' : 'n/a' }}</td>
          <td class="share-col">
            <div class="share">
              <div class="progress-bar-bg"><div class="progress-bar-fill" [style.width.%]="row.cpuSharePercent"></div></div>
              <span>{{ row.cpuSharePercent | number: '1.0-1' }}%</span>
            </div>
          </td>
        </ng-template>

        <!-- Why namespaces are unattributed -->
        <div class="grids-two-col section">
          <div class="card">
            <h2 class="card-title">Why namespaces are unattributed</h2>
            <ul class="reasons">
              <li><strong>{{ r.unmappedOwners.length }}</strong> owner value(s) that match no team</li>
              <li><strong>{{ r.namespacesWithoutOwnerLabel }}</strong> namespace(s) without a <code>{{ r.ownerLabelKey }}</code> label</li>
              <li><strong>{{ r.namespacesWithoutLabels }}</strong> namespace(s) whose labels have not been read (hub without ACM Search)</li>
            </ul>
            <p class="card-note">Unattributed namespaces are never charged to the cluster's owner.</p>
          </div>

          <div class="card">
            <h2 class="card-title">Owner values without a team</h2>
            <div class="empty" *ngIf="r.unmappedOwners.length === 0">Every owner value maps to a team.</div>
            <div class="unmapped" *ngFor="let owner of r.unmappedOwners">
              <div>
                <code>{{ owner.ownerLabelValue }}</code>
                <span class="muted">{{ owner.namespaceCount }} namespace(s)</span>
              </div>
              <div class="map-form" *ngIf="auth.hasRole('ADMIN')">
                <select [(ngModel)]="mapTargets[owner.ownerLabelValue]">
                  <option [ngValue]="undefined">Map to team...</option>
                  <option *ngFor="let team of teams" [ngValue]="team.id">{{ team.name }}</option>
                </select>
                <button class="btn btn-primary btn-sm" [disabled]="!mapTargets[owner.ownerLabelValue] || busy"
                        (click)="mapOwner(owner.ownerLabelValue)">Map</button>
              </div>
            </div>
          </div>
        </div>

        <!-- Teams and aliases -->
        <div class="card section">
          <h2 class="card-title">Teams and owner values</h2>
          <p class="card-note">
            A namespace belongs to a team when its owner label equals the team's name (case and punctuation ignored,
            so <code>payments-platform</code> matches "Payments Platform") or one of the team's aliases.
          </p>
          <div class="table-container">
            <table>
              <thead>
                <tr><th>Team</th><th>Cost center</th><th>Owner values</th><th class="num">Namespaces</th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let team of teams">
                  <td class="font-medium">{{ team.name }}</td>
                  <td>{{ team.costCenter || '—' }}</td>
                  <td>
                    <span class="chip chip-name">{{ slug(team.name) }}</span>
                    <span class="chip" *ngFor="let alias of team.aliases">
                      {{ alias }}
                      <button *ngIf="auth.hasRole('ADMIN')" class="chip-remove" [disabled]="busy"
                              (click)="removeAlias(team, alias)" [attr.aria-label]="'Remove alias ' + alias">×</button>
                    </span>
                  </td>
                  <td class="num">{{ team.namespaceCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    .page-container { padding: 1.5rem 2rem; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1.25rem; }
    .page-title { font-size: 1.75rem; color: #111827; margin-bottom: 0.25rem; }
    .page-subtitle { color: #6B7280; font-size: 0.875rem; }
    code { font-size: 0.8125rem; background: #F3F4F6; padding: 0.05rem 0.3rem; border-radius: 0.25rem; }
    .filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 1rem; margin-bottom: 1.25rem; }
    .filters label { display: flex; flex-direction: column; font-size: 0.75rem; font-weight: 600; color: #6B7280; gap: 0.25rem; }
    .filters input, .filters select, .map-form select {
      font: inherit; font-size: 0.875rem; padding: 0.375rem 0.5rem; border: 1px solid #D1D5DB; border-radius: 0.375rem; background: #fff; color: #111827;
    }
    .quick-ranges { display: flex; gap: 0.25rem; margin-right: auto; }
    .range-btn { font: inherit; font-size: 0.8125rem; padding: 0.375rem 0.75rem; border: 1px solid #E5E7EB; background: #fff; border-radius: 0.375rem; cursor: pointer; color: #374151; }
    .range-btn.active { background: #151515; color: #fff; border-color: #151515; }
    .error-banner { background: #FEE2E2; color: #991B1B; border: 1px solid #FECACA; padding: 0.75rem 1rem; border-radius: 0.5rem; margin-bottom: 1rem; font-size: 0.875rem; }
    .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.25rem; margin-bottom: 1.25rem; }
    .metric-title { font-size: 0.8125rem; font-weight: 600; color: #6B7280; text-transform: uppercase; letter-spacing: 0.05em; }
    .metric-value { font-size: 1.75rem; font-weight: 700; color: #111827; margin: 0.25rem 0; }
    .metric-unit { font-size: 0.875rem; color: #6B7280; font-weight: normal; }
    .metric-footer { font-size: 0.8125rem; color: #6B7280; }
    .card-warn { border-color: #FCD34D; background: #FFFBEB; }
    .info-card { background: #FFFBEB; border: 1px solid #FCD34D; color: #92400E; padding: 0.875rem 1.25rem; border-radius: 0.5rem; display: flex; gap: 0.75rem; margin-bottom: 1.25rem; font-size: 0.875rem; line-height: 1.4; }
    .section { margin-bottom: 1.25rem; }
    .card-title { font-size: 1.125rem; color: #111827; margin-bottom: 0.5rem; }
    .card-note { font-size: 0.8125rem; color: #6B7280; margin-bottom: 1rem; }
    .num { text-align: right; white-space: nowrap; }
    th { white-space: nowrap; }
    th.num { text-align: right; }
    th, td { padding-left: 0.75rem; padding-right: 0.75rem; }
    .nowrap { white-space: nowrap; }
    .share-col { width: 130px; min-width: 130px; }
    .share { display: flex; align-items: center; gap: 0.5rem; font-size: 0.8125rem; color: #374151; }
    .share .progress-bar-bg { flex: 1; }
    .progress-bar-bg { width: 100%; height: 6px; background-color: #E5E7EB; border-radius: 9999px; overflow: hidden; }
    .progress-bar-fill { height: 100%; background-color: #EE0000; border-radius: 9999px; }
    .unattributed-row td { background: #FFFBEB; color: #92400E; }
    .unattributed-row .progress-bar-fill { background-color: #F59E0B; }
    .total-row td { font-weight: 700; border-top: 2px solid #E5E7EB; }
    .cluster-row td { color: #6B7280; font-size: 0.8125rem; }
    .empty { color: #6B7280; font-size: 0.875rem; text-align: center; padding: 1rem; }
    .font-medium { font-weight: 500; }
    .grids-two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }
    .reasons { list-style: none; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.875rem; margin-bottom: 0.75rem; }
    .unmapped { display: flex; justify-content: space-between; align-items: center; gap: 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid #F3F4F6; flex-wrap: wrap; }
    .unmapped:last-child { border-bottom: none; }
    .muted { color: #6B7280; font-size: 0.8125rem; margin-left: 0.5rem; }
    .map-form { display: flex; gap: 0.5rem; }
    .btn-sm { padding: 0.375rem 0.75rem; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .chip { display: inline-flex; align-items: center; gap: 0.25rem; font-size: 0.75rem; padding: 0.125rem 0.5rem; margin: 0.125rem 0.25rem 0.125rem 0; border-radius: 9999px; background: #DBEAFE; color: #1E40AF; font-family: ui-monospace, monospace; }
    .chip-name { background: #F3F4F6; color: #374151; }
    .chip-remove { border: none; background: none; cursor: pointer; color: inherit; font-size: 0.875rem; line-height: 1; padding: 0; }
    @media (max-width: 900px) {
      .grids-two-col { grid-template-columns: 1fr; }
      .page-container { padding: 1rem; }
      .header-row { flex-direction: column; }
    }
  `]
})
export class AttributionComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  readonly quickRanges = [7, 30, 90];
  readonly environments: Environment[] = ['PRODUCTION', 'STAGING', 'QA', 'DEVELOPMENT', 'UNKNOWN'];

  report: AttributionReport | null = null;
  teams: Team[] = [];
  activeRange: number | null = 30;
  from = daysAgo(29);
  to = isoDay(new Date());
  environment: Environment | '' = '';
  mapTargets: Record<string, string | undefined> = {};
  busy = false;
  error = '';

  ngOnInit(): void {
    this.load();
  }

  useRange(days: number): void {
    this.activeRange = days;
    this.from = daysAgo(days - 1);
    this.to = isoDay(new Date());
    this.load();
  }

  load(): void {
    this.error = '';
    forkJoin({
      report: this.portalService.getAttribution(this.from, this.to, this.environment),
      teams: this.portalService.getTeams()
    }).subscribe({
      next: ({ report, teams }) => {
        this.report = report;
        this.teams = teams;
      },
      error: (err) => (this.error = `Could not load attribution (HTTP ${err.status}).`)
    });
  }

  attributedCpu(report: AttributionReport): number {
    return report.teams.reduce((sum: number, row: AttributionRow) => sum + row.cpuRequestCores, 0);
  }

  /** The owner value a team's name matches, as the backend normalizes it. */
  slug(name: string): string {
    return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  }

  mapOwner(value: string): void {
    const teamId = this.mapTargets[value];
    if (!teamId) return;
    this.busy = true;
    this.portalService.addTeamAlias(teamId, value).subscribe({
      next: () => {
        this.busy = false;
        delete this.mapTargets[value];
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err.status === 409 ? `The owner value ${value} already maps to a team.` : `Mapping failed (HTTP ${err.status}).`;
      }
    });
  }

  removeAlias(team: Team, alias: string): void {
    this.busy = true;
    this.portalService.removeTeamAlias(team.id, alias).subscribe({
      next: () => {
        this.busy = false;
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = `Removing the alias failed (HTTP ${err.status}).`;
      }
    });
  }

  exportCsv(): void {
    this.portalService.downloadReport('COST_ATTRIBUTION', 'csv').subscribe({
      next: (response) => saveDownload(response, 'openshift-cost_attribution.csv'),
      error: (err) => (this.error = `The CSV export failed (HTTP ${err.status}).`)
    });
  }
}

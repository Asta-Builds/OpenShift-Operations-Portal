import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { AcmHubSummary } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-simulator',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">ACM Hubs & Simulator Controls</h1>
          <p class="page-subtitle">Multi-Hub connectivity, Resilience4j circuit breakers, and fault injection</p>
        </div>
      </div>

      <!-- Action Feedback Banner -->
      <div *ngIf="statusMessage" class="alert-banner">
        <app-icon name="check-circle" [size]="18"></app-icon>
        <span>{{ statusMessage }}</span>
      </div>

      <!-- Control Cards -->
      <div class="controls-grid">
        <div class="card">
          <div class="card-icon-box">
            <app-icon name="refresh" [size]="20"></app-icon>
          </div>
          <h3 class="control-title">Manual Snapshot Ingestion</h3>
          <p class="control-desc">
            Executes scheduled collection worker immediately across all registered ACM Hubs.
          </p>
          <button class="btn btn-primary" (click)="triggerCollection()" [disabled]="loading">
            <app-icon name="refresh" [size]="16" [className]="loading ? 'spin' : ''"></app-icon>
            {{ loading ? 'Running...' : 'Execute Collection Now' }}
          </button>
        </div>

        <div class="card">
          <div class="card-icon-box" [ngClass]="faultActive ? 'red' : 'amber'">
            <app-icon name="zap" [size]="20"></app-icon>
          </div>
          <h3 class="control-title">Resilience4j Fault Injection</h3>
          <p class="control-desc">
            Simulate a one-off ACM Hub connection timeout on the next collection; the collector retries it.
          </p>
          <button
            class="btn"
            [ngClass]="faultActive ? 'btn-primary' : 'btn-secondary'"
            (click)="toggleFault()"
          >
            <app-icon name="alert-triangle" [size]="16"></app-icon>
            {{ faultActive ? 'Fault Active (Click to Clear)' : 'Inject Connection Timeout' }}
          </button>
        </div>

        <div class="card">
          <div class="card-icon-box blue">
            <app-icon name="server" [size]="20"></app-icon>
          </div>
          <h3 class="control-title">Seed Fleet Baseline</h3>
          <p class="control-desc">
            Creates mock hubs, clusters, namespaces and 30 days of snapshots when the database has no clusters yet.
          </p>
          <button class="btn btn-secondary" (click)="reseedFleet()">
            <app-icon name="layers" [size]="16"></app-icon> Seed If Empty
          </button>
        </div>
      </div>

      <!-- Registered ACM Hubs Overview -->
      <div class="card section-margin">
        <h2 class="card-title">Registered Red Hat ACM Hubs</h2>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Hub Name</th>
                <th>Status</th>
                <th>Last Run</th>
                <th>Failures in a Row</th>
                <th>Circuit Breaker</th>
                <th>Last Sync</th>
                <th>Simulated Outage</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let hub of hubs">
                <td>
                  <strong>{{ hub.name }}</strong>
                  <div><code>{{ hub.apiUrl }}</code></div>
                </td>
                <td>
                  <span class="badge" [ngClass]="hubStatusClass(hub.status)">{{ hub.status }}</span>
                </td>
                <td>
                  <ng-container *ngIf="hub.latestSyncRun as run; else noRun">
                    <div class="run-status" [title]="run.errorMessage ?? ''">{{ run.status }}</div>
                    <div class="run-detail">
                      {{ run.attempts }} attempt(s) · {{ run.clustersOk }}/{{ run.clustersOk + run.clustersFailed }} clusters
                    </div>
                  </ng-container>
                  <ng-template #noRun>Not collected yet</ng-template>
                </td>
                <td>{{ hub.consecutiveFailures }}</td>
                <td>
                  <span class="badge" [ngClass]="circuitClass(hub.circuitBreakerState)">{{ hub.circuitBreakerState }}</span>
                </td>
                <td>{{ (hub.lastSyncTimestamp | date:'yyyy-MM-dd HH:mm:ss') ?? 'Never' }}</td>
                <td>
                  <button class="btn" [ngClass]="isHubDown(hub.name) ? 'btn-primary' : 'btn-secondary'"
                          (click)="toggleHubOutage(hub.name)">
                    {{ isHubDown(hub.name) ? 'End Outage' : 'Start Outage' }}
                  </button>
                </td>
              </tr>
              <tr *ngIf="hubs.length === 0">
                <td colspan="7" class="empty-state">No ACM hubs are registered.</td>
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
    .controls-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .card-icon-box {
      width: 40px;
      height: 40px;
      background: #F3F4F6;
      color: #374151;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.75rem;
    }
    .card-icon-box.amber { background: #FEF3C7; color: #D97706; }
    .card-icon-box.red { background: #FEE2E2; color: #DC2626; }
    .card-icon-box.blue { background: #EFF6FF; color: #2563EB; }
    .control-title {
      font-size: 1.125rem;
      color: #111827;
      margin-bottom: 0.375rem;
    }
    .control-desc {
      color: #6B7280;
      font-size: 0.875rem;
      margin-bottom: 1rem;
      line-height: 1.4;
      min-height: 48px;
    }
    .section-margin {
      margin-top: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      color: #111827;
      margin-bottom: 1rem;
    }
    code {
      background: #F3F4F6;
      padding: 0.2rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.8125rem;
    }
    .alert-banner {
      background: #ECFDF5;
      border: 1px solid #A7F3D0;
      color: #065F46;
      padding: 0.75rem 1rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 1.25rem;
      font-size: 0.875rem;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    .spin { animation: spin 1s linear infinite; }
    .empty-state {
      text-align: center;
      color: #6B7280;
      padding: 1.5rem;
    }
    .run-status {
      font-weight: 600;
    }
    .run-detail {
      font-size: 0.75rem;
      color: #6B7280;
    }
  `]
})
export class SimulatorComponent implements OnInit {
  private portalService = inject(PortalService);

  loading = false;
  faultActive = false;
  statusMessage = '';
  hubs: AcmHubSummary[] = [];
  hubOutages: string[] = [];

  isHubDown(hubName: string): boolean {
    return this.hubOutages.includes(hubName);
  }

  toggleHubOutage(hubName: string): void {
    this.portalService.setHubOutage(hubName, !this.isHubDown(hubName)).subscribe({
      next: (res) => {
        this.statusMessage = res.message;
        this.checkSimulatorStatus();
      },
      error: (err) => console.error('Error toggling hub outage', err)
    });
  }

  hubStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge-ready';
      case 'DEGRADED': return 'badge-staging';
      default: return 'badge-prod';
    }
  }

  circuitClass(state: string): string {
    switch (state) {
      case 'CLOSED': return 'badge-ready';
      case 'HALF_OPEN': return 'badge-staging';
      default: return 'badge-prod';
    }
  }

  ngOnInit(): void {
    this.checkSimulatorStatus();
    this.loadHubs();
  }

  loadHubs(): void {
    this.portalService.getHubs().subscribe({
      next: (res) => (this.hubs = res),
      error: (err) => console.error('Failed to load ACM hubs', err)
    });
  }

  checkSimulatorStatus(): void {
    this.portalService.getSimulatorStatus().subscribe({
      next: (res) => {
        this.faultActive = res.simulateFailureActive;
        this.hubOutages = res.hubOutages;
      },
      error: (err) => console.error('Failed to get simulator status', err)
    });
  }

  toggleFault(): void {
    const nextState = !this.faultActive;
    this.portalService.injectSimulatorFault(nextState).subscribe({
      next: (res) => {
        this.faultActive = res.simulateFailure;
        this.statusMessage = res.message;
      },
      error: (err) => console.error('Error toggling fault', err)
    });
  }

  triggerCollection(): void {
    this.loading = true;
    this.portalService.triggerCollection().subscribe({
      next: (res) => {
        this.loading = false;
        this.statusMessage = res.message;
        this.checkSimulatorStatus();
        this.loadHubs();
      },
      error: (err) => {
        this.loading = false;
        this.statusMessage = 'Collection failed: ' + (err.error?.message || err.message);
        this.checkSimulatorStatus();
        this.loadHubs();
      }
    });
  }

  reseedFleet(): void {
    this.portalService.seedFleet().subscribe({
      next: (res) => {
        this.statusMessage = res.message;
        this.loadHubs();
      },
      error: (err) => console.error('Error seeding fleet', err)
    });
  }
}

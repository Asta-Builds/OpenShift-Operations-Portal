import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
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
            Simulate an ACM Hub network timeout on next collection to verify that Circuit Breaker transitions to fallback without breaking the portal.
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
          <h3 class="control-title">Re-seed Fleet Baseline</h3>
          <p class="control-desc">
            Reinitializes mock clusters, historical 30-day snapshots, and cost center attribution.
          </p>
          <button class="btn btn-secondary" (click)="reseedFleet()">
            <app-icon name="layers" [size]="16"></app-icon> Reset & Seed
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
                <th>API Endpoint</th>
                <th>Status</th>
                <th>Resilience Protection</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>acm-hub-primary-eu</strong></td>
                <td><code>https://api.acm-hub-primary.internal:6443</code></td>
                <td><span class="badge badge-ready">ACTIVE</span></td>
                <td>Circuit Breaker (Resilience4j), Retry (3 attempts)</td>
              </tr>
              <tr>
                <td><strong>acm-hub-secondary-us</strong></td>
                <td><code>https://api.acm-hub-secondary.internal:6443</code></td>
                <td><span class="badge badge-ready">ACTIVE</span></td>
                <td>Circuit Breaker (Resilience4j), Retry (3 attempts)</td>
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
  `]
})
export class SimulatorComponent implements OnInit {
  private portalService = inject(PortalService);

  loading = false;
  faultActive = false;
  statusMessage = '';

  ngOnInit(): void {
    this.checkSimulatorStatus();
  }

  checkSimulatorStatus(): void {
    this.portalService.getSimulatorStatus().subscribe({
      next: (res) => (this.faultActive = res.simulateFailureActive),
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
      },
      error: (err) => {
        this.loading = false;
        this.statusMessage = 'Collection completed with resilience handling: ' + (err.error?.message || err.message);
        this.checkSimulatorStatus();
      }
    });
  }

  reseedFleet(): void {
    this.portalService.seedFleet().subscribe({
      next: (res) => (this.statusMessage = res.message),
      error: (err) => console.error('Error reseeding', err)
    });
  }
}

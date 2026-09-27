import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { AcmHubSummary, SnapshotTriggerResult } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-simulator',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">ACM Hubs & Simulator Controls</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-secondary/10 text-secondary border border-secondary/20">
              Chaos & Resiliency Sandbox
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Multi-Hub connectivity, Resilience4j circuit breakers, and fault injection
          </p>
        </div>
      </div>

      <!-- Action Feedback Banner -->
      <div *ngIf="statusMessage" class="p-3 rounded-2xl bg-success/10 border border-success/30 text-success text-xs font-medium flex items-center justify-between animate-in fade-in">
        <div class="flex items-center gap-2">
          <app-icon name="check-circle" [size]="16"></app-icon>
          <span>{{ statusMessage }}</span>
        </div>
        <button (click)="statusMessage = ''" class="text-success hover:opacity-75">✕</button>
      </div>

      <!-- Interactive Controls (3-Column HeroUI Cards) -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        
        <!-- Manual Collection Card -->
        <div class="heroui-card p-6 flex flex-col justify-between space-y-4">
          <div class="space-y-3">
            <div class="w-10 h-10 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="refresh" [size]="20"></app-icon>
            </div>
            <div>
              <h3 class="text-base font-bold text-foreground">Manual Snapshot Ingestion</h3>
              <p class="text-xs text-default-400 mt-1 leading-relaxed">
                Executes scheduled collection worker immediately across all registered ACM Hubs.
              </p>
            </div>
          </div>

          <button
            type="button"
            (click)="triggerCollection()"
            [disabled]="loading"
            class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 shadow-glow-primary text-xs font-semibold py-2 w-full"
          >
            <app-icon name="refresh" [size]="14" [className]="loading ? 'animate-spin' : ''"></app-icon>
            <span>{{ loading ? 'Running Collection...' : 'Execute Collection Now' }}</span>
          </button>
        </div>

        <!-- Fault Injection Card -->
        <div class="heroui-card p-6 flex flex-col justify-between space-y-4">
          <div class="space-y-3">
            <div class="w-10 h-10 rounded-2xl flex items-center justify-center" [ngClass]="faultActive ? 'bg-danger/10 text-danger' : 'bg-warning/10 text-warning'">
              <app-icon name="zap" [size]="20"></app-icon>
            </div>
            <div>
              <h3 class="text-base font-bold text-foreground">Circuit Breaker Fault Injection</h3>
              <p class="text-xs text-default-400 mt-1 leading-relaxed">
                Simulate a one-off ACM Hub connection timeout on the next collection to test Resilience4j fallbacks.
              </p>
            </div>
          </div>

          <button
            type="button"
            (click)="toggleFault()"
            class="heroui-btn text-xs font-semibold py-2 w-full transition-all"
            [ngClass]="faultActive ? 'bg-danger text-white shadow-glow-danger' : 'bg-content2 hover:bg-content3 border border-divider text-foreground'"
          >
            <app-icon name="alert-triangle" [size]="14"></app-icon>
            <span>{{ faultActive ? 'Fault Injected (Click to Clear)' : 'Inject Connection Timeout' }}</span>
          </button>
        </div>

        <!-- Fleet Simulator Seeder Card -->
        <div class="heroui-card p-6 flex flex-col justify-between space-y-4">
          <div class="space-y-3">
            <div class="w-10 h-10 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
              <app-icon name="database" [size]="20"></app-icon>
            </div>
            <div>
              <h3 class="text-base font-bold text-foreground">Fleet Telemetry Seeder</h3>
              <p class="text-xs text-default-400 mt-1 leading-relaxed">
                Populate synthetic enterprise cluster topologies, ESXi/AWS providerIDs, and namespaces.
              </p>
            </div>
          </div>

          <button
            type="button"
            (click)="seedFleet()"
            class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold py-2 w-full"
          >
            <app-icon name="layers" [size]="14"></app-icon>
            <span>Reseed Test Fleet</span>
          </button>
        </div>

      </div>

      <!-- Registered ACM Hubs Grid -->
      <div class="heroui-card p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Registered ACM Hubs Status</h3>
            <p class="text-xs text-default-400 mt-0.5">Live connectivity status and circuit breaker state per hub</p>
          </div>
          <span class="text-xs text-default-400">{{ hubs.length }} Hubs Enrolled</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div *ngFor="let hub of hubs" class="p-5 rounded-2xl bg-content2 border border-divider space-y-3">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
                  <app-icon name="server" [size]="16"></app-icon>
                </div>
                <div>
                  <h4 class="text-sm font-bold text-foreground">{{ hub.name }}</h4>
                  <span class="text-[10px] text-default-400 font-mono">{{ hub.apiUrl }}</span>
                </div>
              </div>
              <span class="heroui-badge text-[10px]" [ngClass]="hub.status === 'ACTIVE' ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'">
                {{ hub.status }}
              </span>
            </div>

            <div class="flex items-center justify-between text-xs pt-2 border-t border-divider/60 text-default-500">
              <span>Circuit Breaker: <strong class="text-foreground">{{ hub.circuitBreakerState || 'CLOSED' }}</strong></span>
              <span>Last Sync: <strong class="text-foreground">{{ hub.lastSyncTimestamp ? (hub.lastSyncTimestamp | date:'shortTime') : 'Never' }}</strong></span>
            </div>
          </div>
        </div>
      </div>

    </div>
  `
})
export class SimulatorComponent implements OnInit {
  private portalService = inject(PortalService);

  hubs: AcmHubSummary[] = [];
  faultActive = false;
  loading = false;
  statusMessage = '';

  ngOnInit(): void {
    this.loadHubs();
    this.portalService.getSimulatorStatus().subscribe({
      next: (status) => (this.faultActive = status.simulateFailureActive),
      error: () => (this.faultActive = false)
    });
  }

  loadHubs(): void {
    this.portalService.getHubs().subscribe({
      next: (res: AcmHubSummary[]) => (this.hubs = res),
      error: (err: any) => console.error('Failed to load hubs', err)
    });
  }

  triggerCollection(): void {
    this.loading = true;
    this.portalService.triggerCollection().subscribe({
      next: (res: SnapshotTriggerResult) => {
        this.loading = false;
        this.statusMessage = res.message;
        this.loadHubs();
      },
      error: (err: any) => {
        this.loading = false;
        this.statusMessage = 'Collection failed: ' + (err.error?.message || err.message);
      }
    });
  }

  toggleFault(): void {
    const nextState = !this.faultActive;
    this.portalService.injectSimulatorFault(nextState).subscribe({
      next: (res: { simulateFailure: boolean; message: string }) => {
        this.faultActive = res.simulateFailure;
        this.statusMessage = res.message;
      },
      error: (err: any) => {
        this.statusMessage = 'Fault toggle failed: ' + (err.error?.message || err.message);
      }
    });
  }

  seedFleet(): void {
    this.portalService.seedFleet().subscribe({
      next: (res: { message: string }) => {
        this.statusMessage = res.message;
        this.loadHubs();
      },
      error: (err: any) => {
        this.statusMessage = 'Seed failed: ' + (err.error?.message || err.message);
      }
    });
  }
}

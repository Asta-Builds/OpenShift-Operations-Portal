import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortalService } from '../../services/portal.service';
import { ForecastingProjection } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-forecasting',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Capacity & Growth Forecasting</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              Predictive Models
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Rolling regression models estimating future CPU cores and RAM depletion
          </p>
        </div>

        <!-- Horizon Switcher Segmented Control -->
        <div class="flex items-center p-1 rounded-xl bg-content2 border border-divider">
          <button
            type="button"
            (click)="setHorizon(30)"
            [ngClass]="selectedHorizon === 30 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer"
          >
            30 Days
          </button>
          <button
            type="button"
            (click)="setHorizon(60)"
            [ngClass]="selectedHorizon === 60 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer"
          >
            60 Days
          </button>
          <button
            type="button"
            (click)="setHorizon(90)"
            [ngClass]="selectedHorizon === 90 ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer"
          >
            90 Days
          </button>
        </div>
      </div>

      <!-- Capacity Runway Alert Banner -->
      <div *ngIf="projection?.capacityAlert" class="p-4 rounded-2xl bg-danger/10 border border-danger/30 text-danger flex items-start gap-3.5 animate-in fade-in">
        <app-icon name="alert-triangle" [size]="20" className="flex-shrink-0 mt-0.5"></app-icon>
        <div class="text-xs leading-relaxed">
          <strong class="font-bold">Capacity Expansion Recommended:</strong>
          CPU runway <strong>{{ runwayLabel(projection?.runwayDaysCores, projection?.totalCapacityCores) }}</strong>,
          memory runway <strong>{{ runwayLabel(projection?.runwayDaysMemory, projection?.totalCapacityMemoryGb) }}</strong>.
        </div>
      </div>

      <!-- Core Metrics Grid (4-Column HeroUI Cards) -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" *ngIf="projection">
        
        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Current Footprint</span>
            <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="cpu" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-3xl font-extrabold text-foreground">{{ projection.currentCores | number: '1.0-2' }} Cores</div>
          <div class="text-xs text-default-400">RAM: {{ projection.currentMemoryGb | number:'1.0-0' }} GB Active</div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">{{ selectedHorizon }}d Projected Cores</span>
            <div class="w-8 h-8 rounded-xl bg-warning/10 text-warning flex items-center justify-center">
              <app-icon name="trending-up" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-3xl font-extrabold text-foreground">{{ projection.projectedCores | number: '1.0-2' }} Cores</div>
          <div class="text-xs text-warning font-semibold">+{{ projection.estimatedGrowthPercent | number:'1.1-1' }}% Growth Forecast</div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">Daily Growth Rate</span>
            <div class="w-8 h-8 rounded-xl bg-secondary/10 text-secondary flex items-center justify-center">
              <app-icon name="activity" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-3xl font-extrabold text-foreground">+{{ projection.dailyGrowthRateCores | number:'1.1-1' }}</div>
          <div class="text-xs text-default-400">Cores added per day across fleet</div>
        </div>

        <div class="heroui-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold uppercase tracking-wider text-default-400">CPU Core Runway</span>
            <div class="w-8 h-8 rounded-xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="calendar" [size]="16"></app-icon>
            </div>
          </div>
          <div class="text-3xl font-extrabold text-danger">{{ projection.runwayDaysCores !== null ? projection.runwayDaysCores + ' Days' : 'Sufficient' }}</div>
          <div class="text-xs text-default-400">Depletion: <strong class="text-foreground">{{ projection.exhaustionDateCores || 'No exhaustion' }}</strong></div>
        </div>

      </div>

      <!-- Projection Milestones Table -->
      <div class="heroui-card p-6 space-y-4" *ngIf="projection">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Future Capacity Telemetry Checkpoints</h3>
            <p class="text-xs text-default-400 mt-0.5">Projected linear regression checkpoints for capacity planning</p>
          </div>
          <span class="text-xs text-success font-medium">Model R² Confidence: {{ projection.coresRSquared !== null ? (projection.coresRSquared * 100 | number:'1.0-0') + '%' : 'n/a' }}</span>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Projected Date</th>
                <th class="py-3 px-3">Required CPU Cores</th>
                <th class="py-3 px-3">Required Memory</th>
                <th class="py-3 px-3">Total Core Capacity</th>
                <th class="py-3 px-3 text-right">Threshold Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let point of projection.projectedPoints" class="hover:bg-content2/50 transition-colors">
                <td class="py-3 px-3 font-mono font-bold text-foreground">{{ point.date }}</td>
                <td class="py-3 px-3 font-semibold text-foreground">{{ point.cores | number: '1.0-2' }} Cores</td>
                <td class="py-3 px-3 text-default-600">{{ point.memoryGb | number:'1.0-0' }} GB</td>
                <td class="py-3 px-3 text-default-400">{{ projection.totalCapacityCores }} Cores</td>
                <td class="py-3 px-3 text-right">
                  <span class="heroui-badge text-[10px]" [ngClass]="point.cores > projection.totalCapacityCores ? 'bg-danger/15 text-danger' : 'bg-success/15 text-success'">
                    {{ point.cores > projection.totalCapacityCores ? 'CAPACITY EXCEEDED' : 'WITHIN LIMIT' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class ForecastingComponent implements OnInit {
  private portalService = inject(PortalService);

  projection: ForecastingProjection | null = null;
  selectedHorizon = 30;

  ngOnInit(): void {
    this.loadProjection();
  }

  loadProjection(): void {
    this.portalService.getForecastingProjection(this.selectedHorizon).subscribe({
      next: (res) => (this.projection = res),
      error: (err) => console.error('Failed to load forecasting', err)
    });
  }

  setHorizon(days: number): void {
    this.selectedHorizon = days;
    this.loadProjection();
  }

  runwayLabel(days: number | null | undefined, total: number | undefined): string {
    if (days === null || days === undefined) return 'No exhaustion forecast';
    if (days === 0) return 'Exhausted already';
    return `${days} days remaining (Cap: ${total || 0})`;
  }
}

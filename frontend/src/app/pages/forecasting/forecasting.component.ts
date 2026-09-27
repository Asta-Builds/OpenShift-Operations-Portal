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
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Capacity & Resource Forecasting</h1>
          <p class="page-subtitle">Predictive models estimating future CPU cores and memory consumption</p>
        </div>
        <div class="horizon-toggle">
          <button
            class="btn"
            [ngClass]="selectedHorizon === 30 ? 'btn-primary' : 'btn-secondary'"
            (click)="setHorizon(30)"
          >
            30 Days
          </button>
          <button
            class="btn"
            [ngClass]="selectedHorizon === 60 ? 'btn-primary' : 'btn-secondary'"
            (click)="setHorizon(60)"
          >
            60 Days
          </button>
          <button
            class="btn"
            [ngClass]="selectedHorizon === 90 ? 'btn-primary' : 'btn-secondary'"
            (click)="setHorizon(90)"
          >
            90 Days
          </button>
        </div>
      </div>

      <!-- Not enough history for a trend -->
      <div *ngIf="projection?.insufficientData" class="warning-banner">
        <app-icon name="alert-triangle" [size]="20"></app-icon>
        <div>
          <strong>Not enough history to forecast:</strong> {{ projection?.dataPoints }} daily data point(s) in the last
          {{ selectedHorizon }} days; at least 2 are needed.
        </div>
      </div>

      <!-- Capacity Runway Alert Banner -->
      <div *ngIf="projection?.capacityAlert" class="warning-banner">
        <app-icon name="alert-triangle" [size]="20"></app-icon>
        <div>
          <strong>Capacity Expansion Recommended:</strong>
          CPU runway <strong>{{ runwayLabel(projection?.runwayDaysCores, projection?.totalCapacityCores) }}</strong>,
          memory runway <strong>{{ runwayLabel(projection?.runwayDaysMemory, projection?.totalCapacityMemoryGb) }}</strong>.
        </div>
      </div>

      <!-- Projection Cards Grid -->
      <div class="metrics-grid" *ngIf="projection && !projection.insufficientData">
        <div class="card">
          <div class="metric-title">Projected Core Demand</div>
          <div class="metric-value text-red">
            {{ projection.projectedCores }} <span class="metric-unit">Cores</span>
          </div>
          <div class="metric-footer">
            Current: <strong>{{ projection.currentCores }} Cores</strong> (+{{ projection.estimatedGrowthPercent }}%)
          </div>
        </div>

        <div class="card">
          <div class="metric-title">Capacity Runway (CPU)</div>
          <div class="metric-value" [ngClass]="runwayClass(projection.runwayDaysCores)">
            {{ runwayLabel(projection.runwayDaysCores, projection.totalCapacityCores) }}
          </div>
          <div class="metric-footer">
            {{ exhaustionLabel(projection.runwayDaysCores, projection.exhaustionDateCores, projection.totalCapacityCores) }}
          </div>
        </div>

        <div class="card">
          <div class="metric-title">Capacity Runway (Memory)</div>
          <div class="metric-value" [ngClass]="runwayClass(projection.runwayDaysMemory)">
            {{ runwayLabel(projection.runwayDaysMemory, projection.totalCapacityMemoryGb) }}
          </div>
          <div class="metric-footer">
            {{ exhaustionLabel(projection.runwayDaysMemory, projection.exhaustionDateMemory, projection.totalCapacityMemoryGb) }}
          </div>
        </div>

        <div class="card">
          <div class="metric-title">Growth Rate (Daily Velocity)</div>
          <div class="metric-value">+{{ projection.dailyGrowthRateCores }} <span class="metric-unit">Cores/day</span></div>
          <div class="metric-footer">Rolling linear regression slope</div>
        </div>

        <div class="card">
          <div class="metric-title">Fit Quality (R²)</div>
          <div class="metric-value">{{ (projection.coresRSquared | number:'1.2-2') ?? 'n/a' }}</div>
          <div class="metric-footer">CPU trend fitted on {{ projection.dataPoints }} daily points</div>
        </div>
      </div>

      <!-- Forecast Timeline Comparison -->
      <div class="card section-margin" *ngIf="projection && !projection.insufficientData">
        <h2 class="card-title">Trajectory & Forecast Timeline (+{{ selectedHorizon }} Days)</h2>
        <div class="timeline-container">
          <div class="timeline-group">
            <h3 class="group-title">Historical Baseline</h3>
            <div class="points-list">
              <div *ngFor="let pt of projection.historicalPoints" class="point-badge history">
                <span class="date">{{ pt.date }}</span>
                <span class="val">{{ pt.cores }} Cores / {{ pt.memoryGb }} GB</span>
              </div>
            </div>
          </div>

          <div class="timeline-divider">
            <app-icon name="trending-up" [size]="20"></app-icon>
            <span>Rolling OLS Projection</span>
          </div>

          <div class="timeline-group">
            <h3 class="group-title">Projected Growth</h3>
            <div class="points-list">
              <div *ngFor="let pt of projection.projectedPoints" class="point-badge future">
                <span class="date">{{ pt.date }}</span>
                <span class="val">{{ pt.cores }} Cores / {{ pt.memoryGb }} GB</span>
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
      display: flex;
      justify-content: space-between;
      align-items: center;
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
    .horizon-toggle {
      display: flex;
      gap: 0.5rem;
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
    .text-green { color: #10B981; }
    .metric-footer {
      font-size: 0.8125rem;
      color: #6B7280;
    }
    .section-margin {
      margin-top: 1.5rem;
    }
    .card-title {
      font-size: 1.125rem;
      color: #111827;
      margin-bottom: 1.25rem;
    }
    .timeline-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .timeline-group {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .group-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: #4B5563;
      text-transform: uppercase;
    }
    .points-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .point-badge {
      display: flex;
      flex-direction: column;
      padding: 0.5rem 0.875rem;
      border-radius: 0.375rem;
      font-size: 0.8125rem;
      border: 1px solid transparent;
    }
    .point-badge.history {
      background: #F3F4F6;
      border-color: #E5E7EB;
      color: #374151;
    }
    .point-badge.future {
      background: #FEF2F2;
      border-color: #FECACA;
      color: #991B1B;
    }
    .point-badge .date {
      font-size: 0.75rem;
      color: #6B7280;
    }
    .point-badge .val {
      font-weight: 600;
      margin-top: 0.125rem;
    }
    .timeline-divider {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: #0066CC;
      font-size: 0.875rem;
      font-weight: 600;
      padding: 0.5rem 0;
    }
    .warning-banner {
      background: #FFFBEB;
      border: 1px solid #FDE68A;
      color: #92400E;
      padding: 0.75rem 1rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 1.25rem;
      font-size: 0.875rem;
    }
  `]
})
export class ForecastingComponent implements OnInit {
  /** Same threshold ForecastingService uses to raise capacityAlert. */
  private static readonly RUNWAY_ALERT_DAYS = 90;

  private portalService = inject(PortalService);

  projection: ForecastingProjection | null = null;
  selectedHorizon = 30;

  runwayLabel(days: number | null | undefined, capacity: number | undefined): string {
    if (!capacity) {
      return 'Capacity unknown';
    }
    if (days === 0) {
      return 'Exceeded';
    }
    return days == null ? 'No projected exhaustion' : `${days} days`;
  }

  exhaustionLabel(days: number | null, date: string | null, capacity: number): string {
    if (!capacity) {
      return 'No capacity data in this window';
    }
    if (days === 0) {
      return 'Allocation has already reached capacity';
    }
    return date ? `Exhaustion date: ${date}` : 'Allocation is flat or shrinking';
  }

  runwayClass(days: number | null): string {
    return days !== null && days <= ForecastingComponent.RUNWAY_ALERT_DAYS ? 'text-red' : 'text-green';
  }

  ngOnInit(): void {
    this.loadProjection();
  }

  setHorizon(days: number): void {
    this.selectedHorizon = days;
    this.loadProjection();
  }

  loadProjection(): void {
    this.portalService.getForecastingProjection(this.selectedHorizon).subscribe({
      next: (res) => (this.projection = res),
      error: (err) => console.error('Failed to load forecasting', err)
    });
  }
}

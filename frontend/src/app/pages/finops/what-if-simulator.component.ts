import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import {
  WhatIfPreset,
  WhatIfSimulationRequest,
  WhatIfSimulationResult,
  WhatIfWorkload,
  WhatIfDecommission,
  FinOpsEfficiencyRating,
  ClusterSummary,
  HeadroomStatus
} from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-what-if-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Navigation & Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <a routerLink="/finops" class="p-1 rounded-lg text-default-400 hover:text-foreground hover:bg-content2 transition-colors mr-1">
              <app-icon name="arrow-left" [size]="18"></app-icon>
            </a>
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Interactive "What-If" Simulator</h1>
            <span class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-secondary/15 text-secondary border border-secondary/30">
              Capacity & Cost Prediction
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Model rightsizing adoption, predict cluster headroom, test workload onboarding, and estimate infrastructure consolidation ROI before applying changes.
          </p>
        </div>

        <div class="flex items-center gap-2">
          <a
            routerLink="/finops"
            class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold px-3 py-2 flex items-center gap-1.5"
          >
            <app-icon name="coins" [size]="15"></app-icon>
            <span>FinOps Engine</span>
          </a>

          <button
            type="button"
            (click)="resetToDefaults()"
            class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-default-500 hover:text-foreground text-xs font-semibold px-3 py-2 flex items-center gap-1.5"
            title="Reset simulation parameters"
          >
            <app-icon name="refresh" [size]="14"></app-icon>
            <span>Reset</span>
          </button>
        </div>
      </div>

      <!-- Presets Banner (1-Click Enterprise Scenarios) -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-default-400">
            <app-icon name="sparkles" [size]="14" className="text-amber-500"></app-icon>
            <span>1-Click Enterprise Scenarios</span>
          </div>
          <span class="text-[11px] text-default-400">Click a scenario to evaluate fleet impact instantly</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
          <button
            *ngFor="let preset of presets"
            type="button"
            (click)="applyPreset(preset)"
            [ngClass]="activePresetId === preset.id ? 'border-primary bg-primary/10 ring-1 ring-primary shadow-sm' : ''"
            class="heroui-card p-4 text-left transition-all hover:scale-[1.01] hover:border-primary/40 cursor-pointer group flex flex-col justify-between"
          >
            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="text-[10px] font-bold uppercase tracking-wider text-default-400">{{ preset.category }}</span>
                <span class="px-1.5 py-0.5 rounded text-[9px] font-extrabold tracking-wider bg-primary/10 text-primary border border-primary/20">
                  {{ preset.badge }}
                </span>
              </div>
              <h3 class="text-xs font-bold text-foreground group-hover:text-primary transition-colors flex items-center gap-1.5">
                <app-icon [name]="preset.icon || 'zap'" [size]="14" className="text-primary flex-shrink-0"></app-icon>
                <span>{{ preset.title }}</span>
              </h3>
              <p class="text-[11px] text-default-400 leading-relaxed line-clamp-2">
                {{ preset.description }}
              </p>
            </div>
            <div class="pt-3 mt-3 border-t border-divider/60 flex items-center justify-between text-[10px] font-semibold text-primary">
              <span>Apply Scenario</span>
              <app-icon name="arrow-right" [size]="12"></app-icon>
            </div>
          </button>
        </div>
      </div>

      <!-- Main Simulation Workbench (2 Columns: Controls on Left, Results on Right) -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">

        <!-- Left Column: Simulation Controls Deck (5 cols) -->
        <div class="lg:col-span-5 space-y-4">
          
          <!-- Control 1: Rightsizing Adoption Rate Slider -->
          <div class="heroui-card p-5 space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center">
                  <app-icon name="sliders" [size]="16"></app-icon>
                </div>
                <div>
                  <h3 class="text-xs font-bold text-foreground">Rightsizing Adoption</h3>
                  <span class="text-[10px] text-default-400">Scale quota remediation across fleet</span>
                </div>
              </div>
              <div class="text-right">
                <span class="text-lg font-extrabold text-emerald-500">{{ request.rightsizingAdoptionPercent }}%</span>
                <span class="block text-[9px] text-default-400 uppercase font-bold">Target Adoption</span>
              </div>
            </div>

            <!-- Slider Range -->
            <div class="space-y-2">
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                [(ngModel)]="request.rightsizingAdoptionPercent"
                (ngModelChange)="onParamChange()"
                class="w-full h-2 bg-content3 rounded-lg appearance-none cursor-pointer accent-emerald-500"
              />
              <div class="flex justify-between text-[10px] text-default-400 font-mono">
                <button type="button" (click)="setAdoption(0)" class="hover:text-foreground">0%</button>
                <button type="button" (click)="setAdoption(25)" class="hover:text-foreground">25%</button>
                <button type="button" (click)="setAdoption(50)" class="hover:text-foreground">50% (Avg)</button>
                <button type="button" (click)="setAdoption(75)" class="hover:text-foreground">75%</button>
                <button type="button" (click)="setAdoption(100)" class="hover:text-foreground">100% (Max)</button>
              </div>
            </div>

            <!-- Target Efficiency Tiers -->
            <div class="pt-2 border-t border-divider space-y-2">
              <span class="text-[10px] font-bold uppercase tracking-wider text-default-400 block">Target Efficiency Tiers</span>
              <div class="grid grid-cols-2 gap-2 text-xs">
                <label class="flex items-center gap-2 p-2 rounded-xl bg-content2/60 border border-divider cursor-pointer hover:bg-content2">
                  <input
                    type="checkbox"
                    [checked]="hasRating('SEVERE_WASTE')"
                    (change)="toggleRating('SEVERE_WASTE')"
                    class="rounded text-danger accent-danger"
                  />
                  <span class="font-medium text-danger text-[11px]">Severe Waste (&lt;30%)</span>
                </label>
                <label class="flex items-center gap-2 p-2 rounded-xl bg-content2/60 border border-divider cursor-pointer hover:bg-content2">
                  <input
                    type="checkbox"
                    [checked]="hasRating('OVER_PROVISIONED')"
                    (change)="toggleRating('OVER_PROVISIONED')"
                    class="rounded text-amber-500 accent-amber-500"
                  />
                  <span class="font-medium text-amber-500 text-[11px]">Over-Provisioned</span>
                </label>
              </div>
            </div>
          </div>

          <!-- Control 2: Workload Onboarding (Capacity Stress Test) -->
          <div class="heroui-card p-5 space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
                  <app-icon name="box" [size]="16"></app-icon>
                </div>
                <div>
                  <h3 class="text-xs font-bold text-foreground">Workload Onboarding</h3>
                  <span class="text-[10px] text-default-400">Simulate landing new apps</span>
                </div>
              </div>
              <button
                type="button"
                (click)="toggleAddWorkloadForm()"
                class="px-2 py-1 rounded-lg bg-content2 hover:bg-content3 border border-divider text-[10px] font-bold text-foreground flex items-center gap-1 cursor-pointer"
              >
                <app-icon [name]="showWorkloadForm ? 'minus-circle' : 'plus'" [size]="12"></app-icon>
                <span>{{ showWorkloadForm ? 'Close' : 'Add App' }}</span>
              </button>
            </div>

            <!-- New Workload Form -->
            <div *ngIf="showWorkloadForm" class="p-3.5 rounded-2xl bg-content2/50 border border-divider space-y-3 animate-in fade-in duration-150">
              <div class="space-y-1">
                <label class="text-[10px] font-bold text-default-400 uppercase">Application / Workload Name</label>
                <input
                  type="text"
                  [(ngModel)]="newWorkload.name"
                  placeholder="e.g. core-banking-v3-gateway"
                  class="w-full px-3 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-primary"
                />
              </div>

              <div class="grid grid-cols-2 gap-2">
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">Target Cluster</label>
                  <select
                    [(ngModel)]="newWorkload.targetClusterName"
                    class="w-full px-2.5 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-primary"
                  >
                    <option *ngFor="let c of clusters" [value]="c.clusterName">{{ c.clusterName }}</option>
                  </select>
                </div>
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">CPU Requests (Cores)</label>
                  <input
                    type="number"
                    min="1"
                    max="256"
                    [(ngModel)]="newWorkload.requestedCpuCores"
                    class="w-full px-3 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-primary"
                  />
                </div>
              </div>

              <div class="grid grid-cols-2 gap-2">
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">Memory RAM (GB)</label>
                  <input
                    type="number"
                    min="1"
                    max="1024"
                    [(ngModel)]="newWorkload.requestedMemoryGb"
                    class="w-full px-3 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-primary"
                  />
                </div>
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">PVC Storage (GB)</label>
                  <input
                    type="number"
                    min="0"
                    max="5000"
                    [(ngModel)]="newWorkload.requestedStorageGb"
                    class="w-full px-3 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-primary"
                  />
                </div>
              </div>

              <button
                type="button"
                (click)="addWorkload()"
                class="heroui-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold w-full py-1.5 cursor-pointer"
              >
                Inject Workload into Simulation
              </button>
            </div>

            <!-- List of Injected Workloads -->
            <div *ngIf="request.additionalWorkloads && request.additionalWorkloads.length > 0" class="space-y-1.5">
              <div
                *ngFor="let w of request.additionalWorkloads; let idx = index"
                class="flex items-center justify-between p-2.5 rounded-xl bg-content2 border border-divider text-xs"
              >
                <div class="space-y-0.5">
                  <span class="font-bold text-foreground block">{{ w.name }}</span>
                  <div class="text-[10px] text-default-400 flex items-center gap-2">
                    <span class="text-primary font-semibold">-> {{ w.targetClusterName }}</span>
                    <span>• {{ w.requestedCpuCores }} Cores</span>
                    <span>• {{ w.requestedMemoryGb }} GB</span>
                  </div>
                </div>
                <button
                  type="button"
                  (click)="removeWorkload(idx)"
                  class="p-1 rounded-lg text-default-400 hover:text-danger hover:bg-danger/10 transition-colors cursor-pointer"
                >
                  <app-icon name="trash" [size]="13"></app-icon>
                </button>
              </div>
            </div>
          </div>

          <!-- Control 3: Cluster Decommission & Consolidation -->
          <div class="heroui-card p-5 space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-xl bg-amber-500/10 text-amber-500 flex items-center justify-center">
                  <app-icon name="minimize-2" [size]="16"></app-icon>
                </div>
                <div>
                  <h3 class="text-xs font-bold text-foreground">Cluster Consolidation</h3>
                  <span class="text-[10px] text-default-400">Drain & decommission redundant clusters</span>
                </div>
              </div>
            </div>

            <div class="space-y-2">
              <div class="grid grid-cols-2 gap-2 text-xs">
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">Drain Source</label>
                  <select
                    [(ngModel)]="selectedDecomSource"
                    (change)="onDecomSelect()"
                    class="w-full px-2.5 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-amber-500"
                  >
                    <option value="">-- None --</option>
                    <option *ngFor="let c of clusters" [value]="c.clusterName">{{ c.clusterName }}</option>
                  </select>
                </div>
                <div class="space-y-1">
                  <label class="text-[10px] font-bold text-default-400 uppercase">Absorb into Target</label>
                  <select
                    [(ngModel)]="selectedDecomTarget"
                    (change)="onDecomSelect()"
                    class="w-full px-2.5 py-1.5 rounded-xl bg-background border border-divider text-xs text-foreground outline-none focus:border-amber-500"
                  >
                    <option value="">-- None --</option>
                    <option *ngFor="let c of clusters" [value]="c.clusterName">{{ c.clusterName }}</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          <!-- Control 4: Fleet-Wide Organic Growth Slider -->
          <div class="heroui-card p-5 space-y-3">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-xl bg-secondary/10 text-secondary flex items-center justify-center">
                  <app-icon name="activity" [size]="16"></app-icon>
                </div>
                <div>
                  <h3 class="text-xs font-bold text-foreground">Global Growth Stress-Test</h3>
                  <span class="text-[10px] text-default-400">Fleet-wide demand variance</span>
                </div>
              </div>
              <span class="text-sm font-bold font-mono" [ngClass]="(request.fleetGrowthPercent || 0) > 0 ? 'text-amber-500' : 'text-default-400'">
                {{ (request.fleetGrowthPercent || 0) > 0 ? '+' : '' }}{{ request.fleetGrowthPercent || 0 }}%
              </span>
            </div>

            <input
              type="range"
              min="-20"
              max="50"
              step="5"
              [(ngModel)]="request.fleetGrowthPercent"
              (ngModelChange)="onParamChange()"
              class="w-full h-2 bg-content3 rounded-lg appearance-none cursor-pointer accent-secondary"
            />
          </div>
        </div>

        <!-- Right Column: Live Simulation Results & Headroom Scoreboard (7 cols) -->
        <div class="lg:col-span-7 space-y-6">

          <!-- Executive Scoreboard Cards -->
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4" *ngIf="result">
            <!-- Monthly Net Cost Delta -->
            <div class="heroui-card p-5 space-y-2 border-emerald-500/30 relative overflow-hidden bg-gradient-to-br from-content1 to-emerald-500/5">
              <div class="flex items-center justify-between text-emerald-500">
                <span class="text-xs font-bold uppercase tracking-wider">Projected Monthly Savings</span>
                <div class="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center shadow-sm">
                  <app-icon name="trending-down" [size]="18"></app-icon>
                </div>
              </div>
              <div class="text-3xl font-extrabold text-emerald-500 tracking-tight">
                \${{ result.monthlySavingsDelta | number:'1.2-2' }} <span class="text-xs font-normal text-default-400">/ mo</span>
              </div>
              <div class="text-xs text-default-400 flex items-center justify-between pt-1">
                <span>Annualized run-rate:</span>
                <strong class="text-emerald-400 font-bold">\${{ result.annualizedSavingsDelta | number:'1.0-0' }} / yr</strong>
              </div>
            </div>

            <!-- Recovered Capacity -->
            <div class="heroui-card p-5 space-y-2 relative overflow-hidden">
              <div class="flex items-center justify-between text-primary">
                <span class="text-xs font-bold uppercase tracking-wider">Recovered Capacity</span>
                <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center shadow-sm">
                  <app-icon name="cpu" [size]="18"></app-icon>
                </div>
              </div>
              <div class="text-3xl font-extrabold text-foreground tracking-tight">
                {{ result.totalFreedCpuCores | number:'1.0-1' }} <span class="text-xs font-normal text-default-400">Cores</span>
              </div>
              <div class="text-xs text-default-400 flex items-center justify-between pt-1">
                <span>Memory RAM freed:</span>
                <strong class="text-foreground font-bold">{{ result.totalFreedMemoryGb | number:'1.0-1' }} GB</strong>
              </div>
            </div>
          </div>

          <!-- Fleet Allocation Gauge (Before vs After) -->
          <div class="heroui-card p-5 space-y-4" *ngIf="result">
            <h3 class="text-xs font-bold uppercase tracking-wider text-default-400 flex items-center gap-1.5">
              <app-icon name="layers" [size]="14"></app-icon>
              <span>Fleet Allocation Shift (Before vs After Simulation)</span>
            </h3>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <!-- CPU Allocation -->
              <div class="p-3.5 rounded-2xl bg-content2/50 border border-divider space-y-2">
                <div class="flex items-center justify-between text-xs">
                  <span class="font-medium text-default-400">Fleet CPU Allocated</span>
                  <div class="flex items-center gap-2 font-mono">
                    <span class="line-through text-default-400">{{ result.baselineFleetCpuAllocPercent }}%</span>
                    <span class="font-bold text-emerald-500 text-sm">{{ result.simulatedFleetCpuAllocPercent }}%</span>
                  </div>
                </div>
                <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                  <div
                    class="h-full rounded-full bg-emerald-500 transition-all duration-500"
                    [style.width.%]="result.simulatedFleetCpuAllocPercent"
                  ></div>
                </div>
              </div>

              <!-- Memory Allocation -->
              <div class="p-3.5 rounded-2xl bg-content2/50 border border-divider space-y-2">
                <div class="flex items-center justify-between text-xs">
                  <span class="font-medium text-default-400">Fleet RAM Allocated</span>
                  <div class="flex items-center gap-2 font-mono">
                    <span class="line-through text-default-400">{{ result.baselineFleetMemoryAllocPercent }}%</span>
                    <span class="font-bold text-secondary text-sm">{{ result.simulatedFleetMemoryAllocPercent }}%</span>
                  </div>
                </div>
                <div class="w-full h-2 rounded-full bg-content3 overflow-hidden">
                  <div
                    class="h-full rounded-full bg-secondary transition-all duration-500"
                    [style.width.%]="result.simulatedFleetMemoryAllocPercent"
                  ></div>
                </div>
              </div>
            </div>
          </div>

          <!-- Cluster Headroom & Capacity Impact Grid -->
          <div class="heroui-card p-5 space-y-4" *ngIf="result">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="text-xs font-bold uppercase tracking-wider text-default-400">Cluster Capacity & Headroom Matrix</h3>
                <span class="text-[11px] text-default-400">Simulated load shift per cluster node pool</span>
              </div>
              <span class="text-[11px] font-mono text-default-400">{{ result.clusterImpacts.length }} Clusters</span>
            </div>

            <div class="space-y-3">
              <div
                *ngFor="let ci of result.clusterImpacts"
                class="p-4 rounded-2xl border transition-all"
                [ngClass]="getClusterCardClass(ci)"
              >
                <!-- Cluster Header -->
                <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-divider/60">
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold text-foreground">{{ ci.clusterName }}</span>
                    <span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-content2 border border-divider text-default-500">
                      {{ ci.environment }}
                    </span>
                    <span *ngIf="ci.decommissioned" class="px-2 py-0.5 rounded text-[10px] font-bold bg-danger/10 text-danger border border-danger/20">
                      DRAINED & DECOMMISSIONED
                    </span>
                  </div>

                  <!-- Headroom & Node Badges -->
                  <div class="flex items-center gap-2">
                    <span
                      class="px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wide border flex items-center gap-1"
                      [ngClass]="getHeadroomPillClass(ci.headroomStatus)"
                    >
                      <app-icon [name]="getHeadroomIcon(ci.headroomStatus)" [size]="11"></app-icon>
                      {{ ci.headroomStatus }}
                    </span>

                    <span
                      *ngIf="ci.suggestedWorkerNodeDelta !== 0"
                      class="px-2 py-0.5 rounded-full text-[10px] font-bold border"
                      [ngClass]="ci.suggestedWorkerNodeDelta > 0 ? 'bg-danger/10 text-danger border-danger/20' : 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'"
                    >
                      {{ ci.suggestedWorkerNodeDelta > 0 ? '+' + ci.suggestedWorkerNodeDelta + ' Node Required' : ci.suggestedWorkerNodeDelta + ' Node Downsize' }}
                    </span>
                  </div>
                </div>

                <!-- Cluster Usage Progress Bars (if not decommissioned) -->
                <div *ngIf="!ci.decommissioned" class="pt-3 grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <!-- CPU Allocation Bar -->
                  <div class="space-y-1">
                    <div class="flex items-center justify-between text-[11px]">
                      <span class="text-default-400">CPU Allocation:</span>
                      <div class="flex items-center gap-1.5 font-mono">
                        <span class="text-default-400 text-[10px]">{{ ci.baselineCpuAllocPercent }}%</span>
                        <span class="text-default-400">-></span>
                        <strong [ngClass]="ci.simulatedCpuAllocPercent > 85 ? 'text-danger' : 'text-foreground'">
                          {{ ci.simulatedCpuAllocPercent }}%
                        </strong>
                        <span class="text-[10px] text-default-400">({{ ci.simulatedAllocatedCores }}/{{ ci.totalCores }} Cores)</span>
                      </div>
                    </div>
                    <div class="w-full h-1.5 rounded-full bg-content3 overflow-hidden">
                      <div
                        class="h-full rounded-full transition-all duration-500"
                        [ngClass]="ci.simulatedCpuAllocPercent > 85 ? 'bg-danger' : 'bg-primary'"
                        [style.width.%]="ci.simulatedCpuAllocPercent"
                      ></div>
                    </div>
                  </div>

                  <!-- Memory Allocation Bar -->
                  <div class="space-y-1">
                    <div class="flex items-center justify-between text-[11px]">
                      <span class="text-default-400">Memory Allocation:</span>
                      <div class="flex items-center gap-1.5 font-mono">
                        <span class="text-default-400 text-[10px]">{{ ci.baselineMemoryAllocPercent }}%</span>
                        <span class="text-default-400">-></span>
                        <strong [ngClass]="ci.simulatedMemoryAllocPercent > 85 ? 'text-danger' : 'text-foreground'">
                          {{ ci.simulatedMemoryAllocPercent }}%
                        </strong>
                        <span class="text-[10px] text-default-400">({{ ci.simulatedAllocatedMemoryGb }}/{{ ci.totalMemoryGb }} GB)</span>
                      </div>
                    </div>
                    <div class="w-full h-1.5 rounded-full bg-content3 overflow-hidden">
                      <div
                        class="h-full rounded-full transition-all duration-500"
                        [ngClass]="ci.simulatedMemoryAllocPercent > 85 ? 'bg-danger' : 'bg-secondary'"
                        [style.width.%]="ci.simulatedMemoryAllocPercent"
                      ></div>
                    </div>
                  </div>
                </div>

                <!-- Cluster Warnings / Notices -->
                <div *ngIf="ci.warnings && ci.warnings.length > 0" class="mt-2.5 pt-2 border-t border-divider/40">
                  <div *ngFor="let w of ci.warnings" class="flex items-center gap-1.5 text-[11px] text-amber-500 font-medium">
                    <app-icon name="alert-triangle" [size]="12"></app-icon>
                    <span>{{ w }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Strategic Recommendations & Action Plan -->
          <div class="heroui-card p-5 space-y-3" *ngIf="result && result.strategicRecommendations.length > 0">
            <h3 class="text-xs font-bold uppercase tracking-wider text-default-400 flex items-center gap-1.5">
              <app-icon name="sparkles" [size]="14" className="text-primary"></app-icon>
              <span>Automated Strategic Roadmap</span>
            </h3>
            <div class="space-y-2">
              <div
                *ngFor="let rec of result.strategicRecommendations"
                class="flex items-start gap-2.5 p-3 rounded-xl bg-content2/50 border border-divider text-xs text-foreground leading-relaxed"
              >
                <div class="w-5 h-5 rounded-full bg-primary/10 text-primary flex items-center justify-center flex-shrink-0 mt-0.5">
                  <app-icon name="check-circle" [size]="13"></app-icon>
                </div>
                <span>{{ rec }}</span>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  `
})
export class WhatIfSimulatorComponent implements OnInit {
  private portalService = inject(PortalService);

  presets: WhatIfPreset[] = [];
  clusters: ClusterSummary[] = [];
  activePresetId: string | null = 'preset-balanced-enterprise';

  request: WhatIfSimulationRequest = {
    rightsizingAdoptionPercent: 50,
    targetEfficiencyRatings: ['SEVERE_WASTE', 'OVER_PROVISIONED'],
    additionalWorkloads: [],
    clusterDecommissions: [],
    fleetGrowthPercent: 0
  };

  result: WhatIfSimulationResult | null = null;
  loading: boolean = false;
  showWorkloadForm: boolean = false;

  newWorkload: WhatIfWorkload = {
    name: '',
    targetClusterName: '',
    requestedCpuCores: 16,
    requestedMemoryGb: 64,
    requestedStorageGb: 200
  };

  selectedDecomSource: string = '';
  selectedDecomTarget: string = '';

  ngOnInit(): void {
    this.loadPresets();
    this.loadClusters();
    this.runSimulation();
  }

  loadPresets(): void {
    this.portalService.getWhatIfPresets().subscribe({
      next: (presets) => {
        this.presets = presets;
      },
      error: (err) => console.error('Failed to load presets', err)
    });
  }

  loadClusters(): void {
    this.portalService.getClusters().subscribe({
      next: (clusters) => {
        this.clusters = clusters;
        if (clusters.length > 0 && !this.newWorkload.targetClusterName) {
          this.newWorkload.targetClusterName = clusters[0].clusterName;
        }
      },
      error: (err) => console.error('Failed to load clusters', err)
    });
  }

  applyPreset(preset: WhatIfPreset): void {
    this.activePresetId = preset.id;
    this.request = {
      rightsizingAdoptionPercent: preset.request.rightsizingAdoptionPercent,
      targetEfficiencyRatings: preset.request.targetEfficiencyRatings || ['SEVERE_WASTE', 'OVER_PROVISIONED'],
      additionalWorkloads: preset.request.additionalWorkloads ? [...preset.request.additionalWorkloads] : [],
      clusterDecommissions: preset.request.clusterDecommissions ? [...preset.request.clusterDecommissions] : [],
      fleetGrowthPercent: preset.request.fleetGrowthPercent || 0
    };

    if (this.request.clusterDecommissions && this.request.clusterDecommissions.length > 0) {
      this.selectedDecomSource = this.request.clusterDecommissions[0].sourceClusterName || '';
      this.selectedDecomTarget = this.request.clusterDecommissions[0].targetClusterName || '';
    } else {
      this.selectedDecomSource = '';
      this.selectedDecomTarget = '';
    }

    this.runSimulation();
  }

  setAdoption(pct: number): void {
    this.request.rightsizingAdoptionPercent = pct;
    this.onParamChange();
  }

  onParamChange(): void {
    this.activePresetId = null;
    this.runSimulation();
  }

  toggleRating(rating: FinOpsEfficiencyRating): void {
    if (!this.request.targetEfficiencyRatings) {
      this.request.targetEfficiencyRatings = [];
    }
    const idx = this.request.targetEfficiencyRatings.indexOf(rating);
    if (idx >= 0) {
      this.request.targetEfficiencyRatings.splice(idx, 1);
    } else {
      this.request.targetEfficiencyRatings.push(rating);
    }
    this.onParamChange();
  }

  hasRating(rating: FinOpsEfficiencyRating): boolean {
    return this.request.targetEfficiencyRatings?.includes(rating) || false;
  }

  toggleAddWorkloadForm(): void {
    this.showWorkloadForm = !this.showWorkloadForm;
  }

  addWorkload(): void {
    if (!this.newWorkload.name || !this.newWorkload.targetClusterName) {
      return;
    }
    if (!this.request.additionalWorkloads) {
      this.request.additionalWorkloads = [];
    }
    this.request.additionalWorkloads.push({ ...this.newWorkload });
    this.newWorkload = {
      name: '',
      targetClusterName: this.clusters[0]?.clusterName || '',
      requestedCpuCores: 16,
      requestedMemoryGb: 64,
      requestedStorageGb: 200
    };
    this.showWorkloadForm = false;
    this.onParamChange();
  }

  removeWorkload(index: number): void {
    this.request.additionalWorkloads?.splice(index, 1);
    this.onParamChange();
  }

  onDecomSelect(): void {
    if (this.selectedDecomSource && this.selectedDecomTarget && this.selectedDecomSource !== this.selectedDecomTarget) {
      this.request.clusterDecommissions = [{
        sourceClusterName: this.selectedDecomSource,
        targetClusterName: this.selectedDecomTarget
      }];
    } else {
      this.request.clusterDecommissions = [];
    }
    this.onParamChange();
  }

  resetToDefaults(): void {
    this.request = {
      rightsizingAdoptionPercent: 50,
      targetEfficiencyRatings: ['SEVERE_WASTE', 'OVER_PROVISIONED'],
      additionalWorkloads: [],
      clusterDecommissions: [],
      fleetGrowthPercent: 0
    };
    this.selectedDecomSource = '';
    this.selectedDecomTarget = '';
    this.activePresetId = null;
    this.runSimulation();
  }

  runSimulation(): void {
    this.loading = true;
    this.portalService.simulateWhatIf(this.request).subscribe({
      next: (result) => {
        this.result = result;
        this.loading = false;
      },
      error: (err) => {
        console.error('Simulation failed', err);
        this.loading = false;
      }
    });
  }

  getClusterCardClass(ci: any): string {
    if (ci.decommissioned) {
      return 'bg-content1/50 border-dashed border-danger/30 opacity-75';
    }
    if (ci.headroomStatus === 'CRITICAL_OVERCOMMITTED') {
      return 'bg-danger/5 border-danger/40';
    }
    if (ci.headroomStatus === 'WARNING_TIGHT') {
      return 'bg-amber-500/5 border-amber-500/30';
    }
    return 'bg-content1 border-divider';
  }

  getHeadroomPillClass(status: HeadroomStatus): string {
    switch (status) {
      case 'CRITICAL_OVERCOMMITTED':
        return 'bg-danger/10 text-danger border-danger/20';
      case 'WARNING_TIGHT':
        return 'bg-amber-500/10 text-amber-500 border-amber-500/20';
      case 'HEALTHY':
        return 'bg-primary/10 text-primary border-primary/20';
      case 'OPTIMAL':
      default:
        return 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20';
    }
  }

  getHeadroomIcon(status: HeadroomStatus): string {
    switch (status) {
      case 'CRITICAL_OVERCOMMITTED':
        return 'alert-triangle';
      case 'WARNING_TIGHT':
        return 'alert-triangle';
      case 'HEALTHY':
        return 'check-circle';
      case 'OPTIMAL':
      default:
        return 'shield-check';
    }
  }
}

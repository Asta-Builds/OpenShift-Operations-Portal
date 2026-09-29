import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import {
  FinOpsOverview,
  FinOpsNamespaceRecommendation,
  FinOpsPricingConfig,
  FinOpsEfficiencyRating,
  Environment
} from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { saveDownload } from '../../shared/download';

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
  selector: 'app-finops',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  template: `
    <div class="space-y-6">
      
      <!-- Top Header Row -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">FinOps & Rightsizing Engine</h1>
            <span class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
              Cost & Waste Optimization
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1" *ngIf="overview">
            Automated cluster resource rightsizing, over-provisioning waste quantification, and Kubernetes quota remediation.
          </p>
        </div>

        <div class="flex items-center gap-2">
          <!-- Pricing Rates Button -->
          <button
            type="button"
            (click)="openPricingModal()"
            class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs font-semibold px-3 py-2 flex items-center gap-1.5"
            title="Configure Unit Costs"
          >
            <app-icon name="sliders" [size]="15"></app-icon>
            <span>Unit Pricing</span>
          </button>

          <!-- Export CSV Button -->
          <button
            type="button"
            *ngIf="auth.hasRole('OPERATOR')"
            (click)="exportCsv()"
            class="heroui-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold px-4 py-2 flex items-center gap-1.5"
          >
            <app-icon name="download" [size]="15"></app-icon>
            <span>Export FinOps CSV</span>
          </button>
        </div>
      </div>

      <!-- Overview KPI Cards -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" *ngIf="overview">
        <!-- Monthly Fleet Spend -->
        <div class="heroui-card p-5 space-y-2 relative overflow-hidden">
          <div class="flex items-center justify-between text-default-400">
            <span class="text-xs font-medium uppercase tracking-wider">Monthly Projected Spend</span>
            <div class="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="coins" [size]="18"></app-icon>
            </div>
          </div>
          <div class="text-2xl font-bold text-foreground">
            \${{ overview.totalMonthlyAllocatedCost | number:'1.2-2' }}
          </div>
          <div class="flex items-center gap-1 text-[11px] text-default-500">
            <span>Actual usage:</span>
            <strong class="text-foreground">\${{ overview.totalMonthlyActualCost | number:'1.2-2' }}</strong>
          </div>
        </div>

        <!-- Monthly Wasted Spend -->
        <div class="heroui-card p-5 space-y-2 border-danger/30 relative overflow-hidden">
          <div class="flex items-center justify-between text-danger">
            <span class="text-xs font-medium uppercase tracking-wider">Identified Monthly Waste</span>
            <div class="w-8 h-8 rounded-xl bg-danger/10 text-danger flex items-center justify-center">
              <app-icon name="shield-alert" [size]="18"></app-icon>
            </div>
          </div>
          <div class="text-2xl font-bold text-danger">
            \${{ overview.totalMonthlyWastedCost | number:'1.2-2' }}
          </div>
          <div class="text-[11px] text-danger/80">
            Unused CPU/RAM capacity requested across namespaces
          </div>
        </div>

        <!-- Fleet Efficiency Ratio -->
        <div class="heroui-card p-5 space-y-2 relative overflow-hidden">
          <div class="flex items-center justify-between text-default-400">
            <span class="text-xs font-medium uppercase tracking-wider">Fleet Resource Efficiency</span>
            <div class="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center">
              <app-icon name="trending-up" [size]="18"></app-icon>
            </div>
          </div>
          <div class="text-2xl font-bold" [ngClass]="overview.overallFleetEfficiencyPercent >= 70 ? 'text-emerald-500' : 'text-amber-500'">
            {{ overview.overallFleetEfficiencyPercent | number:'1.1-1' }}%
          </div>
          <div class="w-full bg-content3 rounded-full h-1.5 overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500"
              [ngClass]="overview.overallFleetEfficiencyPercent >= 70 ? 'bg-emerald-500' : 'bg-amber-500'"
              [style.width.%]="overview.overallFleetEfficiencyPercent"
            ></div>
          </div>
        </div>

        <!-- Annual Savings Potential -->
        <div class="heroui-card p-5 space-y-2 border-emerald-500/30 relative overflow-hidden">
          <div class="flex items-center justify-between text-emerald-500">
            <span class="text-xs font-medium uppercase tracking-wider">Annualized Savings Potential</span>
            <div class="w-8 h-8 rounded-xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center">
              <app-icon name="sparkles" [size]="18"></app-icon>
            </div>
          </div>
          <div class="text-2xl font-bold text-emerald-500">
            \${{ overview.totalAnnualizedSavingsPotential | number:'1.2-2' }}
          </div>
          <div class="text-[11px] text-emerald-600 dark:text-emerald-400">
            Achievable by applying recommended rightsizing quotas
          </div>
        </div>
      </div>

      <!-- Filters Toolbar -->
      <div class="heroui-card p-4 flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
        <!-- Quick Range Buttons -->
        <div class="flex items-center gap-1.5 p-1 rounded-xl bg-content2 border border-divider">
          <button
            *ngFor="let days of quickRanges"
            (click)="useRange(days)"
            [ngClass]="activeRange === days ? 'bg-content1 text-foreground shadow-sm' : 'text-default-400 hover:text-foreground'"
            class="px-3 py-1 rounded-lg text-xs font-semibold transition-all cursor-pointer"
          >
            Last {{ days }}d
          </button>
        </div>

        <!-- Date Range Pickers & Environment & Rating Filters -->
        <div class="flex flex-wrap items-center gap-3">
          <div class="flex items-center gap-2 text-xs text-default-400">
            <span>From:</span>
            <input
              type="date"
              [(ngModel)]="from"
              [max]="to"
              (change)="activeRange = null; load()"
              class="px-2.5 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
            />
          </div>

          <div class="flex items-center gap-2 text-xs text-default-400">
            <span>To:</span>
            <input
              type="date"
              [(ngModel)]="to"
              [min]="from"
              (change)="activeRange = null; load()"
              class="px-2.5 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
            />
          </div>

          <select
            [(ngModel)]="environment"
            (change)="load()"
            class="px-3 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All Environments</option>
            <option *ngFor="let env of environments" [value]="env">{{ env }}</option>
          </select>

          <select
            [(ngModel)]="ratingFilter"
            (change)="filterRecommendations()"
            class="px-3 py-1.5 rounded-xl bg-content2 border border-divider text-xs text-foreground outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All Waste Tiers</option>
            <option value="SEVERE_WASTE">Severe Waste (&lt;35% eff)</option>
            <option value="OVER_PROVISIONED">Over-Provisioned (35-60%)</option>
            <option value="ACCEPTABLE">Acceptable (60-80%)</option>
            <option value="OPTIMAL">Optimal (&gt;80%)</option>
            <option value="UNDER_PROVISIONED">Under-Provisioned (Risk)</option>
          </select>
        </div>
      </div>

      <div *ngIf="error" class="p-3 rounded-2xl bg-danger/10 border border-danger/30 text-danger text-xs font-medium">
        {{ error }}
      </div>

      <!-- Cost & Waste by Team Breakdown -->
      <div class="heroui-card p-6 space-y-4" *ngIf="overview && overview.teamBreakdowns.length">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">FinOps Cost & Waste by Team</h3>
            <p class="text-xs text-default-400 mt-0.5">Chargeback breakdown and optimization headroom per business unit</p>
          </div>
          <span class="text-xs font-semibold text-default-500">{{ overview.teamBreakdowns.length }} teams</span>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div *ngFor="let team of overview.teamBreakdowns" class="p-4 rounded-2xl bg-content2/50 border border-divider space-y-3">
            <div class="flex items-center justify-between">
              <div>
                <h4 class="text-xs font-bold text-foreground">{{ team.teamName }}</h4>
                <span class="text-[10px] text-default-400 font-mono">{{ team.costCenter || 'N/A' }}</span>
              </div>
              <span class="text-[10px] font-bold px-2 py-0.5 rounded-full"
                    [ngClass]="team.efficiencyScorePercent >= 70 ? 'bg-success/10 text-success' : 'bg-warning/10 text-warning'">
                {{ team.efficiencyScorePercent | number:'1.0-0' }}% eff.
              </span>
            </div>

            <div class="space-y-1">
              <div class="flex justify-between text-[11px]">
                <span class="text-default-500">Spend:</span>
                <span class="font-bold text-foreground">\${{ team.monthlyAllocatedCost | number:'1.2-2' }}/mo</span>
              </div>
              <div class="flex justify-between text-[11px]">
                <span class="text-default-500">Waste:</span>
                <span class="font-bold text-danger">\${{ team.monthlyWastedCost | number:'1.2-2' }}/mo</span>
              </div>
              <div class="flex justify-between text-[11px]">
                <span class="text-default-500">Monthly Savings:</span>
                <span class="font-bold text-emerald-500">\${{ team.monthlyPotentialSavings | number:'1.2-2' }}/mo</span>
              </div>
            </div>

            <!-- Mini Progress Bar -->
            <div class="w-full bg-content3 rounded-full h-1.5 overflow-hidden">
              <div
                class="bg-primary h-full rounded-full"
                [style.width.%]="team.costSharePercent"
                title="Fleet Cost Share: {{ team.costSharePercent }}%"
              ></div>
            </div>
            <div class="text-[10px] text-default-400 text-right">
              {{ team.namespaceCount }} namespace(s) &bull; {{ team.costSharePercent }}% of spend
            </div>
          </div>
        </div>
      </div>

      <!-- Rightsizing Recommendations Table -->
      <div class="heroui-card p-6 space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-divider">
          <div>
            <h3 class="text-base font-bold text-foreground">Namespace Rightsizing & Waste Quantification</h3>
            <p class="text-xs text-default-400 mt-0.5">
              Ranked by highest monthly savings potential. Click "Inspect Quota" to view suggested Kubernetes YAML patch.
            </p>
          </div>
          <span class="text-xs text-default-400">
            Showing {{ filteredRecommendations.length }} of {{ recommendations.length }} namespaces
          </span>
        </div>

        <div class="w-full overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-3 px-3">Namespace & Cluster</th>
                <th class="py-3 px-3">Team</th>
                <th class="py-3 px-3 text-right">CPU Req / Used</th>
                <th class="py-3 px-3 text-right">RAM Req / Used</th>
                <th class="py-3 px-3 text-right">Monthly Spend</th>
                <th class="py-3 px-3 text-right">Identified Waste</th>
                <th class="py-3 px-3 text-center">Status</th>
                <th class="py-3 px-3 text-right">Savings / mo</th>
                <th class="py-3 px-3 text-center">Action</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let item of filteredRecommendations" class="hover:bg-content2/50 transition-colors">
                <!-- Namespace & Cluster -->
                <td class="py-3 px-3">
                  <div class="font-semibold text-foreground">{{ item.namespaceName }}</div>
                  <div class="text-[10px] text-default-400 flex items-center gap-1.5">
                    <span>{{ item.clusterName }}</span>
                    <span>&bull;</span>
                    <span class="px-1 py-0.2 rounded bg-content3 text-[9px] font-mono">{{ item.environment }}</span>
                  </div>
                </td>

                <!-- Team -->
                <td class="py-3 px-3">
                  <div class="font-medium text-foreground">{{ item.teamName }}</div>
                  <div class="text-[10px] text-default-400 font-mono">{{ item.costCenter || 'N/A' }}</div>
                </td>

                <!-- CPU Req / Used -->
                <td class="py-3 px-3 text-right">
                  <div class="font-mono text-foreground font-semibold">
                    {{ item.avgCpuRequestCores | number:'1.1-1' }} / {{ item.avgCpuUsageCores | number:'1.1-1' }} c
                  </div>
                  <div class="text-[10px]" [ngClass]="item.cpuEfficiencyPercent < 60 ? 'text-danger font-medium' : 'text-default-400'">
                    {{ item.cpuEfficiencyPercent | number:'1.0-0' }}% eff
                  </div>
                </td>

                <!-- RAM Req / Used -->
                <td class="py-3 px-3 text-right">
                  <div class="font-mono text-foreground font-semibold">
                    {{ item.avgMemoryRequestGb | number:'1.1-1' }} / {{ item.avgMemoryUsageGb | number:'1.1-1' }} Gi
                  </div>
                  <div class="text-[10px]" [ngClass]="item.memoryEfficiencyPercent < 60 ? 'text-danger font-medium' : 'text-default-400'">
                    {{ item.memoryEfficiencyPercent | number:'1.0-0' }}% eff
                  </div>
                </td>

                <!-- Monthly Spend -->
                <td class="py-3 px-3 text-right font-mono font-semibold text-foreground">
                  \${{ item.monthlyAllocatedCost | number:'1.2-2' }}
                </td>

                <!-- Identified Waste -->
                <td class="py-3 px-3 text-right font-mono font-bold"
                    [ngClass]="item.monthlyWastedCost > 50 ? 'text-danger' : 'text-amber-500'">
                  \${{ item.monthlyWastedCost | number:'1.2-2' }}
                </td>

                <!-- Rating Status Badge -->
                <td class="py-3 px-3 text-center">
                  <span class="px-2 py-0.5 rounded-full text-[10px] font-bold"
                    [ngClass]="{
                      'bg-danger/10 text-danger border border-danger/20': item.rating === 'SEVERE_WASTE',
                      'bg-amber-500/10 text-amber-500 border border-amber-500/20': item.rating === 'OVER_PROVISIONED',
                      'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20': item.rating === 'OPTIMAL',
                      'bg-blue-500/10 text-blue-500 border border-blue-500/20': item.rating === 'ACCEPTABLE',
                      'bg-purple-500/10 text-purple-500 border border-purple-500/20': item.rating === 'UNDER_PROVISIONED'
                    }"
                  >
                    {{ item.rating.replace('_', ' ') }}
                  </span>
                </td>

                <!-- Monthly Savings -->
                <td class="py-3 px-3 text-right font-mono font-bold text-emerald-500">
                  +\${{ item.monthlyPotentialSavings | number:'1.2-2' }}
                </td>

                <!-- Action Button -->
                <td class="py-3 px-3 text-center">
                  <button
                    type="button"
                    (click)="selectedRecommendation = item"
                    class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-xs font-semibold px-2.5 py-1 text-primary cursor-pointer"
                  >
                    Inspect Quota
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Rightsizing Quota Patch Modal -->
      <div
        *ngIf="selectedRecommendation"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
      >
        <div class="heroui-card w-full max-w-2xl p-6 space-y-5 bg-background shadow-2xl border border-divider">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <div>
              <div class="flex items-center gap-2">
                <h3 class="text-base font-bold text-foreground">Rightsizing Remediation Quota</h3>
                <span class="px-2 py-0.5 text-[10px] font-bold rounded-full bg-primary/10 text-primary">
                  {{ selectedRecommendation.action.replace('_', ' ') }}
                </span>
              </div>
              <p class="text-xs text-default-400 mt-1">
                Namespace: <strong class="text-foreground">{{ selectedRecommendation.namespaceName }}</strong> &bull;
                Cluster: <strong class="text-foreground">{{ selectedRecommendation.clusterName }}</strong>
              </p>
            </div>
            <button
              type="button"
              (click)="selectedRecommendation = null"
              class="w-7 h-7 rounded-lg flex items-center justify-center text-default-400 hover:text-foreground hover:bg-content2"
            >
              ✕
            </button>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div class="p-2.5 rounded-xl bg-content2 border border-divider">
              <span class="block text-default-400 text-[10px]">Current CPU Req</span>
              <strong class="text-foreground">{{ selectedRecommendation.avgCpuRequestCores | number:'1.1-1' }} Cores</strong>
            </div>
            <div class="p-2.5 rounded-xl bg-content2 border border-divider">
              <span class="block text-default-400 text-[10px]">Target CPU Req</span>
              <strong class="text-emerald-500 font-bold">{{ selectedRecommendation.recommendedCpuRequestCores | number:'1.2-2' }} Cores</strong>
            </div>
            <div class="p-2.5 rounded-xl bg-content2 border border-divider">
              <span class="block text-default-400 text-[10px]">Current RAM Req</span>
              <strong class="text-foreground">{{ selectedRecommendation.avgMemoryRequestGb | number:'1.1-1' }} GiB</strong>
            </div>
            <div class="p-2.5 rounded-xl bg-content2 border border-divider">
              <span class="block text-default-400 text-[10px]">Target RAM Req</span>
              <strong class="text-emerald-500 font-bold">{{ selectedRecommendation.recommendedMemoryRequestGb | number:'1.2-2' }} GiB</strong>
            </div>
          </div>

          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="text-xs font-semibold text-default-400">Kubernetes ResourceQuota Manifest:</span>
              <button
                type="button"
                (click)="copyYaml(selectedRecommendation.suggestedResourceQuotaYaml)"
                class="text-xs text-primary hover:underline font-medium"
              >
                {{ copied ? 'Copied!' : 'Copy YAML' }}
              </button>
            </div>
            <pre class="p-4 rounded-xl bg-content2 border border-divider text-xs font-mono text-foreground overflow-x-auto select-all max-h-56">{{ selectedRecommendation.suggestedResourceQuotaYaml }}</pre>
          </div>

          <div class="flex items-center justify-between pt-2">
            <div class="text-xs font-medium text-emerald-500">
              Estimated Monthly Savings: \${{ selectedRecommendation.monthlyPotentialSavings | number:'1.2-2' }}
            </div>
            <button
              type="button"
              (click)="selectedRecommendation = null"
              class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-xs font-semibold px-4 py-2 text-foreground"
            >
              Close
            </button>
          </div>
        </div>
      </div>

      <!-- Pricing Rates Modal -->
      <div
        *ngIf="showPricingModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
      >
        <div class="heroui-card w-full max-w-md p-6 space-y-4 bg-background shadow-2xl border border-divider">
          <div class="flex items-center justify-between pb-3 border-b border-divider">
            <h3 class="text-base font-bold text-foreground">Unit Cost Settings</h3>
            <button
              type="button"
              (click)="showPricingModal = false"
              class="w-7 h-7 rounded-lg flex items-center justify-center text-default-400 hover:text-foreground hover:bg-content2"
            >
              ✕
            </button>
          </div>

          <div class="space-y-3 text-xs" *ngIf="pricingForm">
            <div>
              <label class="block text-default-500 mb-1">CPU Hourly Rate (\$/core/hour):</label>
              <input
                type="number"
                step="0.005"
                [(ngModel)]="pricingForm.cpuHourlyRate"
                class="w-full px-3 py-2 rounded-xl bg-content2 border border-divider text-foreground outline-none focus:border-primary"
              />
            </div>
            <div>
              <label class="block text-default-500 mb-1">Memory Hourly Rate (\$/GB RAM/hour):</label>
              <input
                type="number"
                step="0.001"
                [(ngModel)]="pricingForm.memoryHourlyRate"
                class="w-full px-3 py-2 rounded-xl bg-content2 border border-divider text-foreground outline-none focus:border-primary"
              />
            </div>
            <div>
              <label class="block text-default-500 mb-1">Storage Monthly Rate (\$/GB/month):</label>
              <input
                type="number"
                step="0.01"
                [(ngModel)]="pricingForm.storageMonthlyRate"
                class="w-full px-3 py-2 rounded-xl bg-content2 border border-divider text-foreground outline-none focus:border-primary"
              />
            </div>
          </div>

          <div class="flex items-center justify-end gap-2 pt-3 border-t border-divider">
            <button
              type="button"
              (click)="showPricingModal = false"
              class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-xs font-semibold px-4 py-2 text-foreground"
            >
              Cancel
            </button>
            <button
              type="button"
              *ngIf="auth.hasRole('ADMIN')"
              (click)="savePricing()"
              class="heroui-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold px-4 py-2"
            >
              Save Pricing
            </button>
          </div>
        </div>
      </div>

    </div>
  `
})
export class FinOpsComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  readonly quickRanges = [7, 30, 90];
  activeRange: number | null = 30;
  from = daysAgo(30);
  to = isoDay(new Date());
  environment: Environment | '' = '';
  environments: Environment[] = ['PRODUCTION', 'STAGING', 'DEVELOPMENT'];
  ratingFilter: string = '';

  overview: FinOpsOverview | null = null;
  recommendations: FinOpsNamespaceRecommendation[] = [];
  filteredRecommendations: FinOpsNamespaceRecommendation[] = [];
  selectedRecommendation: FinOpsNamespaceRecommendation | null = null;

  showPricingModal = false;
  pricingForm: FinOpsPricingConfig | null = null;
  error: string | null = null;
  copied = false;

  ngOnInit(): void {
    this.load();
  }

  useRange(days: number): void {
    this.activeRange = days;
    this.from = daysAgo(days);
    this.to = isoDay(new Date());
    this.load();
  }

  load(): void {
    this.error = null;
    this.portalService.getFinOpsOverview(this.from, this.to, this.environment || undefined).subscribe({
      next: (res: FinOpsOverview) => {
        this.overview = res;
      },
      error: (err: any) => {
        this.error = 'Failed to load FinOps overview: ' + (err.error?.message || err.message);
      }
    });

    this.portalService.getFinOpsRecommendations(this.from, this.to, this.environment || undefined).subscribe({
      next: (res: FinOpsNamespaceRecommendation[]) => {
        this.recommendations = res;
        this.filterRecommendations();
      },
      error: (err: any) => {
        this.error = 'Failed to load rightsizing recommendations: ' + (err.error?.message || err.message);
      }
    });
  }

  filterRecommendations(): void {
    if (!this.ratingFilter) {
      this.filteredRecommendations = [...this.recommendations];
    } else {
      this.filteredRecommendations = this.recommendations.filter(r => r.rating === this.ratingFilter);
    }
  }

  openPricingModal(): void {
    this.portalService.getFinOpsPricing().subscribe({
      next: (p: FinOpsPricingConfig) => {
        this.pricingForm = { ...p };
        this.showPricingModal = true;
      }
    });
  }

  savePricing(): void {
    if (!this.pricingForm) return;
    this.portalService.updateFinOpsPricing(this.pricingForm).subscribe({
      next: (saved: FinOpsPricingConfig) => {
        this.showPricingModal = false;
        this.load();
      },
      error: (err: any) => {
        this.error = 'Failed to update pricing: ' + (err.error?.message || err.message);
      }
    });
  }

  exportCsv(): void {
    this.portalService.downloadFinOpsCsv(this.from, this.to, this.environment || undefined).subscribe({
      next: (res: HttpResponse<Blob>) => saveDownload(res, `finops-rightsizing-${this.from}-to-${this.to}.csv`),
      error: (err: any) => {
        this.error = 'Export failed: ' + (err.error?.message || err.message);
      }
    });
  }

  copyYaml(yaml: string): void {
    navigator.clipboard?.writeText(yaml).then(() => {
      this.copied = true;
      setTimeout(() => (this.copied = false), 2000);
    });
  }
}

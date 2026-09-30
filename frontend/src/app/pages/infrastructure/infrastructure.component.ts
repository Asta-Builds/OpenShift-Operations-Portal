import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import { InfrastructureTopology, InventoryImportResult, InventoryRow, TopologyNode } from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';
import { TopologyGraphComponent } from './topology-graph.component';

@Component({
  selector: 'app-infrastructure',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent, TopologyGraphComponent],
  template: `
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">Infrastructure & Topologie</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">Multi-Clusters</span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Graphe topologique interactif D3.js et corrélation matérielle physique issue de l'inventaire <code>spec.providerID</code>.
          </p>
        </div>

        <!-- View Switcher Tabs -->
        <div class="flex items-center bg-content2 p-1 rounded-xl border border-divider">
          <button
            (click)="activeTab = 'd3-graph'"
            [class]="activeTab === 'd3-graph' ? 'bg-primary text-primary-foreground shadow-glow-primary font-bold' : 'text-default-600 dark:text-default-400 hover:text-foreground font-medium'"
            class="px-3.5 py-1.5 rounded-lg text-xs flex items-center gap-2 transition-all cursor-pointer"
          >
            <app-icon name="layers" [size]="14"></app-icon>
            <span>Topologie D3</span>
            <span class="px-1.5 py-0.2 rounded-full text-[9px] bg-secondary text-white font-semibold">Interactif</span>
          </button>
          <button
            (click)="activeTab = 'correlation-matrix'"
            [class]="activeTab === 'correlation-matrix' ? 'bg-primary text-primary-foreground shadow-glow-primary font-bold' : 'text-default-600 dark:text-default-400 hover:text-foreground font-medium'"
            class="px-3.5 py-1.5 rounded-lg text-xs flex items-center gap-2 transition-all cursor-pointer"
          >
            <app-icon name="hard-drive" [size]="14"></app-icon>
            <span>Matrice Matérielle</span>
          </button>
        </div>
      </div>

      <!-- D3 Interactive Graph View -->
      <div *ngIf="activeTab === 'd3-graph'">
        <app-topology-graph></app-topology-graph>
      </div>

      <!-- Physical Correlation Matrix View -->
      <div *ngIf="activeTab === 'correlation-matrix'" class="space-y-6">
        <div class="rounded-xl border border-danger/30 bg-danger/10 text-danger text-xs px-4 py-3" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="topology as t">
        <!-- Summary -->
        <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
          <div class="heroui-card p-4">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-default-400">Nodes</div>
            <div class="text-2xl font-bold text-foreground mt-1">{{ t.summary.nodes }}</div>
            <div class="text-xs text-default-400">in the latest snapshots</div>
          </div>
          <div class="heroui-card p-4">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-default-400">In inventory</div>
            <div class="text-2xl font-bold text-success mt-1">{{ t.summary.matched }}</div>
            <div class="text-xs text-default-400">host known</div>
          </div>
          <div class="heroui-card p-4">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-default-400">Public cloud</div>
            <div class="text-2xl font-bold text-foreground mt-1">{{ t.summary.cloud }}</div>
            <div class="text-xs text-default-400">provider-owned hardware</div>
          </div>
          <div class="heroui-card p-4" [ngClass]="t.summary.notInInventory > 0 ? 'border-warning/40 bg-warning/5' : ''">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-default-400">Not in inventory</div>
            <div class="text-2xl font-bold mt-1" [ngClass]="t.summary.notInInventory > 0 ? 'text-warning' : 'text-foreground'">{{ t.summary.notInInventory }}</div>
            <div class="text-xs text-default-400">host unknown</div>
          </div>
          <div class="heroui-card p-4">
            <div class="text-[10px] font-semibold uppercase tracking-wider text-default-400">No providerID</div>
            <div class="text-2xl font-bold text-foreground mt-1">{{ t.summary.noProviderId }}</div>
            <div class="text-xs text-default-400">UPI / platform none</div>
          </div>
        </div>

        <!-- Hypervisor clusters -> hosts -> nodes -->
        <div class="heroui-card p-6 space-y-4" *ngFor="let hc of t.hypervisorClusters">
          <div class="flex flex-wrap items-baseline gap-2">
            <app-icon name="layers" [size]="16"></app-icon>
            <h3 class="text-base font-bold text-foreground">{{ hc.hypervisorCluster || 'Hosts without a cluster' }}</h3>
            <span class="text-xs text-default-400">{{ hc.datacenter || 'datacenter unknown' }} · {{ hc.hosts.length }} hosts · {{ vmCount(hc.hosts) }} VMs</span>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-3">
            <div class="rounded-xl border border-divider bg-content2/40 p-3" *ngFor="let host of hc.hosts">
              <div class="flex items-center justify-between gap-2">
                <span class="font-mono text-xs font-bold text-foreground truncate" [title]="host.hypervisorHost">{{ host.hypervisorHost }}</span>
                <span class="heroui-badge text-[10px] bg-content3 text-default-600" *ngIf="host.inventorySource === 'SIMULATOR'">simulated</span>
              </div>
              <div class="text-[11px] text-default-400 mt-0.5">{{ hardware(host.sockets, host.physicalCores, host.threadsPerCore) }}</div>
              <div class="flex flex-wrap gap-1.5 mt-2">
                <a *ngFor="let n of host.nodes" [routerLink]="['/clusters', n.clusterId]"
                   class="heroui-badge text-[10px] font-mono" [ngClass]="n.role === 'WORKER' ? 'bg-primary/10 text-primary' : 'bg-content3 text-default-600'"
                   [title]="n.clusterName + ' / ' + n.nodeName + ' · ' + n.cpuCores + ' vCPU'">{{ n.nodeName }}</a>
              </div>
            </div>
          </div>
        </div>

        <!-- Bare metal -->
        <div class="heroui-card p-6 space-y-3" *ngIf="t.bareMetal.length > 0">
          <h3 class="text-base font-bold text-foreground">Bare metal</h3>
          <div class="w-full overflow-x-auto">
            <table class="w-full text-left text-xs border-collapse">
              <thead>
                <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                  <th class="py-2 px-3">Datacenter</th><th class="py-2 px-3">Node</th><th class="py-2 px-3">Cluster</th>
                  <th class="py-2 px-3">Machine</th><th class="py-2 px-3">Hardware</th><th class="py-2 px-3 text-right">Logical CPUs</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-divider/40">
                <ng-container *ngFor="let dc of t.bareMetal">
                  <tr *ngFor="let n of dc.nodes">
                    <td class="py-2 px-3 text-default-600">{{ dc.datacenter || '—' }}</td>
                    <td class="py-2 px-3 font-mono font-semibold text-foreground">{{ n.nodeName }}</td>
                    <td class="py-2 px-3"><a class="text-primary hover:underline" [routerLink]="['/clusters', n.clusterId]">{{ n.clusterName }}</a></td>
                    <td class="py-2 px-3 font-mono text-default-500">{{ n.instanceKey }}</td>
                    <td class="py-2 px-3 text-default-600">{{ hardware(n.sockets, n.physicalCores, n.threadsPerCore) }}</td>
                    <td class="py-2 px-3 text-right font-semibold">{{ n.cpuCores }}</td>
                  </tr>
                </ng-container>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Cloud -->
        <div class="heroui-card p-6 space-y-3" *ngIf="t.cloud.length > 0">
          <h3 class="text-base font-bold text-foreground">Public cloud</h3>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div class="rounded-xl border border-divider bg-content2/40 p-3" *ngFor="let z of t.cloud">
              <div class="text-xs font-bold text-foreground">{{ z.providerType }} · {{ z.zone || 'zone unknown' }}</div>
              <div class="text-[11px] text-default-400">{{ z.nodes.length }} instances</div>
              <div class="flex flex-wrap gap-1.5 mt-2">
                <a *ngFor="let n of z.nodes" [routerLink]="['/clusters', n.clusterId]" class="heroui-badge text-[10px] font-mono bg-content3 text-default-600"
                   [title]="n.instanceKey || ''">{{ n.nodeName }}</a>
              </div>
            </div>
          </div>
        </div>

        <!-- Unmatched -->
        <div class="heroui-card p-6 space-y-3" *ngIf="t.notInInventory.length + t.noProviderId.length > 0">
          <div>
            <h3 class="text-base font-bold text-foreground">Nodes without a known host</h3>
            <p class="text-xs text-default-400 mt-0.5">Add these instance keys to the inventory to place them; nothing is guessed meanwhile.</p>
          </div>
          <div class="w-full overflow-x-auto">
            <table class="w-full text-left text-xs border-collapse">
              <thead>
                <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                  <th class="py-2 px-3">Cluster</th><th class="py-2 px-3">Node</th><th class="py-2 px-3">Platform</th>
                  <th class="py-2 px-3">Instance key</th><th class="py-2 px-3">Why</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-divider/40">
                <tr *ngFor="let n of unmatched(t)">
                  <td class="py-2 px-3"><a class="text-primary hover:underline" [routerLink]="['/clusters', n.clusterId]">{{ n.clusterName }}</a></td>
                  <td class="py-2 px-3 font-mono font-semibold text-foreground">{{ n.nodeName }}</td>
                  <td class="py-2 px-3 text-default-600">{{ n.providerType }}</td>
                  <td class="py-2 px-3 font-mono text-default-500 break-all">{{ n.instanceKey || '—' }}</td>
                  <td class="py-2 px-3">
                    <span class="heroui-badge text-[10px]" [ngClass]="n.status === 'NOT_IN_INVENTORY' ? 'bg-warning/15 text-warning' : 'bg-content3 text-default-600'">
                      {{ n.status === 'NOT_IN_INVENTORY' ? 'not in inventory' : 'no providerID' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="heroui-card p-6 text-center text-xs text-default-400" *ngIf="t.summary.nodes === 0">
          No node data has been collected yet. Live hubs do not report nodes until node collection (plan decision D2) is in place.
        </div>
      </ng-container>

      <!-- Inventory -->
      <div class="heroui-card p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-foreground">Inventory</h3>
          <p class="text-xs text-default-400 mt-0.5">
            {{ inventory.length }} rows<span *ngFor="let s of sourceCounts()"> · {{ s.source }} {{ s.count }}</span>.
            Files dropped into the import folder (<code>openshift.portal.inventory.import-dir</code>, one <code>&lt;source&gt;.csv</code> each) are loaded every hour.
          </p>
        </div>

        <div class="space-y-3" *ngIf="auth.hasRole('ADMIN')">
          <div class="text-xs text-default-500">
            CSV with a header row. Identify each machine by <code>provider_id</code> (a node providerID), or by
            <code>provider_type</code> and <code>instance_key</code>; optional columns: <code>hypervisor_host</code>,
            <code>hypervisor_cluster</code>, <code>datacenter</code>, <code>physical_sockets</code>, <code>physical_cores</code>,
            <code>threads_per_core</code>. A file with any error changes nothing.
          </div>
          <div class="flex flex-wrap items-end gap-3">
            <label class="flex flex-col gap-1 text-[10px] font-semibold uppercase tracking-wider text-default-400">
              Source
              <input [(ngModel)]="importSource" class="text-xs px-3 py-1.5 rounded-lg bg-content2 border border-divider text-foreground w-36">
            </label>
            <label class="flex flex-col gap-1 text-[10px] font-semibold uppercase tracking-wider text-default-400">
              File
              <input type="file" accept=".csv,text/csv" (change)="readFile($event)" class="text-xs text-default-600">
            </label>
            <label class="flex items-center gap-2 text-xs text-default-600">
              <input type="checkbox" [(ngModel)]="importReplace"> Full export: remove this source's rows missing from the file
            </label>
            <button type="button" class="heroui-btn bg-primary text-primary-foreground text-xs font-semibold px-4 py-2 disabled:opacity-50"
                    [disabled]="!importCsv || busy" (click)="runImport()">Import</button>
          </div>
          <div class="rounded-xl border border-success/30 bg-success/10 text-success text-xs px-4 py-3" *ngIf="importResult as r">
            {{ r.source }}: {{ r.inserted }} added, {{ r.updated }} updated, {{ r.removed }} removed from {{ r.rows }} rows.
            {{ r.matchedNodes }} current nodes now have a known host.
          </div>
          <div class="rounded-xl border border-danger/30 bg-danger/10 text-danger text-xs px-4 py-3 space-y-1" *ngIf="importErrors.length > 0">
            <div class="font-semibold">The file was rejected; nothing changed.</div>
            <div *ngFor="let e of importErrors">{{ e }}</div>
          </div>
        </div>
      </div>
      </div>
    </div>
  `
})
export class InfrastructureComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  activeTab: 'd3-graph' | 'correlation-matrix' = 'd3-graph';
  topology: InfrastructureTopology | null = null;
  inventory: InventoryRow[] = [];
  error = '';

  importSource = 'CMDB';
  importReplace = false;
  importCsv = '';
  importResult: InventoryImportResult | null = null;
  importErrors: string[] = [];
  busy = false;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    forkJoin({ topology: this.portalService.getInfrastructureTopology(), inventory: this.portalService.getInventory() }).subscribe({
      next: ({ topology, inventory }) => {
        this.topology = topology;
        this.inventory = inventory;
      },
      error: (err) => (this.error = `Could not load the infrastructure view (HTTP ${err.status}).`)
    });
  }

  vmCount(hosts: { nodes: TopologyNode[] }[]): number {
    return hosts.reduce((sum, host) => sum + host.nodes.length, 0);
  }

  unmatched(t: InfrastructureTopology): TopologyNode[] {
    return [...t.notInInventory, ...t.noProviderId];
  }

  /** "2 sockets · 48 cores · 2 threads/core", leaving out what the inventory does not say. */
  hardware(sockets: number | null, cores: number | null, threads: number | null): string {
    const parts = [
      sockets ? `${sockets} socket${sockets > 1 ? 's' : ''}` : null,
      cores ? `${cores} cores` : null,
      threads ? `${threads} threads/core` : null
    ].filter((part) => part !== null);
    return parts.length > 0 ? parts.join(' · ') : 'hardware not in inventory';
  }

  sourceCounts(): { source: string; count: number }[] {
    const counts = new Map<string, number>();
    this.inventory.forEach((row) => counts.set(row.source, (counts.get(row.source) ?? 0) + 1));
    return [...counts.entries()].map(([source, count]) => ({ source, count }));
  }

  readFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    this.importResult = null;
    this.importErrors = [];
    if (!file) {
      this.importCsv = '';
      return;
    }
    file.text().then((text) => (this.importCsv = text));
  }

  runImport(): void {
    this.busy = true;
    this.importResult = null;
    this.importErrors = [];
    this.portalService.importInventory(this.importCsv, this.importSource.trim(), this.importReplace).subscribe({
      next: (result) => {
        this.busy = false;
        this.importResult = result;
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.importErrors = err.error?.errors ?? [err.error?.message ?? `Import failed (HTTP ${err.status}).`];
      }
    });
  }
}

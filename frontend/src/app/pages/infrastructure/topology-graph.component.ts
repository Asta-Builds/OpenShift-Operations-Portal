import {
  Component,
  OnInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  inject,
  ChangeDetectorRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import * as d3 from 'd3';
import { PortalService } from '../../services/portal.service';
import {
  TopologyGraph,
  GraphNode,
  GraphLink,
  GraphSummary,
  TopologyNodeType
} from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

interface SimulationNode extends GraphNode {
  x: number;
  y: number;
  vx?: number;
  vy?: number;
  fx?: number | null;
  fy?: number | null;
}

interface SimulationLink extends d3.SimulationLinkDatum<SimulationNode> {
  source: SimulationNode | string;
  target: SimulationNode | string;
  type: string;
  value: number;
}

@Component({
  selector: 'app-topology-graph',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  template: `
    <div class="space-y-4">
      <!-- Header & Stats Summary -->
      <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
              <app-icon name="layers" [size]="20" className="text-primary"></app-icon>
              Cartographie Topologique Multi-Clusters D3
            </h2>
            <span class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-secondary/15 text-secondary border border-secondary/30 flex items-center gap-1.5">
              <span class="w-1.5 h-1.5 rounded-full bg-secondary animate-ping"></span>
              Graphe de Force Interactif
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            Visualisation temps réel de l'arborescence globale : Hubs ACM &rarr; Clusters OpenShift &rarr; Noeuds Physiques/Workers &rarr; Namespaces applicatifs FinOps.
          </p>
        </div>

        <!-- Quick Summary Cards -->
        <div class="flex flex-wrap items-center gap-2.5" *ngIf="summary">
          <div class="px-3 py-1.5 rounded-xl bg-content2 border border-divider flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-purple-500 shadow-glow-purple"></span>
            <div class="text-[11px] leading-tight">
              <span class="font-bold text-foreground">{{ summary.totalHubs }}</span>
              <span class="text-default-400 ml-1">Hubs</span>
            </div>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-content2 border border-divider flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-blue-500 shadow-glow-blue"></span>
            <div class="text-[11px] leading-tight">
              <span class="font-bold text-foreground">{{ summary.totalClusters }}</span>
              <span class="text-default-400 ml-1">Clusters</span>
            </div>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-content2 border border-divider flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-slate-400"></span>
            <div class="text-[11px] leading-tight">
              <span class="font-bold text-foreground">{{ summary.totalNodes }}</span>
              <span class="text-default-400 ml-1">Noeuds</span>
            </div>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-content2 border border-divider flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            <div class="text-[11px] leading-tight">
              <span class="font-bold text-foreground">{{ summary.totalNamespaces }}</span>
              <span class="text-default-400 ml-1">Namespaces</span>
            </div>
          </div>
          <div class="px-3 py-1.5 rounded-xl bg-primary/10 border border-primary/20 flex items-center gap-2">
            <app-icon name="cpu" [size]="14" className="text-primary"></app-icon>
            <div class="text-[11px] leading-tight">
              <span class="font-bold text-primary">{{ summary.totalCores | number:'1.0-0' }}</span>
              <span class="text-default-400 ml-1">vCPUs</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Controls Toolbar -->
      <div class="heroui-card p-3 flex flex-wrap items-center justify-between gap-3">
        <!-- Left: Search & Filter Toggles -->
        <div class="flex flex-wrap items-center gap-2 sm:gap-3 flex-1">
          <!-- Search box -->
          <div class="relative min-w-[200px] max-w-xs flex-1">
            <app-icon name="search" [size]="14" className="absolute left-3 top-1/2 -translate-y-1/2 text-default-400"></app-icon>
            <input
              type="text"
              [(ngModel)]="searchQuery"
              (ngModelChange)="onSearchChange()"
              placeholder="Rechercher noeud, cluster, namespace..."
              class="w-full pl-8 pr-3 py-1.5 text-xs bg-content3/50 border border-divider rounded-lg focus:outline-none focus:border-primary text-foreground placeholder:text-default-400"
            />
            <button
              *ngIf="searchQuery"
              (click)="clearSearch()"
              class="absolute right-2.5 top-1/2 -translate-y-1/2 text-default-400 hover:text-foreground text-xs"
            >
              ✕
            </button>
          </div>

          <!-- Type Visibility Toggles -->
          <div class="flex items-center gap-1 bg-content2/80 p-1 rounded-lg border border-divider text-xs">
            <button
              (click)="toggleTypeFilter('HUB')"
              [class]="typeFilters.HUB ? 'bg-purple-600/20 text-purple-400 font-semibold border-purple-500/40' : 'text-default-400 opacity-60'"
              class="px-2.5 py-1 rounded-md border border-transparent transition-all flex items-center gap-1.5"
              title="Afficher/Masquer les Hubs ACM"
            >
              <span class="w-2 h-2 rounded-full bg-purple-500"></span>
              Hubs
            </button>
            <button
              (click)="toggleTypeFilter('CLUSTER')"
              [class]="typeFilters.CLUSTER ? 'bg-blue-600/20 text-blue-400 font-semibold border-blue-500/40' : 'text-default-400 opacity-60'"
              class="px-2.5 py-1 rounded-md border border-transparent transition-all flex items-center gap-1.5"
              title="Afficher/Masquer les Clusters OpenShift"
            >
              <span class="w-2 h-2 rounded-full bg-blue-500"></span>
              Clusters
            </button>
            <button
              (click)="toggleTypeFilter('NODE')"
              [class]="typeFilters.NODE ? 'bg-slate-600/20 text-slate-300 font-semibold border-slate-500/40' : 'text-default-400 opacity-60'"
              class="px-2.5 py-1 rounded-md border border-transparent transition-all flex items-center gap-1.5"
              title="Afficher/Masquer les Noeuds physiques et workers"
            >
              <span class="w-2 h-2 rounded-full bg-slate-400"></span>
              Noeuds
            </button>
            <button
              (click)="toggleTypeFilter('NAMESPACE')"
              [class]="typeFilters.NAMESPACE ? 'bg-emerald-600/20 text-emerald-400 font-semibold border-emerald-500/40' : 'text-default-400 opacity-60'"
              class="px-2.5 py-1 rounded-md border border-transparent transition-all flex items-center gap-1.5"
              title="Afficher/Masquer les Namespaces FinOps"
            >
              <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
              Namespaces
            </button>
          </div>

          <!-- Environment Filter -->
          <select
            [(ngModel)]="selectedEnv"
            (change)="applyFilters()"
            class="px-2.5 py-1.5 text-xs bg-content3/50 border border-divider rounded-lg text-foreground focus:outline-none focus:border-primary"
          >
            <option value="ALL">Tous les Environnements</option>
            <option value="PRODUCTION">Production</option>
            <option value="STAGING">Staging</option>
            <option value="DEVELOPMENT">Development</option>
            <option value="LABS">Laboratoires / Sandbox</option>
          </select>
        </div>

        <!-- Right: Canvas Controls -->
        <div class="flex items-center gap-1.5">
          <button
            (click)="zoomIn()"
            class="p-1.5 rounded-lg bg-content2 hover:bg-content3 text-default-400 hover:text-foreground border border-divider transition-all"
            title="Zoom Avant"
          >
            <app-icon name="plus" [size]="14"></app-icon>
          </button>
          <button
            (click)="zoomOut()"
            class="p-1.5 rounded-lg bg-content2 hover:bg-content3 text-default-400 hover:text-foreground border border-divider transition-all"
            title="Zoom Arrière"
          >
            <app-icon name="minus" [size]="14"></app-icon>
          </button>
          <button
            (click)="resetView()"
            class="p-1.5 rounded-lg bg-content2 hover:bg-content3 text-default-400 hover:text-foreground border border-divider transition-all"
            title="Centrer la Vue"
          >
            <app-icon name="crosshair" [size]="14"></app-icon>
          </button>
          <button
            (click)="reheatSimulation()"
            class="p-1.5 rounded-lg bg-content2 hover:bg-content3 text-default-400 hover:text-foreground border border-divider transition-all"
            title="Réorganiser la Force Graphique"
          >
            <app-icon name="refresh" [size]="14"></app-icon>
          </button>
        </div>
      </div>

      <!-- Main Visualization Stage -->
      <div class="relative w-full h-[680px] rounded-2xl border border-divider bg-[#07090e] overflow-hidden shadow-2xl flex">
        <!-- Loading Overlay -->
        <div *ngIf="loading" class="absolute inset-0 z-30 flex flex-col items-center justify-center bg-background/80 backdrop-blur-sm gap-3">
          <div class="w-10 h-10 border-3 border-primary border-t-transparent rounded-full animate-spin"></div>
          <p class="text-xs font-medium text-default-400">Génération de la topologie D3 en cours...</p>
        </div>

        <!-- D3 SVG Canvas Container -->
        <div #chartContainer class="w-full h-full relative cursor-grab active:cursor-grabbing">
          <!-- Canvas SVG is appended here by D3 -->
        </div>

        <!-- Floating Interactive Legend -->
        <div class="absolute bottom-4 left-4 z-10 bg-content1/80 backdrop-blur-md border border-divider/60 rounded-xl p-3 text-[11px] shadow-xl space-y-2 pointer-events-auto">
          <div class="font-bold text-foreground tracking-wide uppercase text-[9px] text-default-400 mb-1">Légende Topologique</div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1.5">
            <div class="flex items-center gap-2">
              <span class="w-3 h-3 rounded-full bg-purple-600 border border-purple-300 ring-2 ring-purple-500/30"></span>
              <span class="text-default-300">Hub ACM Principal</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-3 h-3 rounded-full bg-blue-600 border border-blue-300"></span>
              <span class="text-default-300">Cluster Prod</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-amber-500"></span>
              <span class="text-default-300">Cluster Staging/Dev</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-slate-400"></span>
              <span class="text-default-300">Noeud Worker</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
              <span class="text-emerald-400 font-medium">Namespace A/B (Optimal)</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-rose-500"></span>
              <span class="text-rose-400 font-medium">Namespace D/F (Gaspillage)</span>
            </div>
          </div>
          <div class="text-[10px] text-default-400 pt-1 border-t border-divider/40">
            Astuce : Cliquez sur un noeud pour l'inspecter, glissez-le pour figer sa position.
          </div>
        </div>

        <!-- Floating Selected Node Inspector Drawer -->
        <div
          *ngIf="selectedNode"
          class="absolute top-4 right-4 z-20 w-80 md:w-96 bg-content1/90 backdrop-blur-xl border border-divider/80 rounded-2xl p-4 shadow-2xl space-y-4 animate-in fade-in slide-in-from-right-4 duration-200"
        >
          <!-- Drawer Header -->
          <div class="flex items-start justify-between gap-2 border-b border-divider pb-3">
            <div class="flex items-center gap-2.5">
              <div
                class="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-xs"
                [ngClass]="getNodeColorClass(selectedNode)"
              >
                <app-icon [name]="getNodeIconName(selectedNode)" [size]="18"></app-icon>
              </div>
              <div>
                <span class="text-[10px] font-semibold tracking-wider uppercase text-default-400">{{ selectedNode.type }}</span>
                <h3 class="text-sm font-bold text-foreground truncate max-w-[200px]" [title]="selectedNode.label">
                  {{ selectedNode.label }}
                </h3>
              </div>
            </div>
            <button
              (click)="selectedNode = null"
              class="p-1 rounded-lg text-default-400 hover:text-foreground hover:bg-content2 transition-all"
            >
              ✕
            </button>
          </div>

          <!-- Drawer Body: Attributes -->
          <div class="space-y-3 text-xs">
            <div class="grid grid-cols-2 gap-2">
              <div class="bg-content2/50 p-2.5 rounded-xl border border-divider/40">
                <span class="text-[10px] text-default-400 uppercase font-semibold">Statut</span>
                <div class="font-semibold text-foreground mt-0.5 flex items-center gap-1.5">
                  <span class="w-1.5 h-1.5 rounded-full" [ngClass]="selectedNode.status === 'ACTIVE' || selectedNode.status === 'READY' ? 'bg-success' : 'bg-warning'"></span>
                  {{ selectedNode.status || 'READY' }}
                </div>
              </div>
              <div class="bg-content2/50 p-2.5 rounded-xl border border-divider/40">
                <span class="text-[10px] text-default-400 uppercase font-semibold">Environnement</span>
                <div class="font-semibold text-foreground mt-0.5">
                  {{ selectedNode.environment || 'Global / Hub' }}
                </div>
              </div>
            </div>

            <!-- FinOps Metrics if available -->
            <div *ngIf="selectedNode.type === 'NAMESPACE'" class="bg-content2/40 p-3 rounded-xl border border-divider/40 space-y-2">
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Score FinOps & Tier :</span>
                <span
                  class="px-2 py-0.5 rounded-md font-bold text-xs"
                  [ngClass]="selectedNode.rating === 'A' || selectedNode.rating === 'B' ? 'bg-success/20 text-success' : 'bg-danger/20 text-danger'"
                >
                  Grade {{ selectedNode.rating || 'N/A' }} ({{ selectedNode.efficiencyPercent | number:'1.0-1' }}%)
                </span>
              </div>
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Coût Mensuel :</span>
                <span class="font-mono font-bold text-foreground">{{ selectedNode.monthlyCost | currency:'EUR':'symbol':'1.0-0' }}/mois</span>
              </div>
              <div class="flex items-center justify-between text-[11px]" *ngIf="selectedNode.team">
                <span class="text-default-400">Équipe Propriétaire :</span>
                <span class="font-semibold text-primary">{{ selectedNode.team }}</span>
              </div>
            </div>

            <!-- Node / Hardware Metrics if available -->
            <div *ngIf="selectedNode.type === 'NODE'" class="bg-content2/40 p-3 rounded-xl border border-divider/40 space-y-2">
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Rôle Kubernetes :</span>
                <span class="font-mono font-bold uppercase text-foreground">{{ selectedNode.role || 'WORKER' }}</span>
              </div>
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Capacité CPU / RAM :</span>
                <span class="font-mono font-semibold text-foreground">{{ selectedNode.cpuCores }} Cores · {{ selectedNode.memoryGb }} GB</span>
              </div>
            </div>

            <!-- Cluster Metrics if available -->
            <div *ngIf="selectedNode.type === 'CLUSTER'" class="bg-content2/40 p-3 rounded-xl border border-divider/40 space-y-2">
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Version OpenShift :</span>
                <span class="font-mono font-semibold text-foreground">{{ selectedNode.metadata?.['version'] || '4.15.12' }}</span>
              </div>
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Région / Provider :</span>
                <span class="font-semibold text-foreground">{{ selectedNode.metadata?.['region'] || 'AWS eu-west-1' }}</span>
              </div>
              <div class="flex items-center justify-between text-[11px]">
                <span class="text-default-400">Noeuds Hébergés :</span>
                <span class="font-bold text-primary">{{ selectedNode.metadata?.['nodeCount'] || 12 }} noeuds</span>
              </div>
            </div>

            <!-- Metadata info -->
            <div *ngIf="selectedNode.parentId" class="text-[11px] text-default-400 flex items-center justify-between">
              <span>Parent Topology :</span>
              <span class="font-mono text-default-300 text-[10px]">{{ selectedNode.parentId }}</span>
            </div>
          </div>

          <!-- Quick Navigation Actions -->
          <div class="pt-2 border-t border-divider flex items-center gap-2">
            <a
              *ngIf="selectedNode.type === 'CLUSTER'"
              [routerLink]="['/clusters', selectedNode.metadata?.['clusterId'] || selectedNode.id]"
              class="w-full text-center py-2 px-3 bg-primary text-primary-foreground font-semibold rounded-xl text-xs hover:bg-primary/90 transition-all shadow-glow-primary"
            >
              Ouvrir Détails Cluster &rarr;
            </a>
            <a
              *ngIf="selectedNode.type === 'NAMESPACE'"
              routerLink="/finops"
              class="w-full text-center py-2 px-3 bg-emerald-600 text-white font-semibold rounded-xl text-xs hover:bg-emerald-500 transition-all shadow-glow-emerald"
            >
              Optimiser dans FinOps Engine &rarr;
            </a>
            <button
              *ngIf="selectedNode.type === 'NODE' || selectedNode.type === 'HUB'"
              (click)="unfixNode(selectedNode)"
              class="w-full text-center py-2 px-3 bg-content2 hover:bg-content3 text-foreground font-semibold rounded-xl text-xs transition-all border border-divider"
            >
              Libérer la position physique
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .shadow-glow-purple {
        box-shadow: 0 0 12px rgba(168, 85, 247, 0.4);
      }
      .shadow-glow-blue {
        box-shadow: 0 0 12px rgba(59, 130, 246, 0.4);
      }
      .shadow-glow-emerald {
        box-shadow: 0 0 12px rgba(16, 185, 129, 0.4);
      }
    `
  ]
})
export class TopologyGraphComponent implements OnInit, OnDestroy {
  @ViewChild('chartContainer', { static: false }) chartContainer!: ElementRef<HTMLDivElement>;

  private portalService = inject(PortalService);
  private cdr = inject(ChangeDetectorRef);

  rawGraph: TopologyGraph | null = null;
  summary: GraphSummary | null = null;
  loading = true;
  selectedNode: GraphNode | null = null;

  // Filters
  searchQuery = '';
  selectedEnv = 'ALL';
  typeFilters: Record<TopologyNodeType, boolean> = {
    HUB: true,
    CLUSTER: true,
    NODE: true,
    NAMESPACE: true,
    HOST: true
  };

  // D3 Instances
  private svg: any;
  private gZoom: any;
  private simulation: any;
  private zoomBehavior: any;
  private width = 1200;
  private height = 680;
  private resizeObserver: ResizeObserver | null = null;

  // Render elements references
  private linkSelection: any;
  private nodeSelection: any;

  ngOnInit(): void {
    this.loadTopology();
  }

  ngOnDestroy(): void {
    if (this.simulation) {
      this.simulation.stop();
    }
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
  }

  loadTopology(): void {
    this.loading = true;
    this.portalService.getTopologyGraph().subscribe({
      next: (graph) => {
        this.rawGraph = graph;
        this.summary = graph.summary;
        this.loading = false;
        this.cdr.detectChanges();
        // Give the DOM time to render container
        setTimeout(() => this.initD3Graph(), 50);
      },
      error: (err) => {
        console.error('Failed to load topology graph', err);
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private initD3Graph(): void {
    if (!this.chartContainer || !this.rawGraph) return;

    const element = this.chartContainer.nativeElement;
    this.width = element.clientWidth || 1200;
    this.height = element.clientHeight || 680;

    // Remove any previous SVG
    d3.select(element).selectAll('svg').remove();

    // Create Main SVG
    this.svg = d3
      .select(element)
      .append('svg')
      .attr('width', '100%')
      .attr('height', '100%')
      .attr('viewBox', `0 0 ${this.width} ${this.height}`)
      .attr('class', 'select-none');

    // Add SVG Filters for Glow Effects
    const defs = this.svg.append('defs');

    // Glow filter
    const filter = defs.append('filter').attr('id', 'glow').attr('x', '-50%').attr('y', '-50%').attr('width', '200%').attr('height', '200%');
    filter.append('feGaussianBlur').attr('stdDeviation', '4').attr('result', 'coloredBlur');
    const feMerge = filter.append('feMerge');
    feMerge.append('feMergeNode').attr('in', 'coloredBlur');
    feMerge.append('feMergeNode').attr('in', 'SourceGraphic');

    // Background Subtle Grid Pattern
    const pattern = defs
      .append('pattern')
      .attr('id', 'topo-grid')
      .attr('width', 40)
      .attr('height', 40)
      .attr('patternUnits', 'userSpaceOnUse');

    pattern
      .append('path')
      .attr('d', 'M 40 0 L 0 0 0 40')
      .attr('fill', 'none')
      .attr('stroke', 'rgba(255, 255, 255, 0.03)')
      .attr('stroke-width', 1);

    this.svg
      .append('rect')
      .attr('width', '100%')
      .attr('height', '100%')
      .attr('fill', 'url(#topo-grid)');

    // Zoom Layer
    this.gZoom = this.svg.append('g').attr('class', 'zoom-layer');

    // Zoom Behavior
    this.zoomBehavior = d3
      .zoom()
      .scaleExtent([0.15, 4])
      .on('zoom', (event: any) => {
        this.gZoom.attr('transform', event.transform);
      });

    this.svg.call(this.zoomBehavior).on('dblclick.zoom', null);

    // Initial Center Transform
    this.svg.call(
      this.zoomBehavior.transform,
      d3.zoomIdentity.translate(this.width / 2, this.height / 2).scale(0.85)
    );

    // Setup Resize Observer
    this.setupResizeObserver();

    // Render Data
    this.renderGraph();
  }

  private setupResizeObserver(): void {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
    const element = this.chartContainer.nativeElement;
    this.resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0 && entry.contentRect.height > 0) {
          this.width = entry.contentRect.width;
          this.height = entry.contentRect.height;
          if (this.simulation) {
            this.simulation.force('center', d3.forceCenter(0, 0));
            this.simulation.alpha(0.2).restart();
          }
        }
      }
    });
    this.resizeObserver.observe(element);
  }

  private renderGraph(): void {
    if (!this.rawGraph || !this.gZoom) return;

    // Filter nodes based on active filters
    const filteredNodeIds = new Set<string>();
    const nodesToRender: SimulationNode[] = [];

    this.rawGraph.nodes.forEach((node) => {
      // Type filter
      if (!this.typeFilters[node.type]) return;

      // Environment filter
      if (this.selectedEnv !== 'ALL' && node.environment && node.environment !== this.selectedEnv) {
        return;
      }

      filteredNodeIds.add(node.id);
      nodesToRender.push({ ...node, x: node.x || 0, y: node.y || 0 });
    });

    // Filter links connecting only visible nodes
    const linksToRender: SimulationLink[] = [];
    this.rawGraph.links.forEach((l) => {
      const sourceId = typeof l.source === 'object' ? (l.source as any).id : l.source;
      const targetId = typeof l.target === 'object' ? (l.target as any).id : l.target;

      if (filteredNodeIds.has(sourceId) && filteredNodeIds.has(targetId)) {
        linksToRender.push({
          source: sourceId,
          target: targetId,
          type: l.type,
          value: l.value
        });
      }
    });

    // Clear previous elements
    this.gZoom.selectAll('*').remove();

    // Layers: Links layer below, Nodes layer above
    const gLinks = this.gZoom.append('g').attr('class', 'links-layer');
    const gNodes = this.gZoom.append('g').attr('class', 'nodes-layer');

    // Create D3 Force Simulation
    if (this.simulation) {
      this.simulation.stop();
    }

    this.simulation = d3
      .forceSimulation(nodesToRender)
      .force(
        'link',
        d3
          .forceLink(linksToRender)
          .id((d: any) => d.id)
          .distance((d: any) => {
            if (d.type === 'HUB_TO_CLUSTER') return 180;
            if (d.type === 'CLUSTER_TO_NODE') return 80;
            if (d.type === 'NODE_TO_NAMESPACE') return 45;
            return 70;
          })
          .strength(0.7)
      )
      .force(
        'charge',
        d3.forceManyBody().strength((d: any) => {
          if (d.type === 'HUB') return -800;
          if (d.type === 'CLUSTER') return -450;
          if (d.type === 'NODE') return -120;
          return -60;
        })
      )
      .force(
        'collision',
        d3.forceCollide().radius((d: any) => {
          if (d.type === 'HUB') return 36;
          if (d.type === 'CLUSTER') return 28;
          if (d.type === 'NODE') return 18;
          return 14;
        })
      )
      .force('center', d3.forceCenter(0, 0))
      .alphaDecay(0.025);

    // Render Links
    this.linkSelection = gLinks
      .selectAll('line')
      .data(linksToRender)
      .enter()
      .append('line')
      .attr('stroke', (d: any) => {
        if (d.type === 'HUB_TO_CLUSTER') return 'rgba(168, 85, 247, 0.4)';
        if (d.type === 'CLUSTER_TO_NODE') return 'rgba(59, 130, 246, 0.3)';
        if (d.type === 'NODE_TO_NAMESPACE') return 'rgba(16, 185, 129, 0.25)';
        return 'rgba(255, 255, 255, 0.15)';
      })
      .attr('stroke-width', (d: any) => (d.type === 'HUB_TO_CLUSTER' ? 2 : 1.2))
      .attr('stroke-dasharray', (d: any) => (d.type === 'NODE_TO_NAMESPACE' ? '3,3' : 'none'));

    // Render Nodes (Groups)
    this.nodeSelection = gNodes
      .selectAll('g.node')
      .data(nodesToRender, (d: any) => d.id)
      .enter()
      .append('g')
      .attr('class', 'node cursor-pointer')
      .call(
        d3
          .drag<SVGGElement, SimulationNode>()
          .on('start', (event, d) => this.dragStarted(event, d))
          .on('drag', (event, d) => this.dragged(event, d))
          .on('end', (event, d) => this.dragEnded(event, d))
      )
      .on('click', (_event: any, d: SimulationNode) => {
        this.selectNode(d);
      });

    // Outer glow / aura circles for Hubs and Clusters
    this.nodeSelection
      .filter((d: any) => d.type === 'HUB' || d.type === 'CLUSTER')
      .append('circle')
      .attr('r', (d: any) => (d.type === 'HUB' ? 26 : 20))
      .attr('fill', 'none')
      .attr('stroke', (d: any) => (d.type === 'HUB' ? 'rgba(168, 85, 247, 0.35)' : 'rgba(59, 130, 246, 0.3)'))
      .attr('stroke-width', 2)
      .attr('filter', 'url(#glow)');

    // Main Node Circle
    this.nodeSelection
      .append('circle')
      .attr('r', (d: any) => this.getNodeRadius(d))
      .attr('fill', (d: any) => this.getNodeFill(d))
      .attr('stroke', (d: any) => this.getNodeStroke(d))
      .attr('stroke-width', (d: any) => (d.type === 'HUB' ? 3 : 1.5))
      .attr('class', 'transition-all duration-150');

    // Inner Glyph / Symbol
    this.nodeSelection
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('dominant-baseline', 'central')
      .attr('fill', '#ffffff')
      .attr('font-size', (d: any) => (d.type === 'HUB' ? '12px' : d.type === 'CLUSTER' ? '10px' : '8px'))
      .attr('font-weight', 'bold')
      .attr('pointer-events', 'none')
      .text((d: any) => {
        if (d.type === 'HUB') return 'HUB';
        if (d.type === 'CLUSTER') return d.label.substring(0, 3).toUpperCase();
        if (d.role === 'MASTER') return 'M';
        if (d.type === 'NODE') return 'W';
        if (d.type === 'NAMESPACE') return d.rating || 'NS';
        return '';
      });

    // Node Label under circle
    this.nodeSelection
      .append('text')
      .attr('dy', (d: any) => this.getNodeRadius(d) + 12)
      .attr('text-anchor', 'middle')
      .attr('fill', 'rgba(255, 255, 255, 0.85)')
      .attr('font-size', (d: any) => (d.type === 'HUB' ? '11px' : d.type === 'CLUSTER' ? '10px' : '8px'))
      .attr('font-family', 'ui-monospace, monospace')
      .attr('pointer-events', 'none')
      .text((d: any) => {
        if (d.type === 'HUB' || d.type === 'CLUSTER') return d.label;
        if (d.type === 'NAMESPACE') return d.label.length > 12 ? d.label.substring(0, 10) + '..' : d.label;
        return '';
      });

    // Simulation Tick Update
    this.simulation.on('tick', () => {
      this.linkSelection
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y);

      this.nodeSelection.attr('transform', (d: any) => `translate(${d.x},${d.y})`);
    });

    // Apply search highlight if active
    this.applySearchHighlight();
  }

  // Node radius styling
  private getNodeRadius(d: GraphNode): number {
    switch (d.type) {
      case 'HUB':
        return 22;
      case 'CLUSTER':
        return 16;
      case 'NODE':
        return 10;
      case 'NAMESPACE':
        return 9;
      default:
        return 8;
    }
  }

  // Node fill color styling
  private getNodeFill(d: GraphNode): string {
    switch (d.type) {
      case 'HUB':
        return '#7828C8'; // Deep Purple
      case 'CLUSTER':
        if (d.environment === 'PRODUCTION') return '#006FEE'; // Primary Blue
        if (d.environment === 'STAGING') return '#F5A524'; // Amber
        return '#06B6D4'; // Cyan
      case 'NODE':
        return d.role === 'MASTER' ? '#475569' : '#334155'; // Slate
      case 'NAMESPACE':
        if (d.rating === 'A' || d.rating === 'B') return '#17C964'; // Emerald Optimal
        if (d.rating === 'D') return '#F5A524'; // Warning
        if (d.rating === 'F') return '#F31260'; // Severe Waste
        return '#10B981';
      default:
        return '#64748B';
    }
  }

  // Node stroke color styling
  private getNodeStroke(d: GraphNode): string {
    if (this.selectedNode && this.selectedNode.id === d.id) {
      return '#FFFFFF';
    }
    switch (d.type) {
      case 'HUB':
        return '#C084FC';
      case 'CLUSTER':
        return '#93C5FD';
      case 'NODE':
        return '#94A3B8';
      case 'NAMESPACE':
        return 'rgba(255, 255, 255, 0.4)';
      default:
        return '#CBD5E1';
    }
  }

  // Drag Handlers
  private dragStarted(event: any, d: SimulationNode): void {
    if (!event.active) this.simulation.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  private dragged(event: any, d: SimulationNode): void {
    d.fx = event.x;
    d.fy = event.y;
  }

  private dragEnded(event: any, d: SimulationNode): void {
    if (!event.active) this.simulation.alphaTarget(0);
  }

  unfixNode(node: GraphNode): void {
    node.fx = null;
    node.fy = null;
    if (this.simulation) {
      this.simulation.alpha(0.2).restart();
    }
    this.cdr.detectChanges();
  }

  selectNode(node: GraphNode): void {
    this.selectedNode = node;
    this.cdr.detectChanges();

    if (this.nodeSelection) {
      this.nodeSelection
        .select('circle')
        .attr('stroke', (d: any) => (d.id === node.id ? '#FFFFFF' : this.getNodeStroke(d)))
        .attr('stroke-width', (d: any) => (d.id === node.id ? 3.5 : d.type === 'HUB' ? 3 : 1.5));
    }
  }

  toggleTypeFilter(type: TopologyNodeType): void {
    this.typeFilters[type] = !this.typeFilters[type];
    this.applyFilters();
  }

  applyFilters(): void {
    this.renderGraph();
  }

  onSearchChange(): void {
    this.applySearchHighlight();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.applySearchHighlight();
  }

  private applySearchHighlight(): void {
    if (!this.nodeSelection) return;

    const query = this.searchQuery.trim().toLowerCase();

    if (!query) {
      this.nodeSelection.attr('opacity', 1);
      this.linkSelection.attr('opacity', 1);
      return;
    }

    const matchingIds = new Set<string>();

    this.nodeSelection.each((d: any) => {
      const match =
        d.label.toLowerCase().includes(query) ||
        (d.team && d.team.toLowerCase().includes(query)) ||
        (d.environment && d.environment.toLowerCase().includes(query)) ||
        d.type.toLowerCase().includes(query);
      if (match) {
        matchingIds.add(d.id);
      }
    });

    this.nodeSelection.attr('opacity', (d: any) => (matchingIds.has(d.id) ? 1 : 0.15));
    this.linkSelection.attr('opacity', (d: any) => {
      const sourceId = typeof d.source === 'object' ? d.source.id : d.source;
      const targetId = typeof d.target === 'object' ? d.target.id : d.target;
      return matchingIds.has(sourceId) || matchingIds.has(targetId) ? 0.8 : 0.05;
    });
  }

  // Zoom Actions
  zoomIn(): void {
    if (this.svg && this.zoomBehavior) {
      this.svg.transition().duration(300).call(this.zoomBehavior.scaleBy, 1.3);
    }
  }

  zoomOut(): void {
    if (this.svg && this.zoomBehavior) {
      this.svg.transition().duration(300).call(this.zoomBehavior.scaleBy, 0.75);
    }
  }

  resetView(): void {
    if (this.svg && this.zoomBehavior) {
      this.svg
        .transition()
        .duration(400)
        .call(
          this.zoomBehavior.transform,
          d3.zoomIdentity.translate(this.width / 2, this.height / 2).scale(0.85)
        );
    }
  }

  reheatSimulation(): void {
    if (this.simulation) {
      this.nodeSelection.each((d: any) => {
        d.fx = null;
        d.fy = null;
      });
      this.simulation.alpha(0.8).restart();
    }
  }

  // Visual helper helpers for Drawer
  getNodeColorClass(node: GraphNode): string {
    switch (node.type) {
      case 'HUB':
        return 'bg-purple-600/20 text-purple-400 border border-purple-500/30';
      case 'CLUSTER':
        return 'bg-blue-600/20 text-blue-400 border border-blue-500/30';
      case 'NODE':
        return 'bg-slate-600/20 text-slate-300 border border-slate-500/30';
      case 'NAMESPACE':
        return node.rating === 'A' || node.rating === 'B'
          ? 'bg-emerald-600/20 text-emerald-400 border border-emerald-500/30'
          : 'bg-rose-600/20 text-rose-400 border border-rose-500/30';
      default:
        return 'bg-content3 text-default-400';
    }
  }

  getNodeIconName(node: GraphNode): string {
    switch (node.type) {
      case 'HUB':
        return 'shield-check';
      case 'CLUSTER':
        return 'server';
      case 'NODE':
        return 'cpu';
      case 'NAMESPACE':
        return 'layers';
      default:
        return 'hard-drive';
    }
  }
}

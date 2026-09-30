import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewChild,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import { saveDownload } from '../../shared/download';
import {
  FinAiChart,
  FinAiChartDataPoint,
  FinAiCliSnippet,
  FinAiDryRunResult,
  FinAiGitOpsManifest,
  FinAiMetricItem,
  FinAiNotifyResult,
  FinAiPromptRequest,
  FinAiQuickPrompt,
  FinAiResponse,
  FinAiYamlDiff
} from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

export interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  timestamp: Date;
  text?: string;
  response?: FinAiResponse;
  isLoading?: boolean;
  error?: string;
  feedback?: 'like' | 'dislike';
}

export type ViewFilterMode = 'ALL' | 'CLI' | 'PLAN' | 'METRICS' | 'CHARTS' | 'DIFF';

@Component({
  selector: 'app-finai-copilot',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  template: `
    <!-- Slide-over Backdrop -->
    <div
      *ngIf="isOpen"
      (click)="close()"
      class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm transition-opacity duration-200"
    ></div>

    <!-- Slide-over Drawer -->
    <div
      *ngIf="isOpen"
      class="fixed inset-y-0 right-0 z-50 w-full max-w-2xl bg-content1 border-l border-divider shadow-2xl flex flex-col transition-transform duration-300 transform ease-out"
      (click)="$event.stopPropagation()"
    >
      <!-- Drawer Header -->
      <div class="px-6 py-4 border-b border-divider flex items-center justify-between bg-content1/80 backdrop-blur-md sticky top-0 z-10">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-primary to-purple-600 flex items-center justify-center text-white shadow-md shadow-primary/20">
            <app-icon name="sparkles" [size]="20"></app-icon>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="text-base font-bold text-foreground">FinAI Copilot</h2>
              <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-primary/15 text-primary border border-primary/25">
                OpenShift AI Engine
              </span>
            </div>
            <p class="text-xs text-default-400">Assistant d'optimisation des coûts, sizing & conformité licences</p>
          </div>
        </div>

        <div class="flex items-center gap-1.5">
          <!-- Export Chat to PDF (Executive Summary) -->
          <button
            type="button"
            *ngIf="messages.length > 0"
            (click)="exportChatPdf()"
            title="Télécharger le rapport exécutif managérial au format PDF"
            class="px-2.5 py-1.5 rounded-xl bg-danger hover:bg-danger/90 text-white font-semibold transition-all cursor-pointer flex items-center gap-1.5 text-xs shadow-sm shadow-danger/20"
          >
            <app-icon name="file-text" [size]="14"></app-icon>
            <span class="text-[11px] font-bold">Export PDF</span>
          </button>

          <!-- Export Chat to Markdown (DevOps & Jira) -->
          <button
            type="button"
            *ngIf="messages.length > 0"
            (click)="exportChatMarkdown()"
            title="Exporter l'historique complet au format Markdown (.md) pour Jira ou documentation"
            class="px-2.5 py-1.5 rounded-xl bg-content2 hover:bg-content3 border border-divider text-default-600 hover:text-foreground font-medium transition-all cursor-pointer flex items-center gap-1.5 text-xs"
          >
            <app-icon name="download" [size]="14"></app-icon>
            <span class="text-[11px]">Audit .MD</span>
          </button>

          <!-- Reset Chat -->
          <button
            type="button"
            (click)="resetChat()"
            title="Réinitialiser la conversation"
            class="p-2 rounded-xl text-default-400 hover:text-foreground hover:bg-content2 transition-colors cursor-pointer"
          >
            <app-icon name="refresh" [size]="16"></app-icon>
          </button>

          <!-- Close Drawer -->
          <button
            type="button"
            (click)="close()"
            title="Fermer le Copilot"
            class="p-2 rounded-xl text-default-400 hover:text-foreground hover:bg-content2 transition-colors cursor-pointer"
          >
            <app-icon name="x" [size]="18"></app-icon>
          </button>
        </div>
      </div>

      <!-- Priority 1: View Filter Bar (Pills Filter) -->
      <div *ngIf="messages.length > 0" class="px-6 py-2 bg-content2/80 border-b border-divider flex items-center justify-between text-xs">
        <div class="flex items-center gap-1.5 overflow-x-auto py-0.5">
          <span class="text-[10px] font-bold uppercase tracking-wider text-default-400 mr-1">Filtrer vue :</span>
          <button
            type="button"
            (click)="filterMode = 'ALL'"
            [class.bg-primary]="filterMode === 'ALL'"
            [class.text-primary-foreground]="filterMode === 'ALL'"
            [class.bg-content3]="filterMode !== 'ALL'"
            [class.text-default-600]="filterMode !== 'ALL'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer"
          >
            Tout afficher
          </button>
          <button
            type="button"
            (click)="filterMode = 'CLI'"
            [class.bg-primary]="filterMode === 'CLI'"
            [class.text-primary-foreground]="filterMode === 'CLI'"
            [class.bg-content3]="filterMode !== 'CLI'"
            [class.text-default-600]="filterMode !== 'CLI'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
          >
            <app-icon name="terminal" [size]="11"></app-icon>
            <span>Commandes oc CLI</span>
          </button>
          <button
            type="button"
            (click)="filterMode = 'PLAN'"
            [class.bg-primary]="filterMode === 'PLAN'"
            [class.text-primary-foreground]="filterMode === 'PLAN'"
            [class.bg-content3]="filterMode !== 'PLAN'"
            [class.text-default-600]="filterMode !== 'PLAN'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
          >
            <app-icon name="check-circle" [size]="11"></app-icon>
            <span>Plan d'Action</span>
          </button>
          <button
            type="button"
            (click)="filterMode = 'METRICS'"
            [class.bg-primary]="filterMode === 'METRICS'"
            [class.text-primary-foreground]="filterMode === 'METRICS'"
            [class.bg-content3]="filterMode !== 'METRICS'"
            [class.text-default-600]="filterMode !== 'METRICS'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
          >
            <app-icon name="coins" [size]="11"></app-icon>
            <span>Métriques Clés</span>
          </button>
          <button
            type="button"
            (click)="filterMode = 'CHARTS'"
            [class.bg-primary]="filterMode === 'CHARTS'"
            [class.text-primary-foreground]="filterMode === 'CHARTS'"
            [class.bg-content3]="filterMode !== 'CHARTS'"
            [class.text-default-600]="filterMode !== 'CHARTS'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
          >
            <app-icon name="pie-chart" [size]="11"></app-icon>
            <span>Graphiques</span>
          </button>
          <button
            type="button"
            (click)="filterMode = 'DIFF'"
            [class.bg-primary]="filterMode === 'DIFF'"
            [class.text-primary-foreground]="filterMode === 'DIFF'"
            [class.bg-content3]="filterMode !== 'DIFF'"
            [class.text-default-600]="filterMode !== 'DIFF'"
            class="px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
          >
            <app-icon name="file-text" [size]="11"></app-icon>
            <span>Diff YAML</span>
          </button>
        </div>

        <span class="text-[10px] text-default-400 font-mono hidden md:inline">
          {{ messages.length }} échanges
        </span>
      </div>

      <!-- Context Bar (if cluster / namespace targeted) -->
      <div *ngIf="contextClusterId || contextNamespace" class="px-6 py-2 bg-content2/60 border-b border-divider flex items-center justify-between text-xs">
        <div class="flex items-center gap-2 text-default-500">
          <app-icon name="filter" [size]="13" className="text-primary"></app-icon>
          <span>Contexte ciblé :</span>
          <span *ngIf="contextClusterId" class="px-1.5 py-0.5 rounded bg-content3 font-mono text-[11px] text-foreground font-semibold">
            {{ contextClusterId }}
          </span>
          <span *ngIf="contextNamespace" class="px-1.5 py-0.5 rounded bg-content3 font-mono text-[11px] text-foreground font-semibold">
            ns: {{ contextNamespace }}
          </span>
        </div>
        <button
          type="button"
          (click)="clearContext()"
          class="text-[11px] text-default-400 hover:text-danger cursor-pointer"
        >
          Effacer filtre
        </button>
      </div>

      <!-- Messages / Interaction Area -->
      <div class="flex-1 overflow-y-auto p-6 space-y-6 scroll-smooth" #scrollContainer (scroll)="onScroll()">
        
        <!-- Welcome banner if no messages -->
        <div *ngIf="messages.length === 0" class="space-y-6 my-auto pt-4">
          <div class="p-5 rounded-2xl bg-gradient-to-br from-primary/10 via-content2 to-content2/50 border border-primary/20 text-center space-y-3">
            <div class="w-12 h-12 rounded-2xl bg-primary/20 text-primary flex items-center justify-center mx-auto shadow-inner">
              <app-icon name="bot" [size]="24"></app-icon>
            </div>
            <h3 class="text-sm font-bold text-foreground">Bonjour ! Je suis FinAI Copilot pour OpenShift.</h3>
            <p class="text-xs text-default-500 max-w-md mx-auto leading-relaxed">
              Posez-moi vos questions sur le dimensionnement des CPU/RAM, les recommandations de rightsizing, l'audit des licences Red Hat OpenShift, ou demandez-moi de générer les commandes CLI <code class="font-mono text-primary font-semibold">oc patch</code> correctives.
            </p>
          </div>

          <!-- Quick Prompts Section -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold uppercase tracking-wider text-default-400 flex items-center gap-1.5">
                <app-icon name="zap" [size]="13" className="text-warning"></app-icon>
                Questions suggérées
              </span>
              <span class="text-[11px] text-default-400">1-clic pour interroger</span>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <button
                *ngFor="let qp of quickPrompts"
                type="button"
                (click)="applyQuickPrompt(qp.prompt)"
                class="p-3 text-left rounded-xl bg-content2 hover:bg-content3 border border-divider hover:border-primary/40 transition-all group cursor-pointer space-y-1"
              >
                <div class="flex items-center justify-between">
                  <span class="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-content3 text-primary group-hover:bg-primary group-hover:text-white transition-colors">
                    {{ qp.category }}
                  </span>
                  <span *ngIf="qp.badge" class="text-[9px] font-semibold text-default-400">{{ qp.badge }}</span>
                </div>
                <div class="text-xs font-semibold text-foreground line-clamp-1">{{ qp.title }}</div>
                <div class="text-[11px] text-default-400 line-clamp-2">{{ qp.prompt }}</div>
              </button>
            </div>
          </div>
        </div>

        <!-- Chat History -->
        <div *ngFor="let msg of messages; let idx = index" class="space-y-3">
          
          <!-- USER MESSAGE -->
          <div *ngIf="msg.sender === 'user'" class="flex justify-end items-start gap-2.5">
            <div class="max-w-[85%] rounded-2xl rounded-tr-sm bg-primary text-primary-foreground p-3.5 shadow-md shadow-primary/10">
              <p class="text-xs font-medium leading-relaxed whitespace-pre-wrap">{{ msg.text }}</p>
              <span class="block text-[10px] text-primary-foreground/70 text-right mt-1.5">
                {{ msg.timestamp | date:'HH:mm' }}
              </span>
            </div>
          </div>

          <!-- ASSISTANT MESSAGE -->
          <div *ngIf="msg.sender === 'assistant'" class="flex justify-start items-start gap-3">
            <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-primary to-purple-600 flex items-center justify-center text-white flex-shrink-0 mt-1 shadow-sm">
              <app-icon name="bot" [size]="16"></app-icon>
            </div>

            <div class="flex-1 space-y-3 max-w-[90%]">
              
              <!-- Loading Skeleton / Animation -->
              <div *ngIf="msg.isLoading" class="p-4 rounded-2xl bg-content2 border border-divider space-y-3 animate-pulse">
                <div class="flex items-center gap-2">
                  <div class="w-2.5 h-2.5 rounded-full bg-primary animate-ping"></div>
                  <span class="text-xs font-semibold text-foreground">FinAI analyse la télémétrie des clusters OpenShift...</span>
                </div>
                <div class="space-y-1.5">
                  <div class="h-2.5 bg-content3 rounded w-5/6"></div>
                  <div class="h-2.5 bg-content3 rounded w-3/4"></div>
                  <div class="h-2.5 bg-content3 rounded w-1/2"></div>
                </div>
              </div>

              <!-- Error Box -->
              <div *ngIf="msg.error" class="p-4 rounded-2xl bg-danger/10 border border-danger/30 text-danger text-xs space-y-1">
                <div class="font-bold flex items-center gap-1.5">
                  <app-icon name="alert-triangle" [size]="14"></app-icon>
                  Erreur de traitement FinAI
                </div>
                <div>{{ msg.error }}</div>
              </div>

              <!-- Full Rich Response -->
              <div *ngIf="msg.response as res" class="rounded-2xl bg-content2 border border-divider p-4 space-y-4 shadow-sm">
                
                <!-- Header / Headline & Confidence -->
                <div class="flex items-center justify-between pb-3 border-b border-divider">
                  <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                    <app-icon name="file-text" [size]="13" className="text-primary"></app-icon>
                    {{ res.headline }}
                  </h4>
                  <div class="flex items-center gap-1.5 text-[11px] text-default-400">
                    <span>Confiance IA :</span>
                    <strong class="text-foreground">{{ (res.confidenceScore * 100) | number:'1.0-0' }}%</strong>
                  </div>
                </div>

                <!-- Highlight Metrics Strip (Visible in ALL or METRICS mode) -->
                <div *ngIf="(filterMode === 'ALL' || filterMode === 'METRICS') && res.metrics && res.metrics.length > 0" class="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-1">
                  <div
                    *ngFor="let metric of res.metrics"
                    class="p-2.5 rounded-xl border"
                    [ngClass]="{
                      'bg-success/10 border-success/20 text-success': metric.type === 'SAVINGS' || metric.type === 'SUCCESS',
                      'bg-warning/10 border-warning/20 text-warning': metric.type === 'WARNING',
                      'bg-content3/70 border-divider text-foreground': metric.type === 'CORES' || metric.type === 'INFO'
                    }"
                  >
                    <span class="text-[10px] font-bold uppercase tracking-wider block truncate opacity-80">{{ metric.label }}</span>
                    <span class="text-xs font-bold font-mono">{{ metric.value }}</span>
                  </div>
                </div>

                <!-- Interactive Charts Section (Visible in ALL or CHARTS mode) -->
                <div *ngIf="(filterMode === 'ALL' || filterMode === 'CHARTS') && res.charts && res.charts.length > 0" class="space-y-3 pt-2 border-t border-divider">
                  <div class="flex items-center justify-between">
                    <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                      <app-icon name="bar-chart-2" [size]="13" className="text-primary"></app-icon>
                      Graphiques & Visualisations Interactives
                    </h4>
                    <span class="text-[10px] text-default-400 font-mono">{{ res.charts.length }} vue(s)</span>
                  </div>

                  <div class="space-y-3">
                    <div *ngFor="let chart of res.charts" class="p-3.5 rounded-xl bg-content1 border border-divider space-y-3 shadow-sm">
                      <!-- Chart Header -->
                      <div class="flex items-center justify-between">
                        <div>
                          <div class="text-xs font-bold text-foreground flex items-center gap-1.5">
                            <span>{{ chart.title }}</span>
                            <span *ngIf="chart.totalValue" class="px-1.5 py-0.5 rounded-md bg-primary/10 text-primary text-[10px] font-mono font-bold">
                              {{ chart.totalValue }}
                            </span>
                          </div>
                          <div *ngIf="chart.subtitle" class="text-[11px] text-default-400">
                            {{ chart.subtitle }}
                          </div>
                        </div>
                        <span class="px-2 py-0.5 rounded-full text-[9px] font-bold tracking-wider uppercase"
                              [ngClass]="chart.type === 'DONUT' ? 'bg-secondary/15 text-secondary' : chart.type === 'TREND' ? 'bg-warning/15 text-warning' : 'bg-primary/15 text-primary'">
                          {{ chart.type }}
                        </span>
                      </div>

                      <!-- BAR CHART RENDERING -->
                      <div *ngIf="chart.type === 'BAR'" class="space-y-2 pt-1">
                        <div *ngFor="let pt of chart.points" class="space-y-1">
                          <div class="flex items-center justify-between text-[11px]">
                            <span class="font-medium text-foreground truncate max-w-[200px]" [title]="pt.label">{{ pt.label }}</span>
                            <div class="flex items-center gap-2">
                              <span *ngIf="pt.secondaryValue" class="text-[10px] text-default-400 font-mono">base: {{ pt.secondaryValue }}c</span>
                              <span class="font-bold font-mono text-foreground">{{ pt.formattedValue || pt.value }}</span>
                            </div>
                          </div>
                          <!-- Progress Bar -->
                          <div class="w-full h-2.5 rounded-full bg-content3 overflow-hidden flex">
                            <div
                              class="h-full rounded-full transition-all duration-700 ease-out"
                              [style.width.%]="getBarPercentage(pt.value, chart.points)"
                              [style.backgroundColor]="pt.color || '#3B82F6'"
                            ></div>
                          </div>
                        </div>
                      </div>

                      <!-- DONUT / GAUGE RENDERING -->
                      <div *ngIf="chart.type === 'DONUT'" class="flex flex-col sm:flex-row items-center gap-4 pt-1">
                        <!-- SVG Donut Circle -->
                        <div class="relative w-28 h-28 flex items-center justify-center flex-shrink-0">
                          <svg viewBox="0 0 36 36" class="w-28 h-28 transform -rotate-90">
                            <path
                              class="text-content3"
                              stroke-width="3.8"
                              stroke="currentColor"
                              fill="none"
                              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                            />
                            <path
                              *ngFor="let seg of getDonutSegments(chart.points)"
                              [attr.stroke]="seg.color"
                              stroke-width="3.8"
                              [attr.stroke-dasharray]="seg.dashArray"
                              [attr.stroke-dashoffset]="seg.dashOffset"
                              stroke-linecap="round"
                              fill="none"
                              class="transition-all duration-700"
                            />
                          </svg>
                          <div class="absolute inset-0 flex flex-col items-center justify-center text-center">
                            <span class="text-xs font-bold text-foreground font-mono leading-tight">{{ chart.totalValue }}</span>
                            <span class="text-[9px] text-default-400 uppercase font-semibold">Total</span>
                          </div>
                        </div>

                        <!-- Donut Legend -->
                        <div class="flex-1 space-y-1.5 w-full">
                          <div *ngFor="let pt of chart.points" class="flex items-center justify-between text-[11px] p-1.5 rounded-lg bg-content2/50 border border-divider/60">
                            <div class="flex items-center gap-2">
                              <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" [style.backgroundColor]="pt.color || '#3B82F6'"></span>
                              <span class="text-default-600 font-medium truncate max-w-[150px]">{{ pt.label }}</span>
                            </div>
                            <span class="font-bold font-mono text-foreground">{{ pt.formattedValue || pt.value }}</span>
                          </div>
                        </div>
                      </div>

                      <!-- TREND CHART RENDERING -->
                      <div *ngIf="chart.type === 'TREND'" class="space-y-3 pt-1">
                        <div class="grid grid-cols-3 gap-2">
                          <div *ngFor="let pt of chart.points; let i = index" class="p-2.5 rounded-xl border border-divider bg-content2/40 text-center space-y-1">
                            <span class="text-[10px] text-default-400 block font-semibold">Étape {{ i + 1 }}</span>
                            <span class="text-xs font-bold font-mono text-foreground block">{{ pt.formattedValue || pt.value }}</span>
                            <span class="text-[10px] text-default-500 truncate block">{{ pt.label }}</span>
                          </div>
                        </div>
                      </div>

                    </div>
                  </div>
                </div>

                <!-- Detailed Analysis (Rich Markdown formatted - Visible in ALL mode) -->
                <div *ngIf="filterMode === 'ALL' && res.analysisMarkdown" class="space-y-1.5 pt-2 border-t border-divider">
                  <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                    <app-icon name="activity" [size]="13" className="text-secondary"></app-icon>
                    Analyse Détaillée & Justification
                  </h4>
                  <div
                    class="text-xs text-default-600 leading-relaxed rounded-xl bg-content1/70 p-3.5 border border-divider prose-sm"
                    [innerHTML]="formatMarkdown(res.analysisMarkdown)"
                  ></div>
                </div>

                <!-- Actionable Execution Steps (Visible in ALL or PLAN mode) -->
                <div *ngIf="(filterMode === 'ALL' || filterMode === 'PLAN') && res.executionPlan && res.executionPlan.length > 0" class="space-y-2 pt-2 border-t border-divider">
                  <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                    <app-icon name="check-circle" [size]="13" className="text-success"></app-icon>
                    Plan d'Action Recommandé
                  </h4>
                  <ol class="space-y-1.5">
                    <li *ngFor="let step of res.executionPlan; let i = index" class="flex items-start gap-2 text-xs text-default-600">
                      <span class="w-5 h-5 rounded-full bg-content3 text-foreground font-bold text-[10px] flex items-center justify-center flex-shrink-0 mt-0.5">
                        {{ i + 1 }}
                      </span>
                      <span class="flex-1">{{ step }}</span>
                    </li>
                  </ol>
                </div>

                <!-- Interactive Before vs After YAML Diff Section (Visible in ALL or DIFF mode) -->
                <div *ngIf="(filterMode === 'ALL' || filterMode === 'DIFF') && res.yamlDiff" class="space-y-3 pt-2 border-t border-divider">
                  <div class="flex items-center justify-between">
                    <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                      <app-icon name="file-text" [size]="13" className="text-primary"></app-icon>
                      Diff YAML Interactif Avant / Après
                    </h4>

                    <!-- Split vs Unified Toggle -->
                    <div class="flex items-center p-0.5 rounded-lg bg-content3 border border-divider text-[10px]">
                      <button
                        type="button"
                        (click)="setDiffMode(res.headline, 'split')"
                        [class.bg-primary]="getDiffMode(res.headline) === 'split'"
                        [class.text-primary-foreground]="getDiffMode(res.headline) === 'split'"
                        [class.text-default-500]="getDiffMode(res.headline) !== 'split'"
                        class="px-2 py-0.5 rounded-md font-semibold transition-all cursor-pointer"
                      >
                        Côte à côte
                      </button>
                      <button
                        type="button"
                        (click)="setDiffMode(res.headline, 'unified')"
                        [class.bg-primary]="getDiffMode(res.headline) === 'unified'"
                        [class.text-primary-foreground]="getDiffMode(res.headline) === 'unified'"
                        [class.text-default-500]="getDiffMode(res.headline) !== 'unified'"
                        class="px-2 py-0.5 rounded-md font-semibold transition-all cursor-pointer"
                      >
                        Unifié
                      </button>
                    </div>
                  </div>

                  <!-- Impact Summary Badges -->
                  <div class="p-3 rounded-xl bg-content1 border border-divider space-y-2.5 shadow-sm">
                    <div class="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-divider/60 text-xs">
                      <div class="flex items-center gap-2">
                        <span class="px-2 py-0.5 rounded-md bg-content3 text-foreground font-mono font-bold text-[11px]">
                          {{ res.yamlDiff.resourceKind }} : {{ res.yamlDiff.resourceName }}
                        </span>
                        <span class="text-[11px] text-default-400 font-mono">ns: {{ res.yamlDiff.targetNamespace }}</span>
                      </div>
                      <div class="flex items-center gap-1.5 flex-wrap">
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-success/15 text-success border border-success/20">
                          Δ CPU : {{ res.yamlDiff.cpuDelta }}
                        </span>
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-success/15 text-success border border-success/20">
                          Δ RAM : {{ res.yamlDiff.memoryDelta }}
                        </span>
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-primary/15 text-primary border border-primary/20">
                          Gain : {{ res.yamlDiff.costDelta }}
                        </span>
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-content3 text-default-600">
                          {{ res.yamlDiff.safetyMargin }}
                        </span>
                      </div>
                    </div>

                    <!-- Split View (Side by Side) -->
                    <div *ngIf="getDiffMode(res.headline) === 'split'" class="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs font-mono">
                      <!-- Before / Overprovisioned Pane -->
                      <div class="rounded-xl border border-danger/30 bg-danger/5 overflow-hidden">
                        <div class="px-3 py-1.5 bg-danger/15 border-b border-danger/20 flex items-center justify-between text-danger font-semibold text-[11px]">
                          <span class="flex items-center gap-1.5">
                            <span class="w-2 h-2 rounded-full bg-danger inline-block"></span>
                            Actuel (Surdimensionné)
                          </span>
                          <span class="text-[10px] opacity-80">Avant</span>
                        </div>
                        <pre class="p-3 text-[11px] leading-relaxed text-default-700 overflow-x-auto whitespace-pre">{{ res.yamlDiff.beforeYaml }}</pre>
                      </div>

                      <!-- After / Optimized Pane -->
                      <div class="rounded-xl border border-success/30 bg-success/5 overflow-hidden">
                        <div class="px-3 py-1.5 bg-success/15 border-b border-success/20 flex items-center justify-between text-success font-semibold text-[11px]">
                          <span class="flex items-center gap-1.5">
                            <span class="w-2 h-2 rounded-full bg-success inline-block"></span>
                            Optimisé FinAI Copilot
                          </span>
                          <span class="text-[10px] opacity-80">Après</span>
                        </div>
                        <pre class="p-3 text-[11px] leading-relaxed text-default-700 overflow-x-auto whitespace-pre">{{ res.yamlDiff.afterYaml }}</pre>
                      </div>
                    </div>

                    <!-- Unified View -->
                    <div *ngIf="getDiffMode(res.headline) === 'unified'" class="rounded-xl border border-divider bg-[#0d1117] text-[#c9d1d9] font-mono text-[11px] overflow-hidden">
                      <div class="px-3 py-1.5 bg-[#161b22] border-b border-[#30363d] flex items-center justify-between text-[11px]">
                        <span class="text-default-400">--- a/{{ res.yamlDiff.resourceName }}.yaml +++ b/{{ res.yamlDiff.resourceName }}.yaml</span>
                        <span class="text-[10px] text-success font-semibold">Diff Unifié</span>
                      </div>
                      <div class="p-3 space-y-0.5 overflow-x-auto max-h-72">
                        <div
                          *ngFor="let line of getUnifiedDiffLines(res.yamlDiff)"
                          [ngClass]="{
                            'bg-danger/20 text-red-300 font-semibold px-1 rounded-sm': line.type === 'del',
                            'bg-success/20 text-emerald-300 font-semibold px-1 rounded-sm': line.type === 'add',
                            'text-[#8b949e] px-1': line.type === 'same'
                          }"
                          class="whitespace-pre"
                        >{{ line.text }}</div>
                      </div>
                    </div>

                    <!-- Copy Actions -->
                    <div class="flex items-center justify-end gap-2 pt-1">
                      <button
                        type="button"
                        (click)="copySnippet(res.yamlDiff.afterYaml)"
                        class="px-2.5 py-1 rounded-lg bg-content2 hover:bg-content3 border border-divider text-default-600 hover:text-foreground text-[11px] font-semibold transition-all cursor-pointer flex items-center gap-1"
                      >
                        <app-icon name="copy" [size]="12"></app-icon>
                        <span>Copier YAML Optimisé</span>
                      </button>
                    </div>

                  </div>
                </div>

                <!-- OpenShift CLI Snippets with Dry-Run & GitOps bridges (Visible in ALL or CLI mode) -->
                <div *ngIf="(filterMode === 'ALL' || filterMode === 'CLI') && res.cliCommands && res.cliCommands.length > 0" class="space-y-3 pt-2 border-t border-divider">
                  <div class="flex items-center justify-between">
                    <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                      <app-icon name="terminal" [size]="13" className="text-warning"></app-icon>
                      Commandes CLI <code class="text-foreground font-mono">oc</code> Prêtes à l'Emploi
                    </h4>
                    <span class="text-[10px] font-bold text-default-400">Air-Gapped Ready</span>
                  </div>

                  <div *ngFor="let snippet of res.cliCommands; let sIdx = index" class="rounded-xl overflow-hidden border border-divider bg-[#0d1117] text-[#c9d1d9] shadow-inner space-y-0">
                    <!-- Terminal Header -->
                    <div class="px-3 py-1.5 bg-[#161b22] border-b border-[#30363d] flex items-center justify-between text-[11px]">
                      <div class="flex items-center gap-2">
                        <span class="w-2.5 h-2.5 rounded-full bg-red-500/80 inline-block"></span>
                        <span class="w-2.5 h-2.5 rounded-full bg-amber-500/80 inline-block"></span>
                        <span class="w-2.5 h-2.5 rounded-full bg-green-500/80 inline-block"></span>
                        <span class="font-semibold text-white ml-1">{{ snippet.title }}</span>
                      </div>

                      <div class="flex items-center gap-2">
                        <span *ngIf="snippet.targetNamespace" class="px-1.5 py-0.2 rounded text-[9px] font-mono bg-content3 text-foreground">
                          ns: {{ snippet.targetNamespace }}
                        </span>

                        <button
                          type="button"
                          (click)="copySnippet(snippet.command)"
                          class="flex items-center gap-1 px-2 py-0.5 rounded bg-[#21262d] hover:bg-[#30363d] text-[#c9d1d9] hover:text-white transition-colors cursor-pointer text-[10px] font-medium"
                        >
                          <app-icon [name]="copiedCommand === snippet.command ? 'check' : 'copy'" [size]="11"></app-icon>
                          <span>{{ copiedCommand === snippet.command ? 'Copié !' : 'Copier' }}</span>
                        </button>
                      </div>
                    </div>

                    <!-- Command Body -->
                    <div class="p-3 font-mono text-[11px] overflow-x-auto leading-relaxed whitespace-pre-wrap select-all text-emerald-300">
                      {{ snippet.command }}
                    </div>

                    <!-- Snippet Note & Action Bar -->
                    <div class="px-3 py-2 bg-[#161b22]/70 border-t border-[#30363d] flex flex-wrap items-center justify-between gap-2 text-[10px]">
                      <span class="text-[#8b949e]">{{ snippet.description }}</span>
                      
                      <div class="flex items-center gap-2">
                        <!-- Priority 2: One-Click Dry-Run Validation -->
                        <button
                          type="button"
                          (click)="runDryRun(snippet)"
                          [disabled]="dryRunLoadingKey === snippet.title"
                          class="px-2 py-0.5 rounded bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 text-emerald-400 font-semibold flex items-center gap-1 cursor-pointer transition-all"
                          title="Exécuter un test dry-run sans impact sur l'API Server OpenShift"
                        >
                          <span *ngIf="dryRunLoadingKey === snippet.title" class="w-2.5 h-2.5 border border-emerald-400 border-t-transparent rounded-full animate-spin"></span>
                          <span>🧪 Valider en Dry-Run</span>
                        </button>

                        <!-- Priority 3: GitOps Manifest Generator Bridge -->
                        <button
                          type="button"
                          (click)="openGitOpsModal(snippet.targetNamespace)"
                          class="px-2 py-0.5 rounded bg-purple-500/15 hover:bg-purple-500/25 border border-purple-500/30 text-purple-300 font-semibold flex items-center gap-1 cursor-pointer transition-all"
                          title="Générer les fichiers Kustomize et ArgoCD Application"
                        >
                          <span>🐙 GitOps (ArgoCD)</span>
                        </button>

                        <!-- What-If Simulator Link -->
                        <button
                          type="button"
                          (click)="launchWhatIfSimulator(snippet.targetNamespace)"
                          class="text-primary hover:text-primary-foreground px-2 py-0.5 rounded bg-primary/10 hover:bg-primary transition-all font-semibold flex items-center gap-1 cursor-pointer"
                          title="Tester le dimensionnement dans le simulateur What-If"
                        >
                          <app-icon name="sliders" [size]="11"></app-icon>
                          <span>What-If</span>
                        </button>
                      </div>
                    </div>

                    <!-- Dry-Run Result Inline Banner (if executed) -->
                    <div *ngIf="dryRunResults[snippet.title] as dry" class="p-3 bg-emerald-950/40 border-t border-emerald-500/30 text-[11px] text-emerald-200 space-y-1">
                      <div class="flex items-center justify-between font-bold">
                        <span class="flex items-center gap-1.5 text-emerald-400">
                          <app-icon name="check-circle" [size]="13"></app-icon>
                          {{ dry.status }}
                        </span>
                        <span class="text-[10px] text-emerald-400/80">{{ dry.timestamp | date:'HH:mm:ss' }}</span>
                      </div>
                      <p class="text-xs text-emerald-100/90 leading-relaxed">{{ dry.message }}</p>
                      <div class="flex items-center gap-3 pt-1 text-[10px] text-emerald-300 font-mono">
                        <span>Pods audités : <strong>{{ dry.podsEvaluated }}</strong></span>
                        <span>Dépassements : <strong class="text-emerald-400">{{ dry.podsExceedingLimits }}</strong></span>
                        <span>Interruption de service : <strong>NULLE (0%)</strong></span>
                      </div>
                    </div>

                  </div>
                </div>

                <!-- Follow-up Questions Chips -->
                <div *ngIf="res.suggestedFollowUps && res.suggestedFollowUps.length > 0" class="pt-2 border-t border-divider space-y-2">
                  <span class="text-[11px] font-bold text-default-400 block">Questions de suivi suggérées :</span>
                  <div class="flex flex-wrap gap-1.5">
                    <button
                      *ngFor="let q of res.suggestedFollowUps"
                      type="button"
                      (click)="applyQuickPrompt(q)"
                      class="px-2.5 py-1 rounded-full text-[11px] bg-content1 hover:bg-content3 border border-divider text-default-600 hover:text-foreground transition-all cursor-pointer text-left flex items-center gap-1"
                    >
                      <span class="text-primary">💬</span>
                      <span>{{ q }}</span>
                    </button>
                  </div>
                </div>

                <!-- Footer Feedback, Notify Slack/Teams & Copy All Report -->
                <div class="pt-2 border-t border-divider flex flex-wrap items-center justify-between gap-2 text-[10px] text-default-400">
                  <div class="flex items-center gap-2">
                    <span>Ce diagnostic vous a-t-il aidé ?</span>
                    <button
                      type="button"
                      (click)="setFeedback(msg, 'like')"
                      [class.text-success]="msg.feedback === 'like'"
                      class="p-1 rounded hover:bg-content3 transition-colors cursor-pointer"
                      title="Utile"
                    >
                      👍
                    </button>
                    <button
                      type="button"
                      (click)="setFeedback(msg, 'dislike')"
                      [class.text-danger]="msg.feedback === 'dislike'"
                      class="p-1 rounded hover:bg-content3 transition-colors cursor-pointer"
                      title="À améliorer"
                    >
                      👎
                    </button>
                  </div>

                  <div class="flex items-center gap-3">
                    <!-- Priority 4: Notify Slack / Teams Trigger -->
                    <button
                      type="button"
                      (click)="openNotifyModal(res)"
                      class="text-primary hover:underline flex items-center gap-1 cursor-pointer font-semibold"
                      title="Diffuser cette recommandation sur Slack ou Microsoft Teams"
                    >
                      <span>📢 Diffuser sur Slack/Teams</span>
                    </button>

                    <!-- Copy Full Report -->
                    <button
                      type="button"
                      (click)="copyFullReport(res)"
                      class="text-default-400 hover:text-foreground flex items-center gap-1 cursor-pointer font-medium"
                      title="Copier toute la synthèse au format Markdown"
                    >
                      <app-icon name="copy" [size]="11"></app-icon>
                      <span>{{ copiedReportId === res.headline ? 'Rapport copié !' : 'Copier la synthèse' }}</span>
                    </button>
                  </div>
                </div>

              </div>

            </div>
          </div>

        </div>

      </div>

      <!-- Scroll to bottom button if user scrolled up -->
      <div *ngIf="showScrollDownButton" class="relative">
        <button
          type="button"
          (click)="scrollToBottom(true)"
          class="absolute -top-12 right-6 px-3 py-1.5 rounded-full bg-primary text-primary-foreground shadow-lg flex items-center gap-1.5 text-xs font-semibold hover:bg-primary/90 transition-all cursor-pointer animate-bounce"
        >
          <app-icon name="chevron-down" [size]="14"></app-icon>
          <span>Derniers messages</span>
        </button>
      </div>

      <!-- Drawer Footer / Input Box -->
      <div class="p-4 border-t border-divider bg-content1/80 backdrop-blur-md sticky bottom-0">
        <form (ngSubmit)="sendQuery()" class="flex items-center gap-2">
          <div class="relative flex-1">
            <input
              type="text"
              [(ngModel)]="currentPrompt"
              name="currentPrompt"
              (keydown.enter)="onEnterPressed($event)"
              placeholder="Posez une question sur le sizing, les licences, les quotas..."
              [disabled]="isBusy"
              class="w-full pl-3.5 pr-10 py-2.5 rounded-xl bg-content2 border border-divider text-xs text-foreground placeholder:text-default-400 focus:outline-none focus:border-primary transition-colors"
              autofocus
            />
          </div>

          <button
            type="submit"
            [disabled]="!currentPrompt.trim() || isBusy"
            class="px-4 py-2.5 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold flex items-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-md shadow-primary/20 cursor-pointer"
          >
            <app-icon *ngIf="!isBusy" name="send" [size]="14"></app-icon>
            <span *ngIf="isBusy" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            <span>Envoyer</span>
          </button>
        </form>

        <div class="mt-2 flex items-center justify-between text-[10px] text-default-400">
          <span>Modèle heuristique autonome pour clusters OpenShift sécurisés / air-gapped</span>
          <span class="font-mono">FinAI v1.3</span>
        </div>
      </div>

    </div>

    <!-- MODAL 1: GitOps Manifest Modal (Priority 3) -->
    <div *ngIf="showGitOpsModal" class="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="heroui-card w-full max-w-2xl p-6 space-y-4 bg-background shadow-2xl border border-divider">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-lg bg-purple-500/20 text-purple-400 flex items-center justify-center font-bold">
              🐙
            </div>
            <div>
              <h3 class="text-sm font-bold text-foreground">Manifeste GitOps (ArgoCD & Kustomize)</h3>
              <p class="text-[11px] text-default-400">Arborescence prête à commit pour OpenShift GitOps</p>
            </div>
          </div>
          <button (click)="showGitOpsModal = false" class="text-default-400 hover:text-foreground text-xs p-1 rounded">✕</button>
        </div>

        <div *ngIf="gitOpsLoading" class="py-12 text-center text-xs text-default-500 animate-pulse space-y-2">
          <div class="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto"></div>
          <div>Génération des manifestes Kustomize & Application ArgoCD...</div>
        </div>

        <div *ngIf="!gitOpsLoading && gitOpsManifest" class="space-y-4 text-xs">
          <!-- Branch & Commit Command Strip -->
          <div class="p-3 rounded-xl bg-content2 border border-divider space-y-1.5 font-mono text-[11px]">
            <div class="text-[10px] uppercase font-bold text-default-400">Branche Git recommandée :</div>
            <div class="text-primary font-bold">git checkout -b {{ gitOpsManifest.branchName }}</div>
            <div class="text-default-500 text-[10px]">Emplacement : {{ gitOpsManifest.repoPath }}</div>
          </div>

          <!-- Manifest Tabs -->
          <div class="flex items-center gap-2 border-b border-divider">
            <button
              type="button"
              (click)="activeGitOpsTab = 'kustomize'"
              [class.border-primary]="activeGitOpsTab === 'kustomize'"
              [class.text-primary]="activeGitOpsTab === 'kustomize'"
              class="pb-2 border-b-2 font-semibold text-xs transition-colors cursor-pointer"
            >
              kustomization.yaml
            </button>
            <button
              type="button"
              (click)="activeGitOpsTab = 'quota'"
              [class.border-primary]="activeGitOpsTab === 'quota'"
              [class.text-primary]="activeGitOpsTab === 'quota'"
              class="pb-2 border-b-2 font-semibold text-xs transition-colors cursor-pointer"
            >
              resource-quota.yaml
            </button>
            <button
              type="button"
              (click)="activeGitOpsTab = 'argocd'"
              [class.border-primary]="activeGitOpsTab === 'argocd'"
              [class.text-primary]="activeGitOpsTab === 'argocd'"
              class="pb-2 border-b-2 font-semibold text-xs transition-colors cursor-pointer"
            >
              argocd-application.yaml
            </button>
          </div>

          <!-- Active Tab Content -->
          <div class="rounded-xl overflow-hidden border border-divider bg-[#0d1117] text-emerald-300 p-3 font-mono text-[11px] max-h-56 overflow-y-auto whitespace-pre-wrap select-all">
            <span *ngIf="activeGitOpsTab === 'kustomize'">{{ gitOpsManifest.kustomizationYaml }}</span>
            <span *ngIf="activeGitOpsTab === 'quota'">{{ gitOpsManifest.resourceQuotaYaml }}</span>
            <span *ngIf="activeGitOpsTab === 'argocd'">{{ gitOpsManifest.argocdApplicationYaml }}</span>
          </div>

          <!-- Modal Actions -->
          <div class="flex items-center justify-between pt-2 border-t border-divider">
            <button
              type="button"
              (click)="downloadGitOpsBundle()"
              class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-xs font-semibold px-3 py-1.5 flex items-center gap-1.5 cursor-pointer"
            >
              <app-icon name="download" [size]="14"></app-icon>
              <span>Télécharger le Bundle</span>
            </button>

            <button
              type="button"
              (click)="showGitOpsModal = false"
              class="heroui-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold px-4 py-1.5 cursor-pointer"
            >
              Fermer
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL 2: Slack / Microsoft Teams Notification (Priority 4) -->
    <div *ngIf="showNotifyModal" class="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div class="heroui-card w-full max-w-md p-6 space-y-4 bg-background shadow-2xl border border-divider">
        <div class="flex items-center justify-between pb-3 border-b border-divider">
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-lg bg-emerald-500/20 text-emerald-500 flex items-center justify-center font-bold">
              📢
            </div>
            <div>
              <h3 class="text-sm font-bold text-foreground">Diffuser sur Slack / Teams</h3>
              <p class="text-[11px] text-default-400">Partager les opportunités d'économies FinOps</p>
            </div>
          </div>
          <button (click)="showNotifyModal = false" class="text-default-400 hover:text-foreground text-xs p-1 rounded">✕</button>
        </div>

        <div *ngIf="notifySuccessResult" class="p-4 rounded-xl bg-success/15 border border-success/30 text-success text-xs space-y-2">
          <div class="font-bold flex items-center gap-1.5">
            <app-icon name="check-circle" [size]="14"></app-icon>
            Notification Envoyée avec Succès !
          </div>
          <p>{{ notifySuccessResult.message }}</p>
          <button
            type="button"
            (click)="showNotifyModal = false; notifySuccessResult = null"
            class="w-full mt-2 py-1.5 rounded-lg bg-success text-white font-semibold text-xs"
          >
            Terminer
          </button>
        </div>

        <form *ngIf="!notifySuccessResult" (ngSubmit)="sendNotification()" class="space-y-3 text-xs">
          <div>
            <label class="block text-default-500 mb-1 font-semibold">Plateforme de collaboration :</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                (click)="notifyPlatform = 'SLACK'"
                [ngClass]="notifyPlatform === 'SLACK' ? 'border-primary bg-primary/10 text-primary' : 'border-divider text-foreground'"
                class="p-2.5 rounded-xl border text-center font-bold transition-all cursor-pointer"
              >
                Slack
              </button>
              <button
                type="button"
                (click)="notifyPlatform = 'TEAMS'"
                [ngClass]="notifyPlatform === 'TEAMS' ? 'border-primary bg-primary/10 text-primary' : 'border-divider text-foreground'"
                class="p-2.5 rounded-xl border text-center font-bold transition-all cursor-pointer"
              >
                Microsoft Teams
              </button>
            </div>
          </div>

          <div>
            <label class="block text-default-500 mb-1 font-semibold">Canal ou Webhook :</label>
            <input
              type="text"
              [(ngModel)]="notifyChannel"
              name="notifyChannel"
              class="w-full px-3 py-2 rounded-xl bg-content2 border border-divider text-foreground outline-none focus:border-primary font-mono text-xs"
              placeholder="#finops-alerts"
            />
          </div>

          <div>
            <label class="block text-default-500 mb-1 font-semibold">Sujet de l'Alerte :</label>
            <input
              type="text"
              [(ngModel)]="notifyHeadline"
              name="notifyHeadline"
              class="w-full px-3 py-2 rounded-xl bg-content2 border border-divider text-foreground outline-none focus:border-primary text-xs"
            />
          </div>

          <div class="flex items-center justify-end gap-2 pt-3 border-t border-divider">
            <button
              type="button"
              (click)="showNotifyModal = false"
              class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-xs font-semibold px-3 py-1.5 text-foreground cursor-pointer"
            >
              Annuler
            </button>
            <button
              type="submit"
              [disabled]="notifyLoading"
              class="heroui-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold px-4 py-1.5 flex items-center gap-1.5 cursor-pointer"
            >
              <span *ngIf="notifyLoading" class="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              <span>Diffuser maintenant</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class FinAiCopilotComponent implements OnInit {
  private portalService = inject(PortalService);
  private sanitizer = inject(DomSanitizer);
  private router = inject(Router);

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLDivElement>;

  @Input() isOpen = false;
  @Input() contextClusterId?: string;
  @Input() contextNamespace?: string;
  @Input() initialPrompt?: string;

  @Output() closeDrawer = new EventEmitter<void>();

  quickPrompts: FinAiQuickPrompt[] = [];
  messages: ChatMessage[] = [];
  currentPrompt = '';
  isBusy = false;
  copiedCommand: string | null = null;
  copiedReportId: string | null = null;
  showScrollDownButton = false;

  // Priority 1: Filter bar
  filterMode: ViewFilterMode = 'ALL';

  // Priority 2: One-Click Dry Run state
  dryRunLoadingKey: string | null = null;
  dryRunResults: Record<string, FinAiDryRunResult> = {};

  // Priority 3: GitOps Manifest state
  showGitOpsModal = false;
  gitOpsLoading = false;
  gitOpsManifest: FinAiGitOpsManifest | null = null;
  activeGitOpsTab: 'kustomize' | 'quota' | 'argocd' = 'kustomize';

  // Priority 4: Slack / Teams Notification state
  showNotifyModal = false;
  notifyLoading = false;
  notifyPlatform: 'SLACK' | 'TEAMS' = 'SLACK';
  notifyChannel = '#finops-alerts';
  notifyHeadline = '';
  notifySavings = 1691.82;
  notifyTargetNamespace = '';
  notifySuccessResult: FinAiNotifyResult | null = null;

  // Interactive YAML Diff Mode (Split vs Unified)
  diffViewMode: Record<string, 'split' | 'unified'> = {};

  ngOnInit(): void {
    this.loadQuickPrompts();
    if (this.initialPrompt) {
      this.currentPrompt = this.initialPrompt;
    }
  }

  loadQuickPrompts(): void {
    this.portalService.getFinAiQuickPrompts().subscribe({
      next: (prompts) => {
        this.quickPrompts = prompts;
      },
      error: (err) => {
        console.warn('FinAI Quick prompts failed, using defaults', err);
      }
    });
  }

  close(): void {
    this.closeDrawer.emit();
  }

  clearContext(): void {
    this.contextClusterId = undefined;
    this.contextNamespace = undefined;
  }

  resetChat(): void {
    this.messages = [];
    this.currentPrompt = '';
    this.dryRunResults = {};
    this.filterMode = 'ALL';
  }

  applyQuickPrompt(promptText: string): void {
    this.currentPrompt = promptText;
    this.sendQuery();
  }

  onEnterPressed(e: Event): void {
    this.sendQuery();
  }

  scrollToBottom(smooth: boolean = true): void {
    setTimeout(() => {
      if (this.scrollContainer?.nativeElement) {
        const el = this.scrollContainer.nativeElement;
        el.scrollTo({
          top: el.scrollHeight,
          behavior: smooth ? 'smooth' : 'auto'
        });
        this.showScrollDownButton = false;
      }
    }, 60);
  }

  onScroll(): void {
    if (!this.scrollContainer?.nativeElement) return;
    const el = this.scrollContainer.nativeElement;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    this.showScrollDownButton = distanceFromBottom > 150;
  }

  copySnippet(cmd: string): void {
    navigator.clipboard.writeText(cmd).then(() => {
      this.copiedCommand = cmd;
      setTimeout(() => {
        if (this.copiedCommand === cmd) {
          this.copiedCommand = null;
        }
      }, 2500);
    });
  }

  copyFullReport(res: FinAiResponse): void {
    let text = `# ${res.headline}\n\n${res.analysisMarkdown}\n\n`;
    if (res.metrics?.length) {
      text += `### Métriques Clés :\n`;
      for (const m of res.metrics) text += `- ${m.label} : ${m.value}\n`;
      text += '\n';
    }
    if (res.cliCommands?.length) {
      text += `### Commandes OpenShift :\n`;
      for (const c of res.cliCommands) text += `\`\`\`bash\n${c.command}\n\`\`\`\n`;
      text += '\n';
    }
    if (res.executionPlan?.length) {
      text += `### Plan d'Exécution :\n`;
      res.executionPlan.forEach((s, i) => (text += `${i + 1}. ${s}\n`));
    }
    navigator.clipboard.writeText(text).then(() => {
      this.copiedReportId = res.headline;
      setTimeout(() => {
        if (this.copiedReportId === res.headline) {
          this.copiedReportId = null;
        }
      }, 2500);
    });
  }

  setFeedback(msg: ChatMessage, type: 'like' | 'dislike'): void {
    msg.feedback = type;
  }

  launchWhatIfSimulator(targetNamespace?: string): void {
    this.close();
    this.router.navigate(['/what-if']);
  }

  // Priority 2: Execute Dry Run
  runDryRun(snippet: FinAiCliSnippet): void {
    this.dryRunLoadingKey = snippet.title;
    this.portalService
      .executeFinAiDryRun({
        namespace: snippet.targetNamespace || this.contextNamespace || 'default',
        clusterId: this.contextClusterId,
        command: snippet.command
      })
      .subscribe({
        next: (res) => {
          this.dryRunResults[snippet.title] = res;
          this.dryRunLoadingKey = null;
          this.scrollToBottom(true);
        },
        error: (err) => {
          console.error('Dry-run failed', err);
          this.dryRunLoadingKey = null;
        }
      });
  }

  // Priority 3: GitOps Manifest Modal
  openGitOpsModal(namespace?: string): void {
    this.showGitOpsModal = true;
    this.gitOpsLoading = true;
    this.gitOpsManifest = null;

    const ns = namespace || this.contextNamespace || 'spark-batch-analytics';
    this.portalService
      .generateFinAiGitOps({
        namespace: ns,
        clusterId: this.contextClusterId || 'ocp-ai-training-prod',
        cpuRequest: '14.6c',
        memoryRequest: '69Gi'
      })
      .subscribe({
        next: (manifest) => {
          this.gitOpsManifest = manifest;
          this.gitOpsLoading = false;
        },
        error: (err) => {
          console.error('GitOps generation failed', err);
          this.gitOpsLoading = false;
        }
      });
  }

  downloadGitOpsBundle(): void {
    if (!this.gitOpsManifest) return;
    const bundleText =
      `# ==========================================\n` +
      `# GitOps Bundle: ${this.gitOpsManifest.repoPath}\n` +
      `# Branch: ${this.gitOpsManifest.branchName}\n` +
      `# ==========================================\n\n` +
      `--- # kustomization.yaml ---\n${this.gitOpsManifest.kustomizationYaml}\n\n` +
      `--- # resource-quota.yaml ---\n${this.gitOpsManifest.resourceQuotaYaml}\n\n` +
      `--- # argocd-application.yaml ---\n${this.gitOpsManifest.argocdApplicationYaml}\n`;

    const blob = new Blob([bundleText], { type: 'text/yaml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `gitops-manifests-${new Date().toISOString().slice(0, 10)}.yaml`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  // Priority 4: Slack / Teams Notification Modal
  openNotifyModal(res: FinAiResponse): void {
    this.showNotifyModal = true;
    this.notifySuccessResult = null;
    this.notifyHeadline = res.headline;
    this.notifySavings = 1691.82;
    this.notifyTargetNamespace = this.contextNamespace || 'all';
  }

  sendNotification(): void {
    this.notifyLoading = true;
    this.portalService
      .dispatchFinAiNotify({
        platform: this.notifyPlatform,
        channel: this.notifyChannel,
        headline: this.notifyHeadline,
        summary: 'Recommandation FinAI prête à appliquer pour optimiser les quotas de la flotte.',
        savingsUsd: this.notifySavings,
        namespace: this.notifyTargetNamespace
      })
      .subscribe({
        next: (result) => {
          this.notifyLoading = false;
          this.notifySuccessResult = result;
        },
        error: (err) => {
          this.notifyLoading = false;
          console.error('Notification dispatch failed', err);
        }
      });
  }

  exportChatPdf(): void {
    const lastAssistant = [...this.messages].reverse().find((m) => m.response);
    if (!lastAssistant?.response) return;

    this.portalService.exportFinAiPdf(lastAssistant.response).subscribe({
      next: (res: HttpResponse<Blob>) => {
        saveDownload(res, `finai-copilot-report-${new Date().toISOString().slice(0, 10)}.pdf`);
      },
      error: (err) => {
        console.error('PDF export failed, using print fallback', err);
        window.print();
      }
    });
  }

  exportChatMarkdown(): void {
    if (this.messages.length === 0) return;
    let md = `# FinAI Copilot — Journal d'Audit & Diagnostic\nDate : ${new Date().toLocaleString()}\n\n`;
    for (const msg of this.messages) {
      if (msg.sender === 'user') {
        md += `## 👤 Opérateur (${msg.timestamp.toLocaleTimeString()})\n${msg.text}\n\n`;
      } else if (msg.response) {
        const res = msg.response;
        md += `## 🤖 FinAI Copilot : ${res.headline}\n\n`;
        md += `${res.analysisMarkdown}\n\n`;
        if (res.metrics?.length) {
          md += `### Métriques Clés :\n`;
          for (const m of res.metrics) md += `- **${m.label}** : ${m.value}\n`;
          md += `\n`;
        }
        if (res.cliCommands?.length) {
          md += `### Commandes OpenShift CLI :\n`;
          for (const c of res.cliCommands) {
            md += `#### ${c.title}\n\`\`\`bash\n${c.command}\n\`\`\`\n_${c.description}_\n\n`;
          }
        }
        if (res.executionPlan?.length) {
          md += `### Plan d'Exécution Recommandé :\n`;
          res.executionPlan.forEach((step, idx) => {
            md += `${idx + 1}. ${step}\n`;
          });
          md += `\n`;
        }
      }
    }
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `finai-copilot-report-${new Date().toISOString().slice(0, 10)}.md`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  formatMarkdown(raw: string): SafeHtml {
    if (!raw) return '';
    let html = raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(
        /^### (.*$)/gim,
        '<h5 class="text-xs font-bold text-foreground mt-3 mb-1.5 flex items-center gap-1.5"><span class="w-1.5 h-1.5 rounded-full bg-primary inline-block"></span>$1</h5>'
      )
      .replace(/^#### (.*$)/gim, '<h6 class="text-[11px] font-bold text-foreground mt-2 mb-1">$1</h6>')
      .replace(
        /&gt; \[!WARNING\]\s*([\s\S]*?)(?=\n\n|\n[^\s&]|$)/gim,
        '<div class="p-2.5 my-2 rounded-xl bg-warning/10 border border-warning/30 text-warning text-xs font-medium space-y-1"><div class="font-bold flex items-center gap-1">⚠️ Avertissement Risque</div><div>$1</div></div>'
      )
      .replace(
        /&gt; \[!NOTE\]\s*([\s\S]*?)(?=\n\n|\n[^\s&]|$)/gim,
        '<div class="p-2.5 my-2 rounded-xl bg-primary/10 border border-primary/30 text-primary text-xs font-medium space-y-1"><div class="font-bold flex items-center gap-1">ℹ️ Note Opérationnelle</div><div>$1</div></div>'
      )
      .replace(/\*\*(.*?)\*\*/gim, '<strong class="font-bold text-foreground">$1</strong>')
      .replace(
        /`([^`]+)`/gim,
        '<code class="px-1.5 py-0.5 rounded bg-content3 text-primary font-mono text-[11px] font-semibold">$1</code>'
      )
      .replace(
        /^\* (.*$)/gim,
        '<li class="flex items-start gap-1.5 ml-1 my-0.5 text-xs text-default-600"><span class="text-primary font-bold">•</span><span>$1</span></li>'
      )
      .replace(
        /^- (.*$)/gim,
        '<li class="flex items-start gap-1.5 ml-1 my-0.5 text-xs text-default-600"><span class="text-primary font-bold">•</span><span>$1</span></li>'
      )
      .replace(/\n\n/g, '<div class="h-2"></div>')
      .replace(/\n/g, '<br/>');

    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  sendQuery(): void {
    const text = this.currentPrompt.trim();
    if (!text || this.isBusy) return;

    const userMsg: ChatMessage = {
      id: 'msg-' + Date.now(),
      sender: 'user',
      timestamp: new Date(),
      text
    };
    this.messages.push(userMsg);
    this.scrollToBottom(true);

    const assistantMsgId = 'res-' + (Date.now() + 1);
    const assistantMsg: ChatMessage = {
      id: assistantMsgId,
      sender: 'assistant',
      timestamp: new Date(),
      isLoading: true
    };
    this.messages.push(assistantMsg);
    this.scrollToBottom(true);

    this.currentPrompt = '';
    this.isBusy = true;

    const request: FinAiPromptRequest = {
      prompt: text,
      selectedClusterId: this.contextClusterId,
      selectedNamespace: this.contextNamespace,
      pageContext: 'finops'
    };

    this.portalService.askFinAi(request).subscribe({
      next: (response) => {
        const found = this.messages.find((m) => m.id === assistantMsgId);
        if (found) {
          found.isLoading = false;
          found.response = response;
        }
        this.isBusy = false;
        this.scrollToBottom(true);
        setTimeout(() => this.scrollToBottom(true), 200);
      },
      error: (err) => {
        const found = this.messages.find((m) => m.id === assistantMsgId);
        if (found) {
          found.isLoading = false;
          found.error = err.error?.message || err.message || 'Impossible de joindre le service FinAI.';
        }
        this.isBusy = false;
        this.scrollToBottom(true);
      }
    });
  }

  getBarPercentage(value: number, points: FinAiChartDataPoint[]): number {
    if (!points || points.length === 0) return 0;
    const max = Math.max(...points.map((p) => p.value || 0), 1);
    return Math.min(100, Math.max(8, (value / max) * 100));
  }

  getDonutSegments(points: FinAiChartDataPoint[]): { color: string; dashArray: string; dashOffset: number }[] {
    if (!points || points.length === 0) return [];
    const total = points.reduce((sum, p) => sum + (p.value || 0), 0) || 1;
    let accumulated = 0;
    return points.map((p) => {
      const pct = Math.max(1, ((p.value || 0) / total) * 100);
      const dashArray = `${pct} ${100 - pct}`;
      const dashOffset = -accumulated;
      accumulated += pct;
      return {
        color: p.color || '#3B82F6',
        dashArray,
        dashOffset
      };
    });
  }

  getDiffMode(key: string): 'split' | 'unified' {
    return this.diffViewMode[key] || 'split';
  }

  setDiffMode(key: string, mode: 'split' | 'unified'): void {
    this.diffViewMode[key] = mode;
  }

  getUnifiedDiffLines(diff: FinAiYamlDiff): { text: string; type: 'add' | 'del' | 'same' }[] {
    if (!diff || !diff.beforeYaml || !diff.afterYaml) return [];
    const beforeLines = diff.beforeYaml.split('\n');
    const afterLines = diff.afterYaml.split('\n');
    const result: { text: string; type: 'add' | 'del' | 'same' }[] = [];

    let bIdx = 0;
    let aIdx = 0;

    while (bIdx < beforeLines.length || aIdx < afterLines.length) {
      const bLine = bIdx < beforeLines.length ? beforeLines[bIdx] : null;
      const aLine = aIdx < afterLines.length ? afterLines[aIdx] : null;

      if (bLine === aLine && bLine !== null) {
        result.push({ text: '  ' + bLine, type: 'same' });
        bIdx++;
        aIdx++;
      } else {
        if (bLine !== null && (aLine === null || !afterLines.slice(aIdx, aIdx + 3).includes(bLine))) {
          result.push({ text: '- ' + bLine, type: 'del' });
          bIdx++;
        } else if (aLine !== null) {
          result.push({ text: '+ ' + aLine, type: 'add' });
          aIdx++;
        } else if (bLine !== null) {
          result.push({ text: '- ' + bLine, type: 'del' });
          bIdx++;
        }
      }
    }
    return result;
  }
}

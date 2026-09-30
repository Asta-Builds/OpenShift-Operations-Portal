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
import { Router } from '@angular/router';
import { PortalService } from '../../services/portal.service';
import {
  FinAiCliSnippet,
  FinAiMetricItem,
  FinAiPromptRequest,
  FinAiQuickPrompt,
  FinAiResponse
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
          <!-- Export Chat to Markdown -->
          <button
            type="button"
            *ngIf="messages.length > 0"
            (click)="exportChatMarkdown()"
            title="Exporter la conversation en rapport Markdown"
            class="p-2 rounded-xl text-default-400 hover:text-foreground hover:bg-content2 transition-colors cursor-pointer flex items-center gap-1 text-xs"
          >
            <app-icon name="download" [size]="15"></app-icon>
            <span class="hidden sm:inline text-[11px] font-medium">Export</span>
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

                <!-- Highlight Metrics Strip -->
                <div *ngIf="res.metrics && res.metrics.length > 0" class="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-1">
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

                <!-- Detailed Analysis (Rich Markdown formatted) -->
                <div *ngIf="res.analysisMarkdown" class="space-y-1.5 pt-2 border-t border-divider">
                  <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                    <app-icon name="activity" [size]="13" className="text-secondary"></app-icon>
                    Analyse Détaillée & Justification
                  </h4>
                  <div
                    class="text-xs text-default-600 leading-relaxed rounded-xl bg-content1/70 p-3.5 border border-divider prose-sm"
                    [innerHTML]="formatMarkdown(res.analysisMarkdown)"
                  ></div>
                </div>

                <!-- Actionable Execution Steps -->
                <div *ngIf="res.executionPlan && res.executionPlan.length > 0" class="space-y-2 pt-2 border-t border-divider">
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

                <!-- OpenShift CLI Snippets with 1-click Copy & Simulator Link -->
                <div *ngIf="res.cliCommands && res.cliCommands.length > 0" class="space-y-2.5 pt-2 border-t border-divider">
                  <div class="flex items-center justify-between">
                    <h4 class="text-xs font-bold text-foreground flex items-center gap-1.5">
                      <app-icon name="terminal" [size]="13" className="text-warning"></app-icon>
                      Commandes CLI <code class="text-foreground font-mono">oc</code> Prêtes à l'Emploi
                    </h4>
                    <span class="text-[10px] font-bold text-default-400">Air-Gapped Ready</span>
                  </div>

                  <div *ngFor="let snippet of res.cliCommands" class="rounded-xl overflow-hidden border border-divider bg-[#0d1117] text-[#c9d1d9] shadow-inner">
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

                    <!-- Snippet Note & Action Footer -->
                    <div class="px-3 py-2 bg-[#161b22]/70 border-t border-[#30363d] flex items-center justify-between text-[10px]">
                      <span class="text-[#8b949e]">{{ snippet.description }}</span>
                      <button
                        type="button"
                        (click)="launchWhatIfSimulator(snippet.targetNamespace)"
                        class="text-primary hover:text-primary-foreground px-2 py-0.5 rounded bg-primary/10 hover:bg-primary transition-all font-semibold flex items-center gap-1 cursor-pointer"
                        title="Tester le dimensionnement dans le simulateur What-If"
                      >
                        <app-icon name="sliders" [size]="11"></app-icon>
                        <span>Simuler dans What-If</span>
                      </button>
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

                <!-- Footer Feedback & Copy All Report -->
                <div class="pt-2 border-t border-divider flex items-center justify-between text-[10px] text-default-400">
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
          <span class="font-mono">FinAI v1.2</span>
        </div>
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
  }

  applyQuickPrompt(promptText: string): void {
    this.currentPrompt = promptText;
    this.sendQuery();
  }

  onEnterPressed(e: Event): void {
    // Regular enter triggers form submission
    this.sendQuery();
  }

  /**
   * Automatically scroll the chat container to the bottom smoothly
   */
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
    // Show button if user has scrolled up by more than 150px
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
      // Escape HTML special characters
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      // Headers
      .replace(
        /^### (.*$)/gim,
        '<h5 class="text-xs font-bold text-foreground mt-3 mb-1.5 flex items-center gap-1.5"><span class="w-1.5 h-1.5 rounded-full bg-primary inline-block"></span>$1</h5>'
      )
      .replace(/^#### (.*$)/gim, '<h6 class="text-[11px] font-bold text-foreground mt-2 mb-1">$1</h6>')
      // Alerts
      .replace(
        /&gt; \[!WARNING\]\s*([\s\S]*?)(?=\n\n|\n[^\s&]|$)/gim,
        '<div class="p-2.5 my-2 rounded-xl bg-warning/10 border border-warning/30 text-warning text-xs font-medium space-y-1"><div class="font-bold flex items-center gap-1">⚠️ Avertissement Risque</div><div>$1</div></div>'
      )
      .replace(
        /&gt; \[!NOTE\]\s*([\s\S]*?)(?=\n\n|\n[^\s&]|$)/gim,
        '<div class="p-2.5 my-2 rounded-xl bg-primary/10 border border-primary/30 text-primary text-xs font-medium space-y-1"><div class="font-bold flex items-center gap-1">ℹ️ Note Opérationnelle</div><div>$1</div></div>'
      )
      // Bold
      .replace(/\*\*(.*?)\*\*/gim, '<strong class="font-bold text-foreground">$1</strong>')
      // Inline Code
      .replace(
        /`([^`]+)`/gim,
        '<code class="px-1.5 py-0.5 rounded bg-content3 text-primary font-mono text-[11px] font-semibold">$1</code>'
      )
      // Bullet list items
      .replace(
        /^\* (.*$)/gim,
        '<li class="flex items-start gap-1.5 ml-1 my-0.5 text-xs text-default-600"><span class="text-primary font-bold">•</span><span>$1</span></li>'
      )
      .replace(
        /^- (.*$)/gim,
        '<li class="flex items-start gap-1.5 ml-1 my-0.5 text-xs text-default-600"><span class="text-primary font-bold">•</span><span>$1</span></li>'
      )
      // Line breaks
      .replace(/\n\n/g, '<div class="h-2"></div>')
      .replace(/\n/g, '<br/>');

    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  sendQuery(): void {
    const text = this.currentPrompt.trim();
    if (!text || this.isBusy) return;

    // Add user message
    const userMsg: ChatMessage = {
      id: 'msg-' + Date.now(),
      sender: 'user',
      timestamp: new Date(),
      text
    };
    this.messages.push(userMsg);

    // Auto-scroll immediately when user sends
    this.scrollToBottom(true);

    // Add assistant loading message
    const assistantMsgId = 'res-' + (Date.now() + 1);
    const assistantMsg: ChatMessage = {
      id: assistantMsgId,
      sender: 'assistant',
      timestamp: new Date(),
      isLoading: true
    };
    this.messages.push(assistantMsg);

    // Auto-scroll to show loading spinner
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
        // Auto-scroll to bottom once rich response arrives
        this.scrollToBottom(true);
        // Repeat scroll after 200ms once all DOM nodes (code blocks, badges) are rendered
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
}

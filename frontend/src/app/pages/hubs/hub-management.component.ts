import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PortalService } from '../../services/portal.service';
import { AuthService } from '../../services/auth.service';
import {
  AcmHubSummary,
  HubCheckStatus,
  HubCheckTarget,
  HubConnectionTest,
  HubSettings,
  HubStatus,
  HubSyncRun,
  SyncStatus
} from '../../models/portal.models';
import { IconComponent } from '../../shared/icon.component';

type HubAction = 'test' | 'collect' | 'delete';

@Component({
  selector: 'app-hub-management',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  template: `
    <div class="space-y-6">

      <!-- Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">ACM Hubs</h1>
            <span class="px-2 py-0.5 text-xs font-semibold rounded-full bg-primary/10 text-primary border border-primary/20">
              {{ hubs.length }} registered
            </span>
          </div>
          <p class="text-xs text-default-500 mt-1">
            The hubs the portal collects clusters from, how their collections went, and their connection settings
          </p>
        </div>
        <div class="flex items-center gap-2">
          <button type="button" (click)="load()" class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs py-1.5">
            <app-icon name="refresh" [size]="13"></app-icon>
            <span>Refresh</span>
          </button>
          <button *ngIf="auth.hasRole('OPERATOR') && hubs.length" type="button" (click)="collectAll()" [disabled]="collectingAll"
                  data-testid="collect-all"
                  class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs py-1.5 disabled:opacity-50">
            <app-icon name="activity" [size]="13"></app-icon>
            <span>{{ collectingAll ? 'Collecting…' : 'Collect all now' }}</span>
          </button>
          <button *ngIf="auth.hasRole('ADMIN')" type="button" (click)="openRegister()" data-testid="register-hub"
                  class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 text-xs font-semibold py-1.5">
            <app-icon name="plus" [size]="13"></app-icon>
            <span>Register hub</span>
          </button>
        </div>
      </div>

      <div *ngIf="message" class="p-3 rounded-2xl text-xs font-medium" data-testid="hub-message"
           [ngClass]="messageIsError ? 'bg-danger/10 border border-danger/30 text-danger' : 'bg-success/10 border border-success/30 text-success'">
        {{ message }}
      </div>

      <!-- Register or edit (admins) -->
      <form *ngIf="formMode" (ngSubmit)="save()" class="heroui-card p-6 space-y-4" data-testid="hub-form">
        <div class="pb-3 border-b border-divider">
          <h3 class="text-base font-bold text-foreground">{{ formMode === 'register' ? 'Register a hub' : 'Edit ' + form.name }}</h3>
          <p class="text-xs text-default-400 mt-0.5 leading-relaxed">
            The token is never entered here. Create a Secret with the keys <code class="font-mono">token</code> and, for a private CA,
            <code class="font-mono">ca.crt</code> in the portal's namespace, and mount it at
            <code class="font-mono">/var/run/secrets/acm-hubs/&lt;Secret name&gt;/</code> (deployment guide, step 3).
          </p>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <label class="text-xs text-default-500 space-y-1">
            <span>Name</span>
            <input name="name" [(ngModel)]="form.name" required maxlength="255" [disabled]="formMode === 'edit'" placeholder="hub-east"
                   class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground outline-none focus:border-primary disabled:opacity-60" />
          </label>
          <label class="text-xs text-default-500 space-y-1">
            <span>Credentials Secret</span>
            <input name="credentialsSecretRef" [(ngModel)]="form.credentialsSecretRef" required placeholder="hub-east-credentials"
                   class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-primary" />
          </label>
          <label class="text-xs text-default-500 space-y-1 md:col-span-2">
            <span>API server</span>
            <input name="apiUrl" [(ngModel)]="form.apiUrl" required placeholder="https://api.hub-east.example.com:6443"
                   class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-primary" />
          </label>
          <label class="text-xs text-default-500 space-y-1">
            <span>Observability (optional): requests and usage per namespace</span>
            <input name="observabilityUrl" [(ngModel)]="form.observabilityUrl"
                   placeholder="https://rbac-query-proxy-open-cluster-management-observability.apps.hub-east.example.com"
                   class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-primary" />
          </label>
          <label class="text-xs text-default-500 space-y-1">
            <span>Search (optional): namespace labels, and so ownership</span>
            <input name="searchUrl" [(ngModel)]="form.searchUrl"
                   placeholder="https://search-api-open-cluster-management.apps.hub-east.example.com/searchapi/graphql"
                   class="w-full px-3 py-2 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-primary" />
          </label>
        </div>

        <ng-container *ngTemplateOutlet="checkList; context: { $implicit: formTest }"></ng-container>

        <div class="flex flex-wrap justify-end gap-2">
          <button type="button" (click)="cancelForm()" class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs py-2">Cancel</button>
          <button type="button" (click)="testForm()" [disabled]="formTesting || saving" data-testid="test-settings"
                  class="heroui-btn bg-content2 hover:bg-content3 border border-divider text-foreground text-xs py-2 disabled:opacity-50">
            {{ formTesting ? 'Testing…' : 'Test connection' }}
          </button>
          <button type="submit" [disabled]="saving" class="heroui-btn bg-primary text-primary-foreground hover:bg-primary-600 text-xs font-semibold py-2 disabled:opacity-50">
            {{ saving ? 'Saving…' : formMode === 'register' ? 'Register' : 'Save changes' }}
          </button>
        </div>
      </form>

      <p *ngIf="loadError" class="text-xs text-danger">{{ loadError }}</p>

      <!-- One card per hub -->
      <div *ngFor="let hub of hubs; trackBy: trackHub" class="heroui-card p-6 space-y-4" [attr.data-testid]="'hub-' + hub.name">
        <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
              <app-icon name="network" [size]="20"></app-icon>
            </div>
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="text-base font-bold text-foreground">{{ hub.name }}</h3>
                <span class="heroui-badge text-[10px]" [ngClass]="hubStatusClass(hub.status)" [title]="hubStatusHint(hub.status)">{{ hubStatusLabel(hub.status) }}</span>
                <span *ngIf="hub.circuitBreakerState !== 'CLOSED'" class="heroui-badge text-[10px]" data-testid="breaker"
                      [ngClass]="hub.circuitBreakerState === 'OPEN' ? 'bg-danger/15 text-danger' : 'bg-warning/15 text-warning'"
                      title="After repeated failures, collections stop calling the hub for a while, then try one call">
                  {{ hub.circuitBreakerState === 'OPEN' ? 'Calls paused (circuit open)' : 'Retrying (circuit half-open)' }}
                </span>
              </div>
              <div class="text-[11px] text-default-400 mt-0.5">
                {{ hub.clusterCount }} {{ hub.clusterCount === 1 ? 'cluster' : 'clusters' }} ·
                {{ hub.lastSyncTimestamp ? 'data collected ' + (hub.lastSyncTimestamp | date: 'yyyy-MM-dd HH:mm') : 'no data collected yet' }}
                <span *ngIf="hub.consecutiveFailures > 0" class="text-danger"> · {{ hub.consecutiveFailures }} failed {{ hub.consecutiveFailures === 1 ? 'collection' : 'collections' }} in a row</span>
              </div>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-1.5">
            <ng-container *ngIf="auth.hasRole('OPERATOR')">
              <button type="button" (click)="testHub(hub)" [disabled]="!!busy[hub.id]" data-testid="test-hub"
                      class="px-2.5 py-1 rounded-lg bg-content3 hover:bg-content4 text-default-600 text-[11px] font-semibold disabled:opacity-50">
                {{ busy[hub.id] === 'test' ? 'Testing…' : 'Test connection' }}
              </button>
              <button type="button" (click)="collectHub(hub)" [disabled]="!!busy[hub.id]" data-testid="collect-hub"
                      class="px-2.5 py-1 rounded-lg bg-primary/10 text-primary hover:bg-primary/20 text-[11px] font-semibold disabled:opacity-50">
                {{ busy[hub.id] === 'collect' ? 'Collecting…' : 'Collect now' }}
              </button>
            </ng-container>
            <button type="button" (click)="toggleHistory(hub)" data-testid="history-toggle"
                    class="px-2.5 py-1 rounded-lg bg-content3 hover:bg-content4 text-default-600 text-[11px] font-semibold">
              {{ history[hub.id] ? 'Hide history' : 'History' }}
            </button>
            <ng-container *ngIf="auth.hasRole('ADMIN')">
              <button type="button" (click)="openEdit(hub)" data-testid="edit-hub"
                      class="px-2.5 py-1 rounded-lg bg-content3 hover:bg-content4 text-default-600 text-[11px] font-semibold">Edit</button>
              <button type="button" (click)="askDelete(hub)" [disabled]="!!busy[hub.id]" data-testid="delete-hub"
                      class="px-2.5 py-1 rounded-lg bg-danger/10 text-danger hover:bg-danger/20 text-[11px] font-semibold disabled:opacity-50">Delete</button>
            </ng-container>
          </div>
        </div>

        <!-- Settings -->
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
          <div class="p-3 rounded-xl bg-content2/60 border border-divider">
            <div class="text-[10px] uppercase tracking-wider text-default-400">API server</div>
            <div class="text-xs font-mono text-foreground mt-1 break-all">{{ hub.apiUrl }}</div>
          </div>
          <div class="p-3 rounded-xl bg-content2/60 border border-divider" data-testid="credentials">
            <div class="text-[10px] uppercase tracking-wider text-default-400">Credentials Secret</div>
            <div class="text-xs font-mono text-foreground mt-1 break-all">{{ hub.credentialsSecretRef || '—' }}</div>
            <div class="text-[11px] mt-1 flex items-center gap-1" [ngClass]="credentialsClass(hub)">
              <app-icon [name]="hub.credentialsMounted === false ? 'x-circle' : hub.credentialsMounted ? 'check-circle' : 'minus-circle'" [size]="12"></app-icon>
              {{ credentialsLabel(hub) }}
            </div>
          </div>
          <div class="p-3 rounded-xl bg-content2/60 border border-divider">
            <div class="text-[10px] uppercase tracking-wider text-default-400">Observability</div>
            <div class="text-xs mt-1 break-all" [ngClass]="hub.observabilityUrl ? 'font-mono text-foreground' : 'text-default-400'">
              {{ hub.observabilityUrl || 'Not configured: no requests or usage' }}
            </div>
          </div>
          <div class="p-3 rounded-xl bg-content2/60 border border-divider">
            <div class="text-[10px] uppercase tracking-wider text-default-400">Search</div>
            <div class="text-xs mt-1 break-all" [ngClass]="hub.searchUrl ? 'font-mono text-foreground' : 'text-default-400'">
              {{ hub.searchUrl || 'Not configured: no namespace ownership' }}
            </div>
          </div>
        </div>

        <!-- Latest collection -->
        <div class="flex flex-wrap items-center gap-2 text-xs" data-testid="latest-run">
          <span class="text-default-400">Last collection:</span>
          <ng-container *ngIf="hub.latestSyncRun as run; else notCollected">
            <span class="heroui-badge text-[10px]" [ngClass]="syncStatusClass(run.status)">{{ syncStatusLabel(run.status) }}</span>
            <span class="text-default-500">{{ run.startedAt | date: 'yyyy-MM-dd HH:mm' }}</span>
            <span class="text-default-500">· {{ run.clustersOk }} collected<span *ngIf="run.clustersFailed">, {{ run.clustersFailed }} failed</span></span>
            <span *ngIf="run.errorMessage" class="text-danger break-words">· {{ run.errorMessage }}</span>
          </ng-container>
          <ng-template #notCollected><span class="text-default-500">not collected yet</span></ng-template>
        </div>

        <ng-container *ngTemplateOutlet="checkList; context: { $implicit: tests[hub.id] }"></ng-container>

        <!-- Delete confirmation -->
        <div *ngIf="confirmDeleteId === hub.id" class="p-4 rounded-2xl bg-danger/5 border border-danger/30 space-y-3" data-testid="delete-confirm">
          <p class="text-xs text-foreground leading-relaxed">
            Deleting <strong>{{ hub.name }}</strong> also deletes its {{ hub.clusterCount }} {{ hub.clusterCount === 1 ? 'cluster' : 'clusters' }}
            with their snapshots and history. Type the hub's name to confirm.
          </p>
          <div class="flex flex-wrap gap-2">
            <input [(ngModel)]="confirmDeleteText" [placeholder]="hub.name" data-testid="delete-confirm-name"
                   class="px-3 py-1.5 rounded-xl bg-content1 border border-divider text-xs text-foreground font-mono outline-none focus:border-danger" />
            <button type="button" (click)="deleteHub(hub)" [disabled]="confirmDeleteText !== hub.name || !!busy[hub.id]" data-testid="delete-confirm-button"
                    class="px-3 py-1.5 rounded-xl bg-danger text-white text-xs font-semibold disabled:opacity-40">Delete hub</button>
            <button type="button" (click)="confirmDeleteId = null"
                    class="px-3 py-1.5 rounded-xl bg-content3 hover:bg-content4 text-default-600 text-xs font-semibold">Cancel</button>
          </div>
        </div>

        <!-- Collection history -->
        <div *ngIf="history[hub.id] as runs" class="w-full overflow-x-auto" data-testid="sync-history">
          <p *ngIf="!runs.length" class="text-xs text-default-500">No collections yet.</p>
          <table *ngIf="runs.length" class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-divider text-default-400 uppercase tracking-wider text-[10px]">
                <th class="py-2 px-3">Started</th>
                <th class="py-2 px-3">Result</th>
                <th class="py-2 px-3">Clusters</th>
                <th class="py-2 px-3">Calls</th>
                <th class="py-2 px-3">Duration</th>
                <th class="py-2 px-3">Details</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-divider/40">
              <tr *ngFor="let run of runs; trackBy: trackRun">
                <td class="py-2 px-3 text-default-500 whitespace-nowrap">{{ run.startedAt | date: 'yyyy-MM-dd HH:mm:ss' }}</td>
                <td class="py-2 px-3"><span class="heroui-badge text-[10px]" [ngClass]="syncStatusClass(run.status)">{{ syncStatusLabel(run.status) }}</span></td>
                <td class="py-2 px-3 text-default-600">{{ run.clustersOk }} ok<span *ngIf="run.clustersFailed" class="text-danger"> / {{ run.clustersFailed }} failed</span></td>
                <td class="py-2 px-3 text-default-600">{{ run.attempts }}</td>
                <td class="py-2 px-3 text-default-600">{{ duration(run) }}</td>
                <td class="py-2 px-3 text-default-500 max-w-[420px] break-words">{{ run.errorMessage || '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- No hubs yet -->
      <div *ngIf="loaded && !hubs.length" class="heroui-card p-8 text-center space-y-2" data-testid="no-hubs">
        <div class="w-12 h-12 rounded-2xl bg-primary/10 text-primary flex items-center justify-center mx-auto">
          <app-icon name="network" [size]="22"></app-icon>
        </div>
        <h3 class="text-base font-bold text-foreground">No hubs registered</h3>
        <p class="text-xs text-default-500 max-w-md mx-auto leading-relaxed">
          The portal finds clusters through ACM hubs.
          {{ auth.hasRole('ADMIN') ? 'Register a hub once its token Secret is mounted into the portal.' : 'An administrator registers them.' }}
        </p>
      </div>
    </div>

    <!-- Result of a connection test -->
    <ng-template #checkList let-test>
      <div *ngIf="test" class="p-4 rounded-2xl bg-content2/50 border border-divider space-y-2" data-testid="connection-test">
        <div class="text-xs font-semibold flex items-center gap-1.5" [ngClass]="testHeadlineClass(test)">
          <app-icon [name]="test.ok ? 'check-circle' : 'x-circle'" [size]="14"></app-icon>
          {{ testHeadline(test) }}
        </div>
        <div *ngFor="let check of test.checks" class="flex items-start gap-2 text-xs">
          <app-icon [name]="checkIcon(check.status)" [size]="14" [className]="checkClass(check.status) + ' mt-px shrink-0'"></app-icon>
          <span class="w-28 shrink-0 font-semibold text-foreground">{{ targetLabel(check.target) }}</span>
          <span class="text-default-500 break-words flex-1">{{ check.message }}</span>
          <span *ngIf="check.durationMs" class="text-[10px] text-default-400 whitespace-nowrap">{{ check.durationMs }} ms</span>
        </div>
      </div>
    </ng-template>
  `
})
export class HubManagementComponent implements OnInit {
  private portalService = inject(PortalService);
  auth = inject(AuthService);

  hubs: AcmHubSummary[] = [];
  loaded = false;
  loadError = '';
  message = '';
  messageIsError = false;

  formMode: 'register' | 'edit' | null = null;
  private editingId: string | null = null;
  form: HubSettings = this.emptyForm();
  formTest: HubConnectionTest | null = null;
  formTesting = false;
  saving = false;

  collectingAll = false;
  /** The action running for each hub. */
  busy: Record<string, HubAction | undefined> = {};
  /** Latest connection test of each hub. */
  tests: Record<string, HubConnectionTest | undefined> = {};
  /** Collection history of the hubs whose history is open. */
  history: Record<string, HubSyncRun[] | undefined> = {};
  confirmDeleteId: string | null = null;
  confirmDeleteText = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.portalService.getHubs().subscribe({
      next: (hubs) => {
        this.hubs = hubs;
        this.loaded = true;
        this.loadError = '';
        for (const id of Object.keys(this.history)) {
          if (this.history[id]) {
            this.loadHistory(id);
          }
        }
      },
      error: (err) => (this.loadError = 'Could not load the hubs: ' + this.errorText(err))
    });
  }

  openRegister(): void {
    this.formMode = 'register';
    this.editingId = null;
    this.form = this.emptyForm();
    this.formTest = null;
    this.message = '';
  }

  openEdit(hub: AcmHubSummary): void {
    this.formMode = 'edit';
    this.editingId = hub.id;
    this.form = {
      name: hub.name,
      apiUrl: hub.apiUrl,
      credentialsSecretRef: hub.credentialsSecretRef ?? '',
      observabilityUrl: hub.observabilityUrl ?? '',
      searchUrl: hub.searchUrl ?? ''
    };
    this.formTest = null;
    this.message = '';
  }

  cancelForm(): void {
    this.formMode = null;
    this.editingId = null;
    this.formTest = null;
  }

  testForm(): void {
    this.formTesting = true;
    this.formTest = null;
    this.portalService.testHubSettings(this.form).subscribe({
      next: (result) => {
        this.formTesting = false;
        this.formTest = result;
      },
      error: (err) => {
        this.formTesting = false;
        this.show(this.errorText(err), true);
      }
    });
  }

  save(): void {
    this.saving = true;
    const request = this.formMode === 'edit' && this.editingId
      ? this.portalService.updateHub(this.editingId, this.form)
      : this.portalService.registerHub(this.form);
    const registering = this.formMode === 'register';
    request.subscribe({
      next: (hub) => {
        this.saving = false;
        this.cancelForm();
        this.show(registering
          ? `Hub ${hub.name} registered; the next collection discovers its clusters.`
          : `Hub ${hub.name} updated.`, false);
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.show(this.errorText(err), true);
      }
    });
  }

  testHub(hub: AcmHubSummary): void {
    this.busy[hub.id] = 'test';
    this.tests[hub.id] = undefined;
    this.portalService.testHub(hub.id).subscribe({
      next: (result) => {
        this.busy[hub.id] = undefined;
        this.tests[hub.id] = result;
      },
      error: (err) => {
        this.busy[hub.id] = undefined;
        this.show(this.errorText(err), true);
      }
    });
  }

  collectHub(hub: AcmHubSummary): void {
    this.busy[hub.id] = 'collect';
    this.portalService.collectHub(hub.id).subscribe({
      next: (run) => {
        this.busy[hub.id] = undefined;
        const failed = run.status === 'FAILED' || run.status === 'SKIPPED_CIRCUIT_OPEN';
        this.show(`${hub.name}: ${this.syncStatusLabel(run.status).toLowerCase()}, ${run.clustersOk} clusters collected`
          + (run.clustersFailed ? `, ${run.clustersFailed} failed` : '')
          + (run.errorMessage ? `. ${run.errorMessage}` : '.'), failed);
        this.load();
      },
      error: (err) => {
        this.busy[hub.id] = undefined;
        this.show(this.errorText(err), true);
      }
    });
  }

  collectAll(): void {
    this.collectingAll = true;
    this.portalService.triggerCollection().subscribe({
      next: (result) => {
        this.collectingAll = false;
        this.show(result.message, result.status !== 'COMPLETED');
        this.load();
      },
      error: (err) => {
        this.collectingAll = false;
        this.show(this.errorText(err), true);
      }
    });
  }

  toggleHistory(hub: AcmHubSummary): void {
    if (this.history[hub.id]) {
      this.history[hub.id] = undefined;
    } else {
      this.loadHistory(hub.id);
    }
  }

  askDelete(hub: AcmHubSummary): void {
    this.confirmDeleteId = hub.id;
    this.confirmDeleteText = '';
  }

  deleteHub(hub: AcmHubSummary): void {
    if (this.confirmDeleteText !== hub.name) {
      return;
    }
    this.busy[hub.id] = 'delete';
    this.portalService.deleteHub(hub.id).subscribe({
      next: () => {
        this.busy[hub.id] = undefined;
        this.confirmDeleteId = null;
        this.history[hub.id] = undefined;
        this.tests[hub.id] = undefined;
        this.show(`Hub ${hub.name} and its clusters were deleted.`, false);
        this.load();
      },
      error: (err) => {
        this.busy[hub.id] = undefined;
        this.show(this.errorText(err), true);
      }
    });
  }

  trackHub(_: number, hub: AcmHubSummary): string {
    return hub.id;
  }

  trackRun(_: number, run: HubSyncRun): number {
    return run.id;
  }

  hubStatusLabel(status: HubStatus): string {
    switch (status) {
      case 'ACTIVE': return 'Active';
      case 'DEGRADED': return 'Degraded';
      case 'UNREACHABLE': return 'Unreachable';
      case 'ERROR': return 'Error';
    }
  }

  hubStatusHint(status: HubStatus): string {
    switch (status) {
      case 'ACTIVE': return 'The last collection read every cluster';
      case 'DEGRADED': return 'The hub answered, but some clusters could not be collected';
      case 'UNREACHABLE': return 'The hub could not be reached, or calls to it are paused';
      case 'ERROR': return 'The hub refused the request: check the token and its permissions';
    }
  }

  hubStatusClass(status: HubStatus): string {
    switch (status) {
      case 'ACTIVE': return 'bg-success/15 text-success';
      case 'DEGRADED': return 'bg-warning/15 text-warning';
      case 'UNREACHABLE':
      case 'ERROR': return 'bg-danger/15 text-danger';
    }
  }

  syncStatusLabel(status: SyncStatus): string {
    switch (status) {
      case 'SUCCESS': return 'Succeeded';
      case 'PARTIAL': return 'Partial';
      case 'FAILED': return 'Failed';
      case 'SKIPPED_CIRCUIT_OPEN': return 'Skipped (circuit open)';
    }
  }

  syncStatusClass(status: SyncStatus): string {
    switch (status) {
      case 'SUCCESS': return 'bg-success/15 text-success';
      case 'PARTIAL': return 'bg-warning/15 text-warning';
      case 'FAILED': return 'bg-danger/15 text-danger';
      case 'SKIPPED_CIRCUIT_OPEN': return 'bg-content3 text-default-600';
    }
  }

  credentialsLabel(hub: AcmHubSummary): string {
    if (hub.credentialsMounted === null) return 'Simulated hub: no Secret is read';
    return hub.credentialsMounted ? 'Token mounted' : 'Token not mounted in the portal';
  }

  credentialsClass(hub: AcmHubSummary): string {
    if (hub.credentialsMounted === null) return 'text-default-400';
    return hub.credentialsMounted ? 'text-success' : 'text-danger';
  }

  targetLabel(target: HubCheckTarget): string {
    switch (target) {
      case 'CREDENTIALS': return 'Credentials';
      case 'API': return 'Hub API';
      case 'OBSERVABILITY': return 'Observability';
      case 'SEARCH': return 'Search';
    }
  }

  checkIcon(status: HubCheckStatus): string {
    switch (status) {
      case 'OK': return 'check-circle';
      case 'WARNING': return 'alert-triangle';
      case 'FAILED': return 'x-circle';
      case 'SKIPPED': return 'minus-circle';
    }
  }

  checkClass(status: HubCheckStatus): string {
    switch (status) {
      case 'OK': return 'text-success';
      case 'WARNING': return 'text-warning';
      case 'FAILED': return 'text-danger';
      case 'SKIPPED': return 'text-default-400';
    }
  }

  testHeadline(test: HubConnectionTest): string {
    if (!test.ok) return 'Connection test failed';
    return test.checks.some((check) => check.status === 'WARNING') ? 'Connected, with warnings' : 'Connection works';
  }

  testHeadlineClass(test: HubConnectionTest): string {
    if (!test.ok) return 'text-danger';
    return test.checks.some((check) => check.status === 'WARNING') ? 'text-warning' : 'text-success';
  }

  duration(run: HubSyncRun): string {
    if (!run.finishedAt) return '—';
    const ms = new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime();
    return ms < 1000 ? `${Math.max(ms, 0)} ms` : `${(ms / 1000).toFixed(1)} s`;
  }

  private loadHistory(id: string): void {
    this.portalService.getHubSyncRuns(id).subscribe({
      next: (runs) => (this.history[id] = runs),
      error: (err) => this.show(this.errorText(err), true)
    });
  }

  private show(text: string, isError: boolean): void {
    this.message = text;
    this.messageIsError = isError;
  }

  private errorText(err: any): string {
    return err?.error?.message || err?.message || 'The request failed';
  }

  private emptyForm(): HubSettings {
    return { name: '', apiUrl: '', credentialsSecretRef: '', observabilityUrl: '', searchUrl: '' };
  }
}

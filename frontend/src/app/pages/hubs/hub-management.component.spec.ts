import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HubManagementComponent } from './hub-management.component';
import { AuthService } from '../../services/auth.service';
import { AcmHubSummary, HubConnectionTest, HubSyncRun, PortalRole } from '../../models/portal.models';

describe('HubManagementComponent', () => {
  let fixture: ComponentFixture<HubManagementComponent>;
  let http: HttpTestingController;
  let roles: PortalRole[] = [];

  const failedRun: HubSyncRun = {
    id: 42,
    status: 'FAILED',
    startedAt: '2026-09-29T10:15:00',
    finishedAt: '2026-09-29T10:15:06',
    attempts: 3,
    clustersOk: 0,
    clustersFailed: 0,
    errorMessage: 'ACM Hub hub-east refused the token (HTTP 403)'
  };

  const hub: AcmHubSummary = {
    id: 'h1',
    name: 'hub-east',
    apiUrl: 'https://api.hub-east.example.com:6443',
    credentialsSecretRef: 'hub-east-credentials',
    observabilityUrl: null,
    searchUrl: 'https://search.hub-east.example.com/searchapi/graphql',
    status: 'ERROR',
    lastSyncTimestamp: '2026-09-28T22:00:00',
    consecutiveFailures: 4,
    circuitBreakerState: 'OPEN',
    clusterCount: 12,
    credentialsMounted: false,
    latestSyncRun: failedRun
  };

  function render(granted: PortalRole[]): HTMLElement {
    roles = granted;
    fixture = TestBed.createComponent(HubManagementComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/hubs').flush([hub]);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function click(page: HTMLElement, testId: string): void {
    (page.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function text(page: HTMLElement, testId: string): string {
    return page.querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HubManagementComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { hasRole: (role: PortalRole) => roles.includes(role) } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows why a hub is failing: status, paused calls, missing token and the last error', () => {
    const page = render(['VIEWER']);
    const card = text(page, 'hub-hub-east');

    expect(card).toContain('Error');
    expect(card).toContain('12 clusters');
    expect(card).toContain('4 failed collections in a row');
    expect(text(page, 'breaker')).toContain('Calls paused (circuit open)');
    expect(text(page, 'credentials')).toContain('Token not mounted in the portal');
    expect(card).toContain('Not configured: no requests or usage');
    expect(text(page, 'latest-run')).toContain('Failed');
    expect(text(page, 'latest-run')).toContain('refused the token (HTTP 403)');
  });

  it('lets viewers read the history but not act on hubs', () => {
    const page = render(['VIEWER']);

    for (const action of ['register-hub', 'collect-all', 'test-hub', 'collect-hub', 'edit-hub', 'delete-hub']) {
      expect(page.querySelector(`[data-testid="${action}"]`)).withContext(action).toBeNull();
    }
    click(page, 'history-toggle');
    const request = http.expectOne((req) => req.url === '/api/v1/hubs/h1/sync-runs');
    expect(request.request.params.get('limit')).toBe('20');
    request.flush([failedRun]);
    fixture.detectChanges();

    const history = text(page, 'sync-history');
    expect(history).toContain('2026-09-29 10:15:00');
    expect(history).toContain('6.0 s');
    expect(history).toContain('refused the token');
  });

  it('shows operators each check of a connection test', () => {
    const page = render(['OPERATOR', 'VIEWER']);
    expect(page.querySelector('[data-testid="edit-hub"]')).toBeNull();

    click(page, 'test-hub');
    const result: HubConnectionTest = {
      ok: false,
      checks: [
        { target: 'CREDENTIALS', status: 'FAILED', message: 'Cannot read the token of ACM Hub hub-east', durationMs: 1 },
        { target: 'API', status: 'SKIPPED', message: "Not tried: needs the hub's token", durationMs: 0 },
        { target: 'OBSERVABILITY', status: 'SKIPPED', message: "Not tried: needs the hub's token", durationMs: 0 },
        { target: 'SEARCH', status: 'SKIPPED', message: "Not tried: needs the hub's token", durationMs: 0 }
      ]
    };
    const request = http.expectOne('/api/v1/hubs/h1/test');
    expect(request.request.method).toBe('POST');
    request.flush(result);
    fixture.detectChanges();

    const checks = text(page, 'connection-test');
    expect(checks).toContain('Connection test failed');
    expect(checks).toContain('Credentials');
    expect(checks).toContain('Cannot read the token of ACM Hub hub-east');
    expect(checks).toContain('Hub API');
  });

  it('tells operators when another collection is running', () => {
    const page = render(['OPERATOR', 'VIEWER']);

    click(page, 'collect-hub');
    http.expectOne('/api/v1/hubs/h1/collect').flush(
      { status: 409, message: 'Another collection is running or has just finished; try again in a minute.' },
      { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text(page, 'hub-message')).toContain('Another collection is running');
  });

  it('registers a hub without the optional endpoints left empty, and shows why the API refused it', () => {
    const page = render(['ADMIN', 'OPERATOR', 'VIEWER']);
    click(page, 'register-hub');
    expect(page.querySelector('[data-testid="hub-form"]')).not.toBeNull();
    fixture.componentInstance.form = {
      name: 'hub-west', apiUrl: ' https://api.hub-west.example.com:6443 ', credentialsSecretRef: 'hub-west-credentials',
      observabilityUrl: '', searchUrl: ''
    };

    fixture.componentInstance.testForm();
    const test = http.expectOne('/api/v1/hubs/test');
    expect(test.request.body.observabilityUrl).toBeNull();
    test.flush({ ok: true, checks: [{ target: 'API', status: 'WARNING', message: 'Connected, but the token sees no ManagedClusters', durationMs: 80 }] });
    fixture.detectChanges();
    expect(text(page, 'connection-test')).toContain('Connected, with warnings');

    fixture.componentInstance.save();
    const register = http.expectOne('/api/v1/hubs');
    expect(register.request.method).toBe('POST');
    expect(register.request.body).toEqual({
      name: 'hub-west', apiUrl: 'https://api.hub-west.example.com:6443', credentialsSecretRef: 'hub-west-credentials',
      observabilityUrl: null, searchUrl: null
    });
    register.flush({ status: 409, message: 'An ACM hub named hub-west already exists' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text(page, 'hub-message')).toContain('already exists');
    expect(page.querySelector('[data-testid="hub-form"]')).not.toBeNull();
  });

  it('deletes a hub only after its name is typed', () => {
    const page = render(['ADMIN', 'OPERATOR', 'VIEWER']);
    click(page, 'delete-hub');
    expect(text(page, 'delete-confirm')).toContain('also deletes its 12 clusters');
    const confirm = page.querySelector('[data-testid="delete-confirm-button"]') as HTMLButtonElement;
    expect(confirm.disabled).toBeTrue();

    fixture.componentInstance.confirmDeleteText = 'hub-east';
    fixture.detectChanges();
    expect(confirm.disabled).toBeFalse();
    confirm.click();

    const request = http.expectOne('/api/v1/hubs/h1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/hubs').flush([]);
    fixture.detectChanges();

    expect(text(page, 'hub-message')).toContain('hub-east and its clusters were deleted');
    expect(page.querySelector('[data-testid="no-hubs"]')).not.toBeNull();
  });
});

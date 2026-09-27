import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LicensingComponent } from './licensing.component';
import { LicenseAudit, NodeAgentStatus } from '../../models/portal.models';

describe('LicensingComponent', () => {
  let fixture: ComponentFixture<LicensingComponent>;
  let http: HttpTestingController;

  const audit = (overrides: Partial<LicenseAudit>): LicenseAudit => ({
    totalLicenseCores: 96,
    licensedCapCores: 500,
    highWatermarkCores: 96,
    complianceBreach: false,
    complianceStatus: 'COMPLIANT',
    clustersCounted: 2,
    clustersWithoutNodeData: [],
    workerNodesCount: 6,
    masterNodesCount: 6,
    bareMetalCores: 0,
    virtualCores: 96,
    coresByEnvironment: { PRODUCTION: 96 },
    coresByOwnerTeam: { 'Platform Team': 96 },
    coresByInfrastructure: { VMWARE: 96 },
    ...overrides
  });

  const agent = (overrides: Partial<NodeAgentStatus>): NodeAgentStatus => ({
    clusterName: 'ocp-prod-east',
    agentVersion: '1.0.0',
    collectedAt: '2026-09-27T18:00:00',
    receivedAt: '2026-09-27T18:00:01',
    nodeCount: 6,
    registered: true,
    fresh: true,
    ...overrides
  });

  function render(licenseAudit: LicenseAudit, agents: NodeAgentStatus[]): HTMLElement {
    fixture = TestBed.createComponent(LicensingComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/licensing/audit').flush(licenseAudit);
    http.expectOne('/api/v1/node-reports').flush(agents);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LicensingComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows a complete fleet as compliant without the missing node data panel', () => {
    const page = render(audit({}), [agent({})]);

    expect(page.querySelector('[data-testid="compliance-status"]')?.textContent?.trim()).toBe('COMPLIANT');
    expect(page.querySelector('[data-testid="node-data-gaps"]')).toBeNull();
    expect(page.querySelector('[data-testid="node-agents"]')?.textContent).toContain('ocp-prod-east');
    expect(page.querySelector('[data-testid="node-agents"]')?.textContent).toContain('Reporting');
  });

  it('marks the audit incomplete and lists clusters without node data instead of counting them as 0', () => {
    const page = render(audit({
      complianceStatus: 'INCOMPLETE',
      clustersCounted: 1,
      clustersWithoutNodeData: [
        { clusterName: 'ocp-no-agent', environment: 'PRODUCTION', reason: 'NO_AGENT_REPORT', lastAgentReportAt: null },
        { clusterName: 'ocp-stale', environment: 'STAGING', reason: 'STALE_AGENT_REPORT', lastAgentReportAt: '2026-09-27T09:30:00' }
      ]
    }), [agent({ clusterName: 'ocp-stale', fresh: false }), agent({ clusterName: 'ocp-typo', registered: false })]);

    expect(page.querySelector('[data-testid="compliance-status"]')?.textContent?.trim()).toBe('INCOMPLETE');
    expect(page.textContent).toContain('at least');
    expect(page.textContent).toContain('Worker cores of 1 of 3 clusters');
    const gaps = page.querySelector('[data-testid="node-data-gaps"]')?.textContent ?? '';
    expect(gaps).toContain('2 clusters have no node data');
    expect(gaps).toContain('No node agent has reported this cluster');
    expect(gaps).toContain('The node agent stopped reporting');
    expect(gaps).toContain('never');
    const agents = page.querySelector('[data-testid="node-agents"]')?.textContent ?? '';
    expect(agents).toContain('Stale');
    expect(agents).toContain('Unknown cluster');
  });

  it('reports a breach even while some clusters are missing', () => {
    const page = render(audit({
      complianceStatus: 'BREACH',
      complianceBreach: true,
      highWatermarkCores: 640,
      clustersWithoutNodeData: [
        { clusterName: 'ocp-new', environment: 'QA', reason: 'NOT_COLLECTED', lastAgentReportAt: null }
      ]
    }), []);

    expect(page.querySelector('[data-testid="compliance-status"]')?.textContent?.trim()).toBe('CAP EXCEEDED');
    expect(page.querySelector('[data-testid="node-agents"]')?.textContent).toContain('No node agent has reported yet');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportGeneratorComponent } from './report-generator.component';
import { AuthService } from '../../services/auth.service';
import { PortalNotification, PortalRole, ReportSchedule } from '../../models/portal.models';

describe('ReportGeneratorComponent', () => {
  let fixture: ComponentFixture<ReportGeneratorComponent>;
  let http: HttpTestingController;
  let roles: PortalRole[] = [];

  const schedule: ReportSchedule = {
    id: 'a1',
    title: 'Weekly license audit',
    reportType: 'LICENSE_AUDIT',
    format: 'PDF',
    cronSchedule: '0 0 7 * * MON',
    recipients: 'finops@example.com, cio@example.com',
    enabled: true,
    createdAt: '2026-09-20T10:00:00',
    lastRunAt: '2026-09-21T07:00:00',
    nextRunAt: '2026-09-28T07:00:00',
    lastDelivery: { status: 'NOT_SENT', at: '2026-09-21T07:00:05', detail: 'No SMTP server is configured (spring.mail.host), so nothing was sent' }
  };

  const alert: PortalNotification = {
    id: 3,
    kind: 'LICENSE_BREACH',
    reportId: null,
    subject: '[OpenShift Operations Portal] License cap exceeded: 640 of 500 cores',
    recipients: 'ops@example.com',
    status: 'SENT',
    detail: null,
    createdAt: '2026-09-27T23:59:00',
    sentAt: '2026-09-27T23:59:01'
  };

  function render(granted: PortalRole[]): HTMLElement {
    roles = granted;
    fixture = TestBed.createComponent(ReportGeneratorComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/reports').flush([schedule]);
    if (roles.includes('OPERATOR')) {
      http.expectOne((req) => req.url === '/api/v1/notifications').flush([alert]);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportGeneratorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { hasRole: (role: PortalRole) => roles.includes(role) } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows each schedule with its next run and why its last delivery was not sent', () => {
    const page = render(['ADMIN', 'OPERATOR', 'VIEWER']);
    const schedules = page.querySelector('[data-testid="schedules"]')?.textContent ?? '';

    expect(schedules).toContain('Weekly license audit');
    expect(schedules).toContain('0 7 * * MON');
    expect(schedules).toContain('Next: 2026-09-28 07:00');
    expect(schedules).toContain('Not sent');
    expect(schedules).toContain('No SMTP server is configured');
    expect(page.querySelector('[data-testid="send-now"]')).not.toBeNull();
    const deliveries = page.querySelector('[data-testid="deliveries"]')?.textContent ?? '';
    expect(deliveries).toContain('License alert');
    expect(deliveries).toContain('License cap exceeded');
  });

  it('shows viewers the schedules without actions or the delivery log', () => {
    const page = render(['VIEWER']);

    expect(page.querySelector('[data-testid="schedules"]')?.textContent).toContain('Weekly license audit');
    expect(page.querySelector('[data-testid="send-now"]')).toBeNull();
    expect(page.querySelector('[data-testid="new-schedule"]')).toBeNull();
    expect(page.querySelector('[data-testid="deliveries"]')).toBeNull();
  });

  it('shows why the API rejected a new schedule', () => {
    const page = render(['ADMIN', 'OPERATOR', 'VIEWER']);
    (page.querySelector('[data-testid="new-schedule"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    fixture.componentInstance.form = {
      title: 'Bad', reportType: 'FLEET_CAPACITY', format: 'CSV', cronSchedule: 'every monday', recipients: 'ops@example.com'
    };

    fixture.componentInstance.createSchedule();
    const request = http.expectOne('/api/v1/reports');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.cronSchedule).toBe('every monday');
    request.flush({ status: 400, message: "cronSchedule: 'every monday' is not a valid cron schedule; use five fields, e.g. '0 7 * * MON'" },
      { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(page.querySelector('[data-testid="schedule-message"]')?.textContent).toContain('is not a valid cron schedule');
    expect(page.querySelector('[data-testid="schedule-form"]')).not.toBeNull();
  });
});

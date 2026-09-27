import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { IconComponent } from './shared/icon.component';
import { PortalService } from './services/portal.service';
import { AuthService } from './services/auth.service';
import { AcmHubSummary, CurrentUser } from './models/portal.models';

/** How often the header refreshes hub status. */
const HUB_STATUS_REFRESH_MS = 30_000;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private portalService = inject(PortalService);
  private destroyRef = inject(DestroyRef);
  auth = inject(AuthService);

  title = 'OpenShift Operations Portal';
  hubs: AcmHubSummary[] = [];
  /** Only reachable when the backend runs the simulator and the user is an admin. */
  simulatorAvailable = false;

  get activeHubCount(): number {
    return this.hubs.filter((hub) => hub.status === 'ACTIVE').length;
  }

  roleLabel(user: CurrentUser): string {
    if (user.roles.includes('ADMIN')) return 'Administrator';
    if (user.roles.includes('OPERATOR')) return 'Operator';
    if (user.roles.includes('VIEWER')) return 'Viewer';
    return 'No portal role';
  }

  ngOnInit(): void {
    timer(0, HUB_STATUS_REFRESH_MS)
      .pipe(
        // Keep the last known status when a refresh fails
        switchMap(() => this.portalService.getHubs().pipe(catchError(() => of(this.hubs)))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((hubs) => (this.hubs = hubs));

    this.portalService.getSimulatorStatus().subscribe({
      next: () => (this.simulatorAvailable = true),
      error: () => (this.simulatorAvailable = false)
    });
  }
}

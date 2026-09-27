import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent } from './shared/icon.component';
import { PortalService } from './services/portal.service';
import { AcmHubSummary } from './models/portal.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private portalService = inject(PortalService);

  title = 'OpenShift Operations Portal';
  hubs: AcmHubSummary[] = [];
  /** The simulator endpoints only exist when the backend runs with the simulator enabled. */
  simulatorAvailable = false;

  get activeHubCount(): number {
    return this.hubs.filter((hub) => hub.status === 'ACTIVE').length;
  }

  ngOnInit(): void {
    this.portalService.getHubs().subscribe({
      next: (res) => (this.hubs = res),
      error: (err) => console.error('Failed to load ACM hubs', err)
    });

    this.portalService.getSimulatorStatus().subscribe({
      next: () => (this.simulatorAvailable = true),
      error: () => (this.simulatorAvailable = false)
    });
  }
}

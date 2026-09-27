import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { IconComponent } from './shared/icon.component';
import { PortalService } from './services/portal.service';
import { AuthService } from './services/auth.service';
import { AcmHubSummary, CurrentUser } from './models/portal.models';

const HUB_STATUS_REFRESH_MS = 30_000;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule, IconComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private portalService = inject(PortalService);
  private destroyRef = inject(DestroyRef);
  private router = inject(Router);
  auth = inject(AuthService);

  title = 'OpenShift Operations Portal';
  hubs: AcmHubSummary[] = [];
  simulatorAvailable = false;
  isDark = true;
  searchQuery = '';
  isSearchOpen = false;

  get activeHubCount(): number {
    return this.hubs.filter((hub) => hub.status === 'ACTIVE').length;
  }

  roleLabel(user: CurrentUser): string {
    if (user.roles.includes('ADMIN')) return 'Administrator';
    if (user.roles.includes('OPERATOR')) return 'Operator';
    if (user.roles.includes('VIEWER')) return 'Viewer';
    return 'Viewer';
  }

  ngOnInit(): void {
    // Initialize Dark Theme by default (Signature HeroUI style)
    const savedTheme = localStorage.getItem('heroui-theme');
    if (savedTheme) {
      this.isDark = savedTheme === 'dark';
    } else {
      this.isDark = true;
    }
    this.applyTheme();

    timer(0, HUB_STATUS_REFRESH_MS)
      .pipe(
        switchMap(() => this.portalService.getHubs().pipe(catchError(() => of(this.hubs)))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((hubs) => (this.hubs = hubs));

    this.portalService.getSimulatorStatus().subscribe({
      next: () => (this.simulatorAvailable = true),
      error: () => (this.simulatorAvailable = false)
    });
  }

  toggleTheme(): void {
    this.isDark = !this.isDark;
    localStorage.setItem('heroui-theme', this.isDark ? 'dark' : 'light');
    this.applyTheme();
  }

  private applyTheme(): void {
    if (this.isDark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }

  openSearch(): void {
    this.isSearchOpen = true;
  }

  closeSearch(): void {
    this.isSearchOpen = false;
    this.searchQuery = '';
  }

  executeQuickSearch(route: string): void {
    this.closeSearch();
    this.router.navigate([route]);
  }
}

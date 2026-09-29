import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="className"
    >
      <!-- dashboard -->
      <ng-container *ngIf="name === 'dashboard'">
        <rect width="7" height="9" x="3" y="3" rx="1" />
        <rect width="7" height="5" x="14" y="3" rx="1" />
        <rect width="7" height="9" x="14" y="12" rx="1" />
        <rect width="7" height="5" x="3" y="16" rx="1" />
      </ng-container>

      <!-- server -->
      <ng-container *ngIf="name === 'server'">
        <rect width="20" height="8" x="2" y="2" rx="2" ry="2" />
        <rect width="20" height="8" x="2" y="14" rx="2" ry="2" />
        <line x1="6" x2="6.01" y1="6" y2="6" />
        <line x1="6" x2="6.01" y1="18" y2="18" />
      </ng-container>

      <!-- shield-check -->
      <ng-container *ngIf="name === 'shield-check'">
        <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
        <path d="m9 12 2 2 4-4" />
      </ng-container>

      <!-- shield-alert -->
      <ng-container *ngIf="name === 'shield-alert'">
        <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
        <line x1="12" x2="12" y1="8" y2="12" />
        <line x1="12" x2="12.01" y1="16" y2="16" />
      </ng-container>

      <!-- trending-up -->
      <ng-container *ngIf="name === 'trending-up'">
        <polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
        <polyline points="16 7 22 7 22 13" />
      </ng-container>

      <!-- file-text -->
      <ng-container *ngIf="name === 'file-text'">
        <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
        <path d="M14 2v4a2 2 0 0 0 2 2h4" />
        <path d="M10 9H8" />
        <path d="M16 13H8" />
        <path d="M16 17H8" />
      </ng-container>

      <!-- activity -->
      <ng-container *ngIf="name === 'activity'">
        <path d="M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.22 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.46 12H2" />
      </ng-container>

      <!-- refresh -->
      <ng-container *ngIf="name === 'refresh'">
        <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
        <path d="M3 3v5h5" />
        <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
        <path d="M16 21h5v-5" />
      </ng-container>

      <!-- cpu -->
      <ng-container *ngIf="name === 'cpu'">
        <rect width="16" height="16" x="4" y="4" rx="2" />
        <rect width="6" height="6" x="9" y="9" rx="1" />
        <path d="M15 2v2" />
        <path d="M15 20v2" />
        <path d="M2 15h2" />
        <path d="M2 9h2" />
        <path d="M20 15h2" />
        <path d="M20 9h2" />
        <path d="M9 2v2" />
        <path d="M9 20v2" />
      </ng-container>

      <!-- hard-drive -->
      <ng-container *ngIf="name === 'hard-drive'">
        <line x1="22" x2="2" y1="12" y2="12" />
        <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
        <line x1="6" x2="6.01" y1="16" y2="16" />
        <line x1="10" x2="10.01" y1="16" y2="16" />
      </ng-container>

      <!-- layers -->
      <ng-container *ngIf="name === 'layers'">
        <path d="m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z" />
        <path d="m22 12.5-8.58 3.91a2 2 0 0 1-1.66 0L2 12.5" />
        <path d="m22 17.5-8.58 3.91a2 2 0 0 1-1.66 0L2 17.5" />
      </ng-container>

      <!-- users -->
      <ng-container *ngIf="name === 'users'">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </ng-container>

      <!-- download -->
      <ng-container *ngIf="name === 'download'">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
        <polyline points="7 10 12 15 17 10" />
        <line x1="12" x2="12" y1="15" y2="3" />
      </ng-container>

      <!-- check-circle -->
      <ng-container *ngIf="name === 'check-circle'">
        <circle cx="12" cy="12" r="10" />
        <path d="m9 12 2 2 4-4" />
      </ng-container>

      <!-- alert-triangle -->
      <ng-container *ngIf="name === 'alert-triangle'">
        <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
        <line x1="12" x2="12" y1="9" y2="13" />
        <line x1="12" x2="12.01" y1="17" y2="17" />
      </ng-container>

      <!-- zap -->
      <ng-container *ngIf="name === 'zap'">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
      </ng-container>

      <!-- search -->
      <ng-container *ngIf="name === 'search'">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.3-4.3" />
      </ng-container>

      <!-- sun -->
      <ng-container *ngIf="name === 'sun'">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2" />
        <path d="M12 20v2" />
        <path d="m4.93 4.93 1.41 1.41" />
        <path d="m17.66 17.66 1.41 1.41" />
        <path d="M2 12h2" />
        <path d="M20 12h2" />
        <path d="m6.34 17.66-1.41 1.41" />
        <path d="m19.07 4.93-1.41 1.41" />
      </ng-container>

      <!-- moon -->
      <ng-container *ngIf="name === 'moon'">
        <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
      </ng-container>

      <!-- bell -->
      <ng-container *ngIf="name === 'bell'">
        <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
      </ng-container>

      <!-- chevron-down -->
      <ng-container *ngIf="name === 'chevron-down'">
        <path d="m6 9 6 6 6-6" />
      </ng-container>

      <!-- chevron-right -->
      <ng-container *ngIf="name === 'chevron-right'">
        <path d="m9 18 6-6-6-6" />
      </ng-container>

      <!-- arrow-up-right -->
      <ng-container *ngIf="name === 'arrow-up-right'">
        <path d="M7 7h10v10" />
        <path d="M7 17 17 7" />
      </ng-container>

      <!-- arrow-down-right -->
      <ng-container *ngIf="name === 'arrow-down-right'">
        <path d="m7 7 10 10" />
        <path d="M17 7v10H7" />
      </ng-container>

      <!-- calendar -->
      <ng-container *ngIf="name === 'calendar'">
        <path d="M8 2v4" />
        <path d="M16 2v4" />
        <rect width="18" height="18" x="3" y="4" rx="2" />
        <path d="M3 10h18" />
      </ng-container>

      <!-- database -->
      <ng-container *ngIf="name === 'database'">
        <ellipse cx="12" cy="5" rx="9" ry="3" />
        <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
        <path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3" />
      </ng-container>

      <!-- cloud -->
      <ng-container *ngIf="name === 'cloud'">
        <path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z" />
      </ng-container>

      <!-- sliders -->
      <ng-container *ngIf="name === 'sliders'">
        <line x1="4" x2="4" y1="21" y2="14" />
        <line x1="4" x2="4" y1="10" y2="3" />
        <line x1="12" x2="12" y1="21" y2="12" />
        <line x1="12" x2="12" y1="8" y2="3" />
        <line x1="20" x2="20" y1="21" y2="16" />
        <line x1="20" x2="20" y1="12" y2="3" />
        <line x1="1" x2="7" y1="14" y2="14" />
        <line x1="9" x2="15" y1="8" y2="8" />
        <line x1="17" x2="23" y1="16" y2="16" />
      </ng-container>

      <!-- log-out -->
      <ng-container *ngIf="name === 'log-out'">
        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
        <polyline points="16 17 21 12 16 7" />
        <line x1="21" x2="9" y1="12" y2="12" />
      </ng-container>

      <!-- filter -->
      <ng-container *ngIf="name === 'filter'">
        <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
      </ng-container>

      <!-- network -->
      <ng-container *ngIf="name === 'network'">
        <rect x="16" y="16" width="6" height="6" rx="1" />
        <rect x="2" y="16" width="6" height="6" rx="1" />
        <rect x="9" y="2" width="6" height="6" rx="1" />
        <path d="M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3" />
        <path d="M12 12V8" />
      </ng-container>

      <!-- x-circle -->
      <ng-container *ngIf="name === 'x-circle'">
        <circle cx="12" cy="12" r="10" />
        <path d="m15 9-6 6" />
        <path d="m9 9 6 6" />
      </ng-container>

      <!-- minus-circle -->
      <ng-container *ngIf="name === 'minus-circle'">
        <circle cx="12" cy="12" r="10" />
        <path d="M8 12h8" />
      </ng-container>

      <!-- plus -->
      <ng-container *ngIf="name === 'plus'">
        <path d="M5 12h14" />
        <path d="M12 5v14" />
      </ng-container>
    </svg>
  `
})
export class IconComponent {
  @Input() name: string = 'dashboard';
  @Input() size: number = 18;
  @Input() strokeWidth: number = 2;
  @Input() className: string = '';
}

import { Injectable, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, firstValueFrom, throwError } from 'rxjs';
import { CurrentUser, PortalRole } from '../models/portal.models';
import { PortalService } from './portal.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private oauthService = inject(OAuthService);
  private portalService = inject(PortalService);

  /** False when the backend runs with security disabled; every feature is then available. */
  readonly enabled = signal(false);
  readonly user = signal<CurrentUser | null>(null);

  /**
   * Runs before the app starts. When the backend requires sign-in this redirects to Keycloak
   * (authorization code + PKCE) and resumes here once the code has been exchanged.
   */
  async initialize(): Promise<void> {
    const config = await firstValueFrom(this.portalService.getAuthConfig());
    if (!config.enabled || !config.issuer || !config.clientId) {
      return;
    }
    this.enabled.set(true);

    this.oauthService.configure({
      issuer: config.issuer,
      clientId: config.clientId,
      redirectUri: window.location.origin + '/',
      postLogoutRedirectUri: window.location.origin + '/',
      responseType: 'code',
      scope: 'openid profile email'
    });
    this.oauthService.setupAutomaticSilentRefresh();

    const returnTo = window.location.pathname + window.location.search;
    await this.oauthService.loadDiscoveryDocumentAndLogin({ state: returnTo });
    // Back from Keycloak on the redirect URI: restore the page the user asked for before the router starts
    const requested = decodeURIComponent(this.oauthService.state ?? '');
    if (requested.startsWith('/') && !requested.startsWith('//')) {
      window.history.replaceState(null, '', requested);
    }

    this.user.set(await firstValueFrom(this.portalService.getCurrentUser()));
  }

  /** Always true when security is disabled. */
  hasRole(role: PortalRole): boolean {
    return !this.enabled() || (this.user()?.roles.includes(role) ?? false);
  }

  login(): void {
    this.oauthService.initCodeFlow(window.location.pathname + window.location.search);
  }

  logout(): void {
    this.oauthService.logOut();
  }
}

/** Sends the user back through sign-in when the session has expired and the token could not be refreshed. */
export const reauthenticateOnUnauthorized: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && auth.enabled()) {
        auth.login();
      }
      return throwError(() => error);
    })
  );
};

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard de route pour l'espace agent administratif.
 * Autorise uniquement les utilisateurs authentifiés avec le rôle 'AGENT'.
 * Redirige vers /login si non authentifié, ou vers l'espace correspondant sinon.
 * Réf. CDC § 8.2, RG13.
 */
export const agentGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  const user = authService.currentUser();

  if (user?.role === 'AGENT') {
    return true;
  }

  // Redirection intelligente selon le rôle
  if (user?.role === 'ADMINISTRATEUR') {
    router.navigate(['/admin']);
  } else {
    // ENTREPRENEUR ou rôle inconnu
    router.navigate(['/dashboard']);
  }
  return false;
};


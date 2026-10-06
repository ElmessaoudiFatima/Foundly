import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  const user = authService.currentUser();
  if (user?.role === 'ADMINISTRATEUR') {
    return true;
  }

  // Si utilisateur authentifié mais non admin, renvoyer vers dashboard
  router.navigate(['/dashboard']);
  return false;
};

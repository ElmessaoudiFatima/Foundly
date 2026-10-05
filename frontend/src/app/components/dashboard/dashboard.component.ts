import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.authService.currentUser;
  isLoggingOut = false;
  toastMessage = '';

  get roleLabel(): string {
    const role = this.user()?.role;
    if (role === 'ADMINISTRATEUR') return 'Administrateur';
    if (role === 'AGENT') return 'Agent Administratif';
    return 'Entrepreneur';
  }

  get spaceTitle(): string {
    const role = this.user()?.role;
    if (role === 'ADMINISTRATEUR') return 'Espace Administration';
    if (role === 'AGENT') return 'Espace Agent Administratif';
    return 'Espace Entrepreneur';
  }

  logout(): void {
    this.isLoggingOut = true;
    this.authService.logout().subscribe({
      next: () => {
        this.isLoggingOut = false;
      },
      error: () => {
        this.isLoggingOut = false;
      }
    });
  }

  showFeatureNotice(title: string): void {
    this.toastMessage = `Le module « ${title} » sera disponible dans la prochaine mise à jour.`;
    setTimeout(() => this.toastMessage = '', 3500);
  }
}

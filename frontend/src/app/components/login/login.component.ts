import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ApiErrorResponse } from '../../models/auth.models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  showPassword = false;
  rememberMe = false;
  isLoading = false;
  errorMessage: string | null = null;
  infoMessage: string | null = null;

  ngOnInit(): void {
    this.email = '';
    this.password = '';
    setTimeout(() => {
      this.email = '';
      this.password = '';
    }, 0);
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  onSubmit(): void {
    this.errorMessage = null;
    this.infoMessage = null;

    const trimmedEmail = this.email.trim();
    if (!trimmedEmail) {
      this.errorMessage = 'Veuillez saisir votre adresse e-mail.';
      return;
    }

    if (!this.password) {
      this.errorMessage = 'Veuillez saisir votre mot de passe.';
      return;
    }

    this.isLoading = true;

    this.authService.login({ email: trimmedEmail, password: this.password }).subscribe({
      next: (session) => {
        this.isLoading = false;
        // Redirection selon le rôle (RG13)
        if (session.user.role === 'ADMINISTRATEUR') {
          this.router.navigate(['/admin']);
        } else if (session.user.role === 'AGENT') {
          this.router.navigate(['/agent']);
        } else {
          // ENTREPRENEUR par défaut
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        if (err.status === 401) {
          const apiError = err.error as ApiErrorResponse;
          this.errorMessage = apiError?.message || 'Identifiants invalides : vérifiez votre adresse e-mail ou votre mot de passe.';
        } else if (err.status === 0) {
          this.errorMessage = 'Impossible de joindre le serveur. Assurez-vous que le backend Spring Boot est bien démarré sur le port 8080.';
        } else {
          this.errorMessage = err.error?.message || 'Une erreur inattendue est survenue. Veuillez réessayer.';
        }
      }
    });
  }

  forgotPassword(): void {
    this.infoMessage = 'Pour réinitialiser votre mot de passe dans cette version de démonstration, veuillez contacter le support administrateur.';
  }
}

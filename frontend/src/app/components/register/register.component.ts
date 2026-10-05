import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ApiErrorResponse, RegisterRequest } from '../../models/auth.models';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  cin = '';
  password = '';
  confirmPassword = '';

  acceptTerms = false;
  showPassword = false;
  showConfirmPassword = false;
  isLoading = false;

  errorMessage: string | null = null;
  fieldErrors: Record<string, string> = {};

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPassword(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  // Password validation checks
  get isPasswordLongEnough(): boolean {
    return this.password.length >= 12;
  }

  get doPasswordsMatch(): boolean {
    return this.password !== '' && this.password === this.confirmPassword;
  }

  get isCinValid(): boolean {
    return this.cin.trim().length >= 4;
  }

  get isPhoneValid(): boolean {
    return this.phone.trim().length >= 8;
  }

  onSubmit(): void {
    this.errorMessage = null;
    this.fieldErrors = {};

    const cleanFirstName = this.firstName.trim();
    const cleanLastName = this.lastName.trim();
    const cleanEmail = this.email.trim();
    const cleanCin = this.cin.trim().toUpperCase();
    const cleanPhone = this.phone.trim();

    // Client-side validations
    if (!cleanFirstName) {
      this.fieldErrors['firstName'] = 'Le prénom est obligatoire.';
    }
    if (!cleanLastName) {
      this.fieldErrors['lastName'] = 'Le nom est obligatoire.';
    }
    if (!cleanEmail) {
      this.fieldErrors['email'] = "L'adresse e-mail est obligatoire.";
    }
    if (!cleanCin) {
      this.fieldErrors['cin'] = 'Le numéro de CIN est obligatoire (ex: AB123456).';
    } else if (cleanCin.length < 4) {
      this.fieldErrors['cin'] = 'Le CIN doit contenir au moins 4 caractères.';
    }
    if (!cleanPhone) {
      this.fieldErrors['phone'] = 'Le numéro de téléphone est obligatoire (ex: 0612345678).';
    } else if (cleanPhone.length < 8) {
      this.fieldErrors['phone'] = 'Le numéro de téléphone doit contenir au moins 8 chiffres.';
    }
    if (!this.password) {
      this.fieldErrors['password'] = 'Le mot de passe est obligatoire.';
    } else if (this.password.length < 12) {
      this.fieldErrors['password'] = 'Le mot de passe doit comporter au moins 12 caractères pour respecter la politique de sécurité.';
    }
    if (this.password !== this.confirmPassword) {
      this.fieldErrors['confirmPassword'] = 'Les deux mots de passe ne correspondent pas.';
    }
    if (!this.acceptTerms) {
      this.errorMessage = 'Vous devez accepter les conditions d’utilisation pour continuer.';
      return;
    }

    if (Object.keys(this.fieldErrors).length > 0) {
      this.errorMessage = 'Veuillez corriger les erreurs signalées dans le formulaire.';
      return;
    }

    this.isLoading = true;

    const payload: RegisterRequest = {
      firstName: cleanFirstName,
      lastName: cleanLastName,
      email: cleanEmail,
      password: this.password,
      cin: cleanCin,
      phone: cleanPhone
    };

    this.authService.register(payload).subscribe({
      next: () => {
        this.isLoading = false;
        // On successful registration, auto log in & navigate to dashboard
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        if (err.status === 409) {
          this.errorMessage = 'Cette adresse e-mail est déjà associée à un compte Foundly. Veuillez vous connecter.';
          this.fieldErrors['email'] = 'Adresse e-mail déjà utilisée.';
        } else if (err.status === 422) {
          const apiError = err.error as ApiErrorResponse;
          this.errorMessage = apiError?.message || 'Données invalides. Veuillez vérifier vos informations.';
          if (apiError?.fieldErrors) {
            for (const fe of apiError.fieldErrors) {
              this.fieldErrors[fe.field] = fe.message;
            }
          }
        } else if (err.status === 0) {
          this.errorMessage = 'Impossible de contacter le serveur backend. Vérifiez que Spring Boot est actif sur le port 8080.';
        } else {
          this.errorMessage = err.error?.message || "Une erreur est survenue lors de l'inscription.";
        }
      }
    });
  }
}

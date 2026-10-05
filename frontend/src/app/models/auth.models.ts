export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  cin: string;
  phone: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type RoleUtilisateur = 'ENTREPRENEUR' | 'AGENT' | 'ADMINISTRATEUR';

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  cinMasked: string;
  phone: string;
  role: RoleUtilisateur;
  active: boolean;
  createdAt: string;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: FieldError[];
}

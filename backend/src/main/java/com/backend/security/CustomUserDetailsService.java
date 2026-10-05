package com.backend.security;

import com.backend.entity.Utilisateur;
import com.backend.repository.UtilisateurRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de UserDetailsService pour Spring Security.
 * Charge un utilisateur par email (loadUserByUsername) ou par UUID (loadUserById).
 * Les rôles sont préfixés ROLE_ pour être compatibles avec hasRole().
 */
@Component
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public CustomUserDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Utilisé par Spring Security (form login, etc.).
     * Le "username" ici est l'email.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utilisateur u = utilisateurRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + email));
        return buildUserDetails(u);
    }

    /**
     * Chargement par UUID — utilisé par JwtAuthenticationFilter.
     */
    public UserDetails loadUserById(UUID id) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + id));
        return buildUserDetails(u);
    }

    private UserDetails buildUserDetails(Utilisateur u) {
        String role = "ROLE_" + u.getRole().name();
        return new User(
                u.getId().toString(),
                u.getPassword(),
                u.isActif(),      // enabled
                true,             // accountNonExpired
                true,             // credentialsNonExpired
                true,             // accountNonLocked
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}

package com.backend.security;

import com.backend.repository.UtilisateurRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtre JWT exécuté une seule fois par requête.
 * Lit "Authorization: Bearer <jwt>", valide le token, charge l'utilisateur,
 * vérifie qu'il est actif, et remplit le SecurityContext.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UtilisateurRepository utilisateurRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   UtilisateurRepository utilisateurRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Pas de header ou pas du format Bearer → on passe au filtre suivant
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        // Token invalide ou expiré → on passe sans authentifier
        if (!jwtService.isTokenValid(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Si déjà authentifié dans ce contexte, pas besoin de re-traiter
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtService.extractSubject(jwt);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserById(UUID.fromString(userId));
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // Vérification du compte actif (enabled dans UserDetails)
        if (!userDetails.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}

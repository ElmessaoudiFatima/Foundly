package com.backend.mapper;

import com.backend.dto.request.RegisterRequest;
import com.backend.dto.response.UserResponse;
import com.backend.entity.Utilisateur;
import org.springframework.stereotype.Component;

/**
 * Mapping manuel entre RegisterRequest ↔ Utilisateur et Utilisateur ↔ UserResponse.
 * Pas de MapStruct. Pas de Lombok.
 */
@Component
public class UserMapper {

    /**
     * RegisterRequest → Utilisateur (entité non persistée).
     * Note : email, password, role et actif sont définis dans AuthServiceImpl, pas ici.
     */
    public Utilisateur toEntity(RegisterRequest req) {
        Utilisateur u = new Utilisateur();
        u.setPrenom(req.firstName());
        u.setNom(req.lastName());
        // email sera normalisé (trim + toLowerCase) par le service avant sauvegarde
        u.setEmail(req.email());
        u.setCin(req.cin());
        u.setTelephone(req.phone());
        return u;
    }

    /**
     * Utilisateur → UserResponse (DTO de présentation).
     * Le CIN n'est JAMAIS renvoyé en clair : masquage systématique.
     */
    public UserResponse toResponse(Utilisateur u) {
        return new UserResponse(
                u.getId(),
                u.getPrenom(),   // firstName ← prenom
                u.getNom(),      // lastName  ← nom
                u.getEmail(),
                maskCin(u.getCin()),
                u.getTelephone(),
                u.getRole(),
                u.isActif(),
                u.getCreatedAt()
        );
    }

    /**
     * Masque le CIN : garde les 2 premiers et les 2 derniers caractères,
     * remplace tout l'intérieur par "***".
     * Exemples : "AB123456" → "AB***56", "ABCD" → "AB***CD", null/court → "***"
     */
    private String maskCin(String cin) {
        if (cin == null || cin.length() < 4) {
            return "***";
        }
        String prefix = cin.substring(0, 2);
        String suffix = cin.substring(cin.length() - 2);
        return prefix + "***" + suffix;
    }
}

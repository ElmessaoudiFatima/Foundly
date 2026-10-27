package com.backend.service;

import com.backend.dto.audit.PagedAuditEventDto;
import com.backend.dto.dossier.*;
import com.backend.entity.Utilisateur;
import com.backend.enums.DossierStatus;

import java.util.UUID;

public interface DossierService {

    DossierDetailDto createDossier(DossierCreateRequest request, Utilisateur entrepreneur);

    DossierDetailDto getDossierDetail(UUID dossierId, Utilisateur user);

    DossierSummaryDto getDossierSummary(UUID dossierId, Utilisateur user);

    PagedDossierSummaryDto getEntrepreneurDossiers(UUID entrepreneurId, DossierStatus status, int page, int size);

    DossierDetailDto updateDossier(UUID dossierId, DossierUpdateRequest request, Utilisateur user);

    SubmissionResultDto submitDossier(UUID dossierId, Utilisateur user);

    PagedAuditEventDto getAuditEvents(UUID dossierId, int page, int size, Utilisateur user);
}

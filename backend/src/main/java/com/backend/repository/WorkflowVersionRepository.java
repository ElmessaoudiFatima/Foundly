package com.backend.repository;

import com.backend.entity.WorkflowVersion;
import com.backend.enums.LegalFormCode;
import com.backend.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    List<WorkflowVersion> findAllByOrderByFormeJuridiqueAscDateModificationDesc();

    List<WorkflowVersion> findByFormeJuridiqueOrderByDateModificationDesc(LegalFormCode formeJuridique);

    Optional<WorkflowVersion> findByFormeJuridiqueAndStatut(LegalFormCode formeJuridique, WorkflowStatus statut);

    boolean existsByFormeJuridiqueAndNumeroVersion(LegalFormCode formeJuridique, String numeroVersion);

    boolean existsByFormeJuridiqueAndStatut(LegalFormCode formeJuridique, WorkflowStatus statut);
}

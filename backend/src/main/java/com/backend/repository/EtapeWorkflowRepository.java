package com.backend.repository;

import com.backend.entity.EtapeWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EtapeWorkflowRepository extends JpaRepository<EtapeWorkflow, UUID> {

    List<EtapeWorkflow> findByWorkflowVersionIdOrderByOrdreAsc(UUID workflowVersionId);
}

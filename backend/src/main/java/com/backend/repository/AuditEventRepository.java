package com.backend.repository;

import com.backend.entity.AuditEvent;
import com.backend.enums.AuditEventAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByDossierIdOrderByOccurredAtDesc(UUID dossierId);

    Page<AuditEvent> findByDossierIdOrderByOccurredAtDesc(UUID dossierId, Pageable pageable);

    Page<AuditEvent> findByStepTrackingIdInOrderByOccurredAtDesc(Collection<UUID> stepTrackingIds, Pageable pageable);

    List<AuditEvent> findByActorIdAndActionInOrderByOccurredAtDesc(UUID actorId, Collection<AuditEventAction> actions);

    List<AuditEvent> findByDossierIdAndActorIdAndActionInOrderByOccurredAtDesc(
            UUID dossierId, UUID actorId, Collection<AuditEventAction> actions);
}

package com.backend.entity;

import com.backend.enums.ActorType;
import com.backend.enums.AuditEventAction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditEventAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Utilisateur actor;

    @Column(length = 150)
    private String actorNom;

    @Column
    private UUID stepTrackingId;

    @Column(length = 1000)
    private String reason;

    @Column(length = 2000)
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    public AuditEvent() {}

    public AuditEvent(Dossier dossier, AuditEventAction action, ActorType actorType, Utilisateur actor, String actorNom, UUID stepTrackingId, String reason, String details) {
        this.dossier = dossier;
        this.action = action;
        this.actorType = actorType;
        this.actor = actor;
        this.actorNom = actorNom;
        this.stepTrackingId = stepTrackingId;
        this.reason = reason;
        this.details = details;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Dossier getDossier() { return dossier; }
    public void setDossier(Dossier dossier) { this.dossier = dossier; }

    public AuditEventAction getAction() { return action; }
    public void setAction(AuditEventAction action) { this.action = action; }

    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }

    public Utilisateur getActor() { return actor; }
    public void setActor(Utilisateur actor) { this.actor = actor; }

    public String getActorNom() { return actorNom; }
    public void setActorNom(String actorNom) { this.actorNom = actorNom; }

    public UUID getStepTrackingId() { return stepTrackingId; }
    public void setStepTrackingId(UUID stepTrackingId) { this.stepTrackingId = stepTrackingId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

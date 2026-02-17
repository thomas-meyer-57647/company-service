package de.innologic.companyservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "deletion_tombstone")
@Getter
@Setter
public class DeletionTombstoneEntity {

    @Id
    @Column(name = "deletion_id", nullable = false, length = 36)
    private String deletionId;

    @Column(name = "company_id", nullable = false, length = 36)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private DeletionState state;

    @Column(name = "requested_at_utc", nullable = false)
    private Instant requestedAtUtc;

    @Column(name = "requested_by_sub", nullable = false, length = 100)
    private String requestedBySub;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "completed_at_utc")
    private Instant completedAtUtc;

    @Column(name = "failed_at_utc")
    private Instant failedAtUtc;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}

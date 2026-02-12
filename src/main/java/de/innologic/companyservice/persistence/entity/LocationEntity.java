package de.innologic.companyservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "location")
@Getter
@Setter
public class LocationEntity {

    @Id
    @Column(name = "location_id", nullable = false, length = 36)
    private String locationId;

    @Column(name = "company_id", nullable = false, length = 36)
    private String companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location_code", length = 64)
    private String locationCode;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LocationStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "trashed_by", length = 100)
    private String trashedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "trashed_cause", length = 16)
    private TrashedCause trashedCause;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

package de.innologic.companyservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "company")
@Getter
@Setter
public class CompanyEntity {

    @Id
    @Column(name = "company_id", nullable = false, length = 36)
    private String companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "locale", length = 32)
    private String locale;

    @Column(name = "logo_file_ref")
    private String logoFileRef;

    @Column(name = "main_location_id", nullable = false, length = 36)
    private String mainLocationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "trashed_by", length = 100)
    private String trashedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}

package de.innologic.companyservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "deletion_ack")
@IdClass(DeletionAckEntity.DeletionAckId.class)
@Getter
@Setter
public class DeletionAckEntity {

    @Id
    @Column(name = "deletion_id", nullable = false, length = 36)
    private String deletionId;

    @Id
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "acked_at_utc", nullable = false)
    private Instant ackedAtUtc;

    @Column(name = "acked_by_sub", nullable = false, length = 100)
    private String ackedBySub;

    public static class DeletionAckId implements Serializable {
        private String deletionId;
        private String serviceName;

        public DeletionAckId() {
        }

        public DeletionAckId(String deletionId, String serviceName) {
            this.deletionId = deletionId;
            this.serviceName = serviceName;
        }

        public String getDeletionId() {
            return deletionId;
        }

        public void setDeletionId(String deletionId) {
            this.deletionId = deletionId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }
}

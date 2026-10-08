package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecordEntity {

    @EmbeddedId
    public Key id;

    @Column(name = "request_hash")
    public String requestHash;

    public String status;

    @Column(name = "response_status")
    public Integer responseStatus;

    @Column(name = "response_body")
    public String responseBody;

    @Column(name = "content_type")
    public String contentType;

    public String location;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Embeddable
    public record Key(
            @Column(name = "scope") String scope,
            @Column(name = "idempotency_key") String idempotencyKey) implements Serializable {
    }
}

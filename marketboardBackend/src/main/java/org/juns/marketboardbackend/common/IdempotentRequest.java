package org.juns.marketboardbackend.common;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotent_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "operation", "request_key"}))
public class IdempotentRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 40) private String operation;
    @Column(name = "request_key", nullable = false, length = 36) private String requestKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "response_json", nullable = false, columnDefinition = "MEDIUMTEXT") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected IdempotentRequest() {}
    public IdempotentRequest(Long userId, String operation, String key, String hash, String response) {
        this.userId = userId; this.operation = operation; this.requestKey = key;
        this.requestHash = hash; this.responseJson = response; this.createdAt = Instant.now();
    }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
}

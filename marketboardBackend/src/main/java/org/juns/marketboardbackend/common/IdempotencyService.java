package org.juns.marketboardbackend.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {
    private final EntityManager em;
    private final ObjectMapper mapper;
    public IdempotencyService(EntityManager em, ObjectMapper mapper) { this.em = em; this.mapper = mapper; }

    // Serialize protected creations per user across instances. The business write and replay
    // response commit together; rollback leaves neither. External calculations must be read-only.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T execute(Long userId, String operation, String key, Object input, Class<T> type, Supplier<T> action) {
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Idempotency-Key에 UUID를 지정하세요.");
        }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        if (em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) throw new ResourceNotFoundException("User not found");
        var existing = em.createQuery("select r from IdempotentRequest r where r.userId = :user and r.operation = :operation and r.requestKey = :key", IdempotentRequest.class)
                .setParameter("user", userId).setParameter("operation", operation).setParameter("key", key).getResultList();
        if (!existing.isEmpty()) {
            var record = existing.get(0);
            if (!record.getRequestHash().equals(hash)) throw new IdempotencyConflictException();
            return mapper.readValue(record.getResponseJson(), type);
        }
        T response = action.get();
        em.persist(new IdempotentRequest(userId, operation, key, hash, mapper.writeValueAsString(response)));
        em.flush();
        return response;
    }
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() { super("같은 요청 키에 다른 입력을 사용할 수 없습니다."); }
    }
}

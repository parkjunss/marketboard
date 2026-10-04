package org.juns.marketboardbackend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.user.User;

@Entity
@Table(name = "oauth_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OAuthProvider provider;
    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OAuthAccount(User user, OAuthProvider provider, String providerSubject) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = Instant.now();
    }
}

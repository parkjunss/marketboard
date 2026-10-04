package org.juns.marketboardbackend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}

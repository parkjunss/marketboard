package org.juns.marketboardbackend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import(PortfolioStrategyService.class)
class PortfolioStrategyServiceTest {

    @Autowired PortfolioStrategyService service;
    @Autowired PortfolioRepository portfolios;
    @Autowired PortfolioWeightRuleRepository rules;
    @Autowired PortfolioInvestmentThesisRepository theses;
    @Autowired SymbolRepository symbols;
    @Autowired UserRepository users;
    @MockitoBean SymbolResolutionService symbolResolution;

    @Test
    void upsertsRuleAndCreatesImmutableThesisRevisions() {
        User user = user("strategy@example.com");
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(user).name("Main").build());
        Symbol symbol = symbols.save(Symbol.builder().ticker("AAA").name("AAA")
                .exchange("NASDAQ").priority(1).build());
        when(symbolResolution.resolveOrFetch("AAA")).thenReturn(symbol);

        service.putRule(user.getId(), portfolio.getId(), new BigDecimal("0.25"));
        var first = service.createThesis(user.getId(), portfolio.getId(), "AAA", "성장 지속",
                "매출 역성장", new BigDecimal("0.15"), new BigDecimal("0.20"));
        var second = service.reviseThesis(user.getId(), portfolio.getId(), first.id(), "수익성 동반 성장",
                "영업이익률 하락", new BigDecimal("0.18"), new BigDecimal("0.22"));

        assertThat(service.getRule(user.getId(), portfolio.getId()).maxPositionWeight())
                .isEqualByComparingTo("0.25");
        assertThat(first.revision()).isEqualTo(1);
        assertThat(second.revision()).isEqualTo(2);
        assertThat(service.getTheses(user.getId(), portfolio.getId()))
                .extracting(PortfolioStrategyService.ThesisResponse::revision)
                .containsExactly(2, 1);
        assertThat(service.snapshot(user.getId()).get(portfolio.getId()).theses())
                .extracting(PortfolioStrategyService.ThesisResponse::revision)
                .containsExactly(2);
        assertThat(theses.count()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidWeightsAndOtherUsersPortfolio() {
        User owner = user("owner-strategy@example.com");
        User other = user("other-strategy@example.com");
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(owner).name("Main").build());

        assertThatThrownBy(() -> service.putRule(other.getId(), portfolio.getId(), new BigDecimal("0.2")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createThesis(owner.getId(), portfolio.getId(), "AAA", "가설", "무효화",
                new BigDecimal("0.3"), new BigDecimal("0.2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User user(String email) {
        return users.save(User.builder().email(email).username(email)
                .passwordHash("unused").role(Role.USER).build());
    }
}

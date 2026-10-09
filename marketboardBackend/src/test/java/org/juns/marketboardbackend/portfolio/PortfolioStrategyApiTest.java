package org.juns.marketboardbackend.portfolio;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioStrategyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired SymbolRepository symbols;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SymbolResolutionService symbolResolution;

    @Test
    void ruleAndThesisRevisionAreOwnerScoped() throws Exception {
        User owner = user("strategy-owner");
        User other = user("strategy-other");
        Portfolio portfolio = portfolios.save(Portfolio.builder().user(owner).name("Main").build());
        Symbol symbol = symbols.save(Symbol.builder().ticker("STR" + UUID.randomUUID().toString().substring(0, 5))
                .name("Strategy").exchange("NASDAQ").priority(1).build());
        when(symbolResolution.resolveOrFetch(symbol.getTicker())).thenReturn(symbol);
        String ownerToken = bearer(owner);
        try {
            mvc.perform(put("/api/portfolios/{id}/rules", portfolio.getId())
                            .header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"maxPositionWeight\":0.25}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.maxPositionWeight").value(0.25));
            String created = mvc.perform(post("/api/portfolios/{id}/theses", portfolio.getId())
                            .header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticker\":\"" + symbol.getTicker() + "\",\"thesis\":\"성장 지속\","
                                    + "\"invalidationCondition\":\"매출 역성장\",\"targetWeight\":0.15,\"maxWeight\":0.2}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(1))
                    .andReturn().getResponse().getContentAsString();
            long thesisId = mapper.readTree(created).get("id").asLong();
            mvc.perform(put("/api/portfolios/{portfolioId}/theses/{thesisId}", portfolio.getId(), thesisId)
                            .header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"thesis\":\"수익성 동반 성장\",\"invalidationCondition\":\"마진 하락\","
                                    + "\"targetWeight\":0.18,\"maxWeight\":0.22}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
            mvc.perform(get("/api/portfolios/{id}/theses", portfolio.getId())
                            .header("Authorization", ownerToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(get("/api/portfolios/{id}/theses", portfolio.getId())
                            .header("Authorization", bearer(other)))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("delete from portfolio_investment_theses where portfolio_id = ?", portfolio.getId());
            jdbc.update("delete from portfolio_weight_rules where portfolio_id = ?", portfolio.getId());
            portfolios.deleteById(portfolio.getId());
            symbols.deleteById(symbol.getId());
            users.deleteAllById(java.util.List.of(owner.getId(), other.getId()));
        }
    }

    private User user(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return users.save(User.builder().email(prefix + suffix + "@test.com").username(prefix + suffix)
                .passwordHash("unused").role(Role.USER).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateAccessToken(user.getId(), user.getEmail(), Role.USER);
    }
}

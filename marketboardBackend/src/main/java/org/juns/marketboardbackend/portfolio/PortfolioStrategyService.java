package org.juns.marketboardbackend.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioStrategyService {

    public record RuleResponse(Long portfolioId, BigDecimal maxPositionWeight, Instant updatedAt) {}
    public record ThesisResponse(Long id, Long portfolioId, Long symbolId, String ticker, int revision,
            String thesis, String invalidationCondition, BigDecimal targetWeight,
            BigDecimal maxWeight, Instant createdAt) {}
    public record StrategySnapshot(RuleResponse rule, List<ThesisResponse> theses) {}

    private final PortfolioRepository portfolios;
    private final PortfolioWeightRuleRepository rules;
    private final PortfolioInvestmentThesisRepository theses;
    private final SymbolResolutionService symbols;

    public PortfolioStrategyService(
            PortfolioRepository portfolios,
            PortfolioWeightRuleRepository rules,
            PortfolioInvestmentThesisRepository theses,
            SymbolResolutionService symbols) {
        this.portfolios = portfolios;
        this.rules = rules;
        this.theses = theses;
        this.symbols = symbols;
    }

    @Transactional(readOnly = true)
    public RuleResponse getRule(Long userId, Long portfolioId) {
        ownedPortfolio(userId, portfolioId);
        return rules.findByPortfolioId(portfolioId).map(this::ruleResponse).orElse(null);
    }

    @Transactional
    public RuleResponse putRule(Long userId, Long portfolioId, BigDecimal maxPositionWeight) {
        Portfolio portfolio = lockedPortfolio(userId, portfolioId);
        PortfolioWeightRule rule = rules.findByPortfolioId(portfolioId)
                .orElseGet(() -> new PortfolioWeightRule(portfolio, maxPositionWeight));
        rule.update(maxPositionWeight);
        return ruleResponse(rules.save(rule));
    }

    @Transactional(readOnly = true)
    public List<ThesisResponse> getTheses(Long userId, Long portfolioId) {
        ownedPortfolio(userId, portfolioId);
        return theses.findByPortfolio_IdOrderBySymbol_IdAscRevisionDesc(portfolioId).stream()
                .map(this::thesisResponse).toList();
    }

    @Transactional
    public ThesisResponse createThesis(Long userId, Long portfolioId, String ticker,
            String thesis, String invalidationCondition, BigDecimal targetWeight, BigDecimal maxWeight) {
        validateWeights(targetWeight, maxWeight);
        Portfolio portfolio = lockedPortfolio(userId, portfolioId);
        Symbol symbol = symbols.resolveOrFetch(ticker);
        int revision = nextRevision(portfolioId, symbol.getId());
        return thesisResponse(theses.save(new PortfolioInvestmentThesis(
                portfolio, symbol, revision, thesis, invalidationCondition, targetWeight, maxWeight)));
    }

    @Transactional
    public ThesisResponse reviseThesis(Long userId, Long portfolioId, Long thesisId,
            String thesis, String invalidationCondition, BigDecimal targetWeight, BigDecimal maxWeight) {
        validateWeights(targetWeight, maxWeight);
        Portfolio portfolio = lockedPortfolio(userId, portfolioId);
        PortfolioInvestmentThesis previous = theses.findById(thesisId)
                .filter(row -> row.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Investment thesis not found: " + thesisId));
        int revision = nextRevision(portfolioId, previous.getSymbol().getId());
        return thesisResponse(theses.save(new PortfolioInvestmentThesis(
                portfolio, previous.getSymbol(), revision, thesis, invalidationCondition, targetWeight, maxWeight)));
    }

    @Transactional(readOnly = true)
    public Map<Long, StrategySnapshot> snapshot(Long userId) {
        Map<Long, StrategySnapshot> result = new LinkedHashMap<>();
        for (Portfolio portfolio : portfolios.findByUser_IdOrderByCreatedAtAsc(userId)) {
            RuleResponse rule = rules.findByPortfolioId(portfolio.getId()).map(this::ruleResponse).orElse(null);
            List<ThesisResponse> current = currentTheses(
                    theses.findByPortfolio_IdOrderBySymbol_IdAscRevisionDesc(portfolio.getId()));
            result.put(portfolio.getId(), new StrategySnapshot(rule, current));
        }
        return result;
    }

    private List<ThesisResponse> currentTheses(List<PortfolioInvestmentThesis> history) {
        Map<Long, ThesisResponse> current = new LinkedHashMap<>();
        for (PortfolioInvestmentThesis row : history) {
            current.putIfAbsent(row.getSymbol().getId(), thesisResponse(row));
        }
        return List.copyOf(current.values());
    }

    private int nextRevision(Long portfolioId, Long symbolId) {
        return theses.findByPortfolio_IdOrderBySymbol_IdAscRevisionDesc(portfolioId).stream()
                .filter(row -> row.getSymbol().getId().equals(symbolId))
                .mapToInt(PortfolioInvestmentThesis::getRevision).max().orElse(0) + 1;
    }

    private void validateWeights(BigDecimal targetWeight, BigDecimal maxWeight) {
        if (targetWeight.compareTo(maxWeight) > 0) {
            throw new IllegalArgumentException("목표 비중은 최대 비중을 초과할 수 없습니다.");
        }
    }

    private Portfolio ownedPortfolio(Long userId, Long portfolioId) {
        return portfolios.findByIdAndUser_Id(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
    }

    private Portfolio lockedPortfolio(Long userId, Long portfolioId) {
        return portfolios.findOwnedByIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
    }

    private RuleResponse ruleResponse(PortfolioWeightRule rule) {
        return new RuleResponse(rule.getPortfolioId(), rule.getMaxPositionWeight(), rule.getUpdatedAt());
    }

    private ThesisResponse thesisResponse(PortfolioInvestmentThesis row) {
        return new ThesisResponse(row.getId(), row.getPortfolio().getId(), row.getSymbol().getId(),
                row.getSymbol().getTicker(), row.getRevision(), row.getThesis(), row.getInvalidationCondition(),
                row.getTargetWeight(), row.getMaxWeight(), row.getCreatedAt());
    }
}

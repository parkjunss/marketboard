package org.juns.marketboardbackend.portfolio;

import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.common.exception.DuplicatePortfolioPositionException;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionResponse;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionUpdateRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioSummaryResponse;
import org.juns.marketboardbackend.quote.QuoteService;
import org.juns.marketboardbackend.quote.ResolvedPrice;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;
    private final UserRepository userRepository;
    private final SymbolResolutionService symbolResolutionService;
    private final QuoteService quoteService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository,
            UserRepository userRepository,
            SymbolResolutionService symbolResolutionService,
            QuoteService quoteService) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
        this.userRepository = userRepository;
        this.symbolResolutionService = symbolResolutionService;
        this.quoteService = quoteService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> getPortfolios(Long userId) {
        return portfolioRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                .map(portfolio -> PortfolioSummaryResponse.of(portfolio, buildPositionResponses(portfolio.getId())))
                .toList();
    }

    @Transactional
    public PortfolioSummaryResponse createPortfolio(Long userId, String name) {
        User user = userRepository.getReferenceById(userId);
        Portfolio saved = portfolioRepository.save(Portfolio.builder().user(user).name(name).build());
        return PortfolioSummaryResponse.of(saved, List.of());
    }

    @Transactional
    public PortfolioSummaryResponse renamePortfolio(Long userId, Long portfolioId, String name) {
        Portfolio portfolio = getOwnedPortfolio(userId, portfolioId);
        portfolio.rename(name);
        return PortfolioSummaryResponse.of(portfolio, buildPositionResponses(portfolio.getId()));
    }

    @Transactional
    public void deletePortfolio(Long userId, Long portfolioId) {
        Portfolio portfolio = getOwnedPortfolio(userId, portfolioId);
        portfolioPositionRepository.deleteByPortfolio_Id(portfolio.getId());
        portfolioRepository.delete(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioPositionResponse> getPositions(Long userId, Long portfolioId) {
        getOwnedPortfolio(userId, portfolioId);
        return buildPositionResponses(portfolioId);
    }

    @Transactional
    public PortfolioPositionResponse addPosition(Long userId, Long portfolioId, PortfolioPositionRequest request) {
        Portfolio portfolio = getOwnedPortfolio(userId, portfolioId);
        Symbol symbol = symbolResolutionService.resolveOrFetch(request.ticker());
        if (portfolioPositionRepository.existsByPortfolio_IdAndSymbol_Id(portfolioId, symbol.getId())) {
            throw new DuplicatePortfolioPositionException(symbol.getTicker());
        }
        PortfolioPosition saved = portfolioPositionRepository.save(PortfolioPosition.builder()
                .portfolio(portfolio)
                .symbol(symbol)
                .quantity(request.quantity())
                .avgCost(request.avgCost())
                .build());
        return PortfolioPositionResponse.from(saved, quoteService.resolvePrice(symbol.getTicker()).orElse(null));
    }

    @Transactional
    public PortfolioPositionResponse updatePosition(
            Long userId, Long portfolioId, Long positionId, PortfolioPositionUpdateRequest request) {
        getOwnedPortfolio(userId, portfolioId);
        PortfolioPosition position = portfolioPositionRepository
                .findByIdAndPortfolio_Id(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio position not found: " + positionId));
        position.update(request.quantity(), request.avgCost());
        return PortfolioPositionResponse.from(
                position, quoteService.resolvePrice(position.getSymbol().getTicker()).orElse(null));
    }

    @Transactional
    public void removePosition(Long userId, Long portfolioId, Long positionId) {
        getOwnedPortfolio(userId, portfolioId);
        PortfolioPosition position = portfolioPositionRepository
                .findByIdAndPortfolio_Id(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio position not found: " + positionId));
        portfolioPositionRepository.delete(position);
    }

    private Portfolio getOwnedPortfolio(Long userId, Long portfolioId) {
        return portfolioRepository
                .findByIdAndUser_Id(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
    }

    private List<PortfolioPositionResponse> buildPositionResponses(Long portfolioId) {
        List<PortfolioPosition> positions = portfolioPositionRepository.findByPortfolio_IdOrderByIdAsc(portfolioId);
        List<String> tickers = positions.stream().map(position -> position.getSymbol().getTicker()).toList();
        Map<String, ResolvedPrice> prices = quoteService.resolvePrices(tickers);
        return positions.stream()
                .map(position -> PortfolioPositionResponse.from(
                        position, prices.get(position.getSymbol().getTicker().toUpperCase())))
                .toList();
    }
}

package org.juns.marketboardbackend.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.juns.marketboardbackend.common.exception.PortfolioLedgerConflictException;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionResponse;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolResolutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioTransactionService {

    private final PortfolioRepository portfolios;
    private final PortfolioPositionRepository positions;
    private final PortfolioTransactionRepository transactions;
    private final SymbolResolutionService symbols;
    private final boolean writesEnabled;

    public PortfolioTransactionService(
            PortfolioRepository portfolios,
            PortfolioPositionRepository positions,
            PortfolioTransactionRepository transactions,
            SymbolResolutionService symbols,
            @Value("${marketboard.portfolio-ledger.writes-enabled:false}") boolean writesEnabled) {
        this.portfolios = portfolios;
        this.positions = positions;
        this.transactions = transactions;
        this.symbols = symbols;
        this.writesEnabled = writesEnabled;
    }

    @Transactional
    public PortfolioTransactionResponse create(
            Long userId, Long portfolioId, PortfolioTransactionRequest request, String sourceTransactionKey) {
        assertWritesEnabled();
        if (request.type() != PortfolioTransactionType.BUY && request.type() != PortfolioTransactionType.SELL) {
            throw new IllegalArgumentException("BUY 또는 SELL 거래만 기록할 수 있습니다.");
        }

        Symbol symbol = symbols.resolveOrFetch(request.ticker());
        Portfolio portfolio = portfolios.findOwnedByIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        transactions.findFirstByPortfolio_IdOrderByOccurredAtDescIdDesc(portfolioId)
                .filter(latest -> request.occurredAt().isBefore(latest.getOccurredAt()))
                .ifPresent(latest -> {
                    throw new PortfolioLedgerConflictException("최신 거래보다 과거 시점의 거래는 기록할 수 없습니다.");
                });

        PortfolioTransaction transaction = request.type() == PortfolioTransactionType.BUY
                ? PortfolioTransaction.buy(portfolio, symbol, request.quantity(), request.unitPrice(),
                        request.fee(), request.occurredAt(), sourceTransactionKey)
                : PortfolioTransaction.sell(portfolio, symbol, request.quantity(), request.unitPrice(),
                        request.fee(), request.occurredAt(), sourceTransactionKey);
        applyToPosition(portfolio, symbol, transaction);
        return PortfolioTransactionResponse.from(transactions.saveAndFlush(transaction));
    }

    @Transactional(readOnly = true)
    public List<PortfolioTransactionResponse> getTransactions(Long userId, Long portfolioId) {
        portfolios.findByIdAndUser_Id(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        return transactions.findByPortfolio_IdOrderByOccurredAtAscIdAsc(portfolioId).stream()
                .map(PortfolioTransactionResponse::from)
                .toList();
    }

    public void assertDirectPositionWriteAllowed() {
        if (writesEnabled) {
            throw new PortfolioLedgerConflictException("원장 전환 후에는 포지션을 직접 편집할 수 없습니다.");
        }
    }

    private void assertWritesEnabled() {
        if (!writesEnabled) {
            throw new PortfolioLedgerConflictException("포트폴리오 거래 원장이 아직 활성화되지 않았습니다.");
        }
    }

    private void applyToPosition(Portfolio portfolio, Symbol symbol, PortfolioTransaction transaction) {
        PortfolioPosition position = positions.findByPortfolio_IdAndSymbol_Id(portfolio.getId(), symbol.getId())
                .orElse(null);
        if (transaction.getTransactionType() == PortfolioTransactionType.BUY) {
            applyBuy(portfolio, symbol, position, transaction);
        } else {
            applySell(position, transaction);
        }
    }

    private void applyBuy(
            Portfolio portfolio, Symbol symbol, PortfolioPosition position, PortfolioTransaction transaction) {
        BigDecimal oldQuantity = position == null ? BigDecimal.ZERO : position.getQuantity();
        BigDecimal oldCost = position == null
                ? BigDecimal.ZERO
                : position.getQuantity().multiply(position.getAvgCost());
        BigDecimal newQuantity = oldQuantity.add(transaction.getQuantity());
        BigDecimal newCost = oldCost
                .add(transaction.getQuantity().multiply(transaction.getUnitPrice()))
                .add(transaction.getFee());
        BigDecimal averageCost = newCost.divide(newQuantity, 4, RoundingMode.HALF_UP);
        if (position == null) {
            positions.save(PortfolioPosition.builder()
                    .portfolio(portfolio).symbol(symbol)
                    .quantity(newQuantity).avgCost(averageCost).build());
        } else {
            position.update(newQuantity, averageCost);
        }
    }

    private void applySell(PortfolioPosition position, PortfolioTransaction transaction) {
        if (position == null || position.getQuantity().compareTo(transaction.getQuantity()) < 0) {
            throw new PortfolioLedgerConflictException("보유 수량을 초과해 매도할 수 없습니다.");
        }
        BigDecimal remaining = position.getQuantity().subtract(transaction.getQuantity());
        if (remaining.signum() == 0) {
            positions.delete(position);
        } else {
            position.update(remaining, position.getAvgCost());
        }
    }
}

package org.juns.marketboardbackend.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioOpeningBalanceBackfillService {

    private final PortfolioOpeningBalanceBackfillRepository backfillRepository;
    private final PortfolioTransactionRepository transactions;

    public PortfolioOpeningBalanceBackfillService(
            PortfolioOpeningBalanceBackfillRepository backfillRepository,
            PortfolioTransactionRepository transactions) {
        this.backfillRepository = backfillRepository;
        this.transactions = transactions;
    }

    @Transactional
    public Result backfill() {
        List<PortfolioPosition> currentPositions = backfillRepository.findAllPositionsForUpdate();
        Map<Long, PortfolioTransaction> openings = openingsBySourcePosition();
        int existing = openings.size();

        List<PortfolioTransaction> missing = currentPositions.stream()
                .filter(position -> !openings.containsKey(position.getId()))
                .map(this::openingFrom)
                .toList();
        transactions.saveAll(missing);
        transactions.flush();

        Map<Long, PortfolioTransaction> verified = openingsBySourcePosition();
        for (PortfolioPosition position : currentPositions) {
            verify(position, verified.get(position.getId()));
        }
        if (verified.size() != currentPositions.size()) {
            throw new IllegalStateException("기초잔고 수와 현재 포지션 수가 일치하지 않습니다.");
        }
        BigDecimal positionQuantity = currentPositions.stream()
                .map(PortfolioPosition::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal positionCost = currentPositions.stream()
                .map(position -> position.getQuantity().multiply(position.getAvgCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openingQuantity = verified.values().stream()
                .map(PortfolioTransaction::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openingCost = verified.values().stream()
                .map(opening -> opening.getQuantity().multiply(opening.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (positionQuantity.compareTo(openingQuantity) != 0 || positionCost.compareTo(openingCost) != 0) {
            throw new IllegalStateException("기초잔고 수량 또는 원가 합계가 현재 포지션과 일치하지 않습니다.");
        }
        return new Result(currentPositions.size(), existing, missing.size(),
                positionQuantity, positionCost, Instant.now());
    }

    private Map<Long, PortfolioTransaction> openingsBySourcePosition() {
        Map<Long, PortfolioTransaction> result = new HashMap<>();
        for (PortfolioTransaction opening : transactions.findByTransactionTypeOrderBySourcePositionIdAsc(
                PortfolioTransactionType.OPENING_BALANCE)) {
            if (opening.getSourcePositionId() == null || result.put(opening.getSourcePositionId(), opening) != null) {
                throw new IllegalStateException("중복되거나 출처가 없는 기초잔고가 있습니다.");
            }
        }
        return result;
    }

    private PortfolioTransaction openingFrom(PortfolioPosition position) {
        try {
            return PortfolioTransaction.openingBalance(
                    position.getPortfolio(), position.getSymbol(), position.getQuantity(),
                    position.getAvgCost(), position.getId(), position.getUpdatedAt());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "포지션을 기초잔고로 변환할 수 없습니다. positionId=" + position.getId(), exception);
        }
    }

    private void verify(PortfolioPosition position, PortfolioTransaction opening) {
        if (opening == null
                || !position.getPortfolio().getId().equals(opening.getPortfolio().getId())
                || !position.getSymbol().getId().equals(opening.getSymbol().getId())
                || position.getQuantity().compareTo(opening.getQuantity()) != 0
                || position.getAvgCost().compareTo(opening.getUnitPrice()) != 0) {
            throw new IllegalStateException("기초잔고 검증에 실패했습니다. positionId=" + position.getId());
        }
    }

    public record Result(int totalPositions, int alreadyMigrated, int inserted,
            BigDecimal totalQuantity, BigDecimal totalCost, Instant completedAt) {}
}

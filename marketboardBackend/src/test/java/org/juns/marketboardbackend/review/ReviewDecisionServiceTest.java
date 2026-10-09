package org.juns.marketboardbackend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import org.juns.marketboardbackend.marketbreadth.MarketBreadthService;
import org.juns.marketboardbackend.marketindex.MarketIndexHistoryService;
import org.juns.marketboardbackend.portfolio.PortfolioService;
import org.juns.marketboardbackend.portfolio.PortfolioTransactionService;
import org.juns.marketboardbackend.portfolio.PortfolioStrategyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ReviewDecisionServiceTest {
    @Mock InvestmentReviewRepository reviews;
    @Mock ReviewDecisionRepository decisions;
    @Mock MarketIndexHistoryService indices;
    @Mock MarketBreadthService breadth;
    @Mock PortfolioService portfolios;
    @Mock PortfolioTransactionService transactions;
    @Mock PortfolioStrategyService strategy;
    @Mock ObjectMapper mapper;

    @Test
    void decisionBelongsToOwnedReviewAndOnlyDeferredChoiceKeepsFollowUpDate() {
        var service = new ReviewService(reviews, indices, breadth, portfolios, mapper, decisions, transactions, strategy);
        when(reviews.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(mock(InvestmentReview.class)));
        when(decisions.save(any())).thenAnswer(call -> call.getArgument(0));

        var saved = service.decide(7L, 3L, ReviewDecision.Choice.HOLD, "  그대로 유지  ", LocalDate.now());

        assertThat(saved.reason()).isEqualTo("그대로 유지");
        assertThat(saved.followUpDate()).isNull();
        verify(reviews).findByIdAndUserId(3L, 7L);
    }
}

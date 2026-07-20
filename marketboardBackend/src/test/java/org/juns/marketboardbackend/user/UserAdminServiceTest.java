package org.juns.marketboardbackend.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.juns.marketboardbackend.alert.AlertRepository;
import org.juns.marketboardbackend.auth.RefreshTokenService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.common.exception.SelfDeleteNotAllowedException;
import org.juns.marketboardbackend.dashboard.DashboardConfigRepository;
import org.juns.marketboardbackend.portfolio.Portfolio;
import org.juns.marketboardbackend.portfolio.PortfolioPositionRepository;
import org.juns.marketboardbackend.portfolio.PortfolioRepository;
import org.juns.marketboardbackend.watchlist.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private WatchlistItemRepository watchlistItemRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private DashboardConfigRepository dashboardConfigRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioPositionRepository portfolioPositionRepository;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(
                userRepository, refreshTokenService, watchlistItemRepository,
                alertRepository, dashboardConfigRepository, portfolioRepository, portfolioPositionRepository);
    }

    private User user(long id) {
        User user = User.builder().email("e2e-check-1@example.com").passwordHash("hash").username("e2e").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Portfolio portfolio(long id, User owner) {
        Portfolio portfolio = Portfolio.builder().user(owner).name("Main").build();
        ReflectionTestUtils.setField(portfolio, "id", id);
        return portfolio;
    }

    @Test
    void delete_existingUser_removesOwnedDataThenTheUser() {
        User target = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        Portfolio portfolio = portfolio(10L, target);
        when(portfolioRepository.findByUser_IdOrderByCreatedAtAsc(5L)).thenReturn(List.of(portfolio));

        userAdminService.delete(5L, 1L);

        verify(refreshTokenService).revoke(5L);
        verify(watchlistItemRepository).deleteByUser_Id(5L);
        verify(alertRepository).deleteByUser_Id(5L);
        verify(dashboardConfigRepository).deleteByUser_Id(5L);
        verify(portfolioPositionRepository).deleteByPortfolio_Id(10L);
        verify(portfolioRepository).deleteAll(List.of(portfolio));
        verify(userRepository, times(1)).delete(target);
    }

    @Test
    void delete_ownAccount_throwsAndDeletesNothing() {
        assertThatThrownBy(() -> userAdminService.delete(1L, 1L))
                .isInstanceOf(SelfDeleteNotAllowedException.class);

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void delete_unknownUser_throwsAndDeletesNothing() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.delete(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}

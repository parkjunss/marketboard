package org.juns.marketboardbackend.user;

import java.util.List;
import org.juns.marketboardbackend.alert.AlertRepository;
import org.juns.marketboardbackend.auth.RefreshTokenService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.common.exception.SelfDeleteNotAllowedException;
import org.juns.marketboardbackend.dashboard.DashboardConfigRepository;
import org.juns.marketboardbackend.portfolio.Portfolio;
import org.juns.marketboardbackend.portfolio.PortfolioPositionRepository;
import org.juns.marketboardbackend.portfolio.PortfolioRepository;
import org.juns.marketboardbackend.user.dto.UserResponse;
import org.juns.marketboardbackend.user.dto.UserUpdateRequest;
import org.juns.marketboardbackend.watchlist.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final WatchlistItemRepository watchlistItemRepository;
    private final AlertRepository alertRepository;
    private final DashboardConfigRepository dashboardConfigRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository portfolioPositionRepository;

    public UserAdminService(
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            WatchlistItemRepository watchlistItemRepository,
            AlertRepository alertRepository,
            DashboardConfigRepository dashboardConfigRepository,
            PortfolioRepository portfolioRepository,
            PortfolioPositionRepository portfolioPositionRepository) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.watchlistItemRepository = watchlistItemRepository;
        this.alertRepository = alertRepository;
        this.dashboardConfigRepository = dashboardConfigRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioPositionRepository = portfolioPositionRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findUser(id);
        user.changeRole(request.role());
        if (request.status() == UserStatus.SUSPENDED) {
            user.suspend();
            refreshTokenService.revoke(id);
        } else {
            user.reactivate();
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void revokeToken(Long id) {
        findUser(id);
        refreshTokenService.revoke(id);
    }

    /**
     * Hard-deletes a user and everything owned by them (watchlist, alerts, dashboard config,
     * portfolios/positions, refresh token) — irreversible short of a DB backup restore. Meant
     * for junk accounts (e.g. leftover e2e test users); {@link #update} with SUSPENDED status
     * is the reversible option for real users.
     */
    @Transactional
    public void delete(Long id, Long requesterId) {
        if (id.equals(requesterId)) {
            throw new SelfDeleteNotAllowedException();
        }
        User user = findUser(id);
        refreshTokenService.revoke(id);
        watchlistItemRepository.deleteByUser_Id(id);
        alertRepository.deleteByUser_Id(id);
        dashboardConfigRepository.deleteByUser_Id(id);
        List<Portfolio> portfolios = portfolioRepository.findByUser_IdOrderByCreatedAtAsc(id);
        for (Portfolio portfolio : portfolios) {
            portfolioPositionRepository.deleteByPortfolio_Id(portfolio.getId());
        }
        portfolioRepository.deleteAll(portfolios);
        userRepository.delete(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unknown user id " + id));
    }
}

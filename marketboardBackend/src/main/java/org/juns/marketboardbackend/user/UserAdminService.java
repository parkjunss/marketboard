package org.juns.marketboardbackend.user;

import java.util.List;
import org.juns.marketboardbackend.auth.RefreshTokenService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.user.dto.UserResponse;
import org.juns.marketboardbackend.user.dto.UserUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public UserAdminService(UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
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

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unknown user id " + id));
    }
}

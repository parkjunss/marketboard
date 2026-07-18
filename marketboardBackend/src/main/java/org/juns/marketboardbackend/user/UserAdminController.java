package org.juns.marketboardbackend.user;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.user.dto.UserResponse;
import org.juns.marketboardbackend.user.dto.UserUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userAdminService.getAll();
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userAdminService.update(id, request);
    }

    @PostMapping("/{id}/revoke-token")
    public ResponseEntity<Void> revokeToken(@PathVariable Long id) {
        userAdminService.revokeToken(id);
        return ResponseEntity.noContent().build();
    }
}

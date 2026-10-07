package org.juns.marketboardbackend.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    public record CreateRequest(@NotNull Integer period) {}
    public record DecisionRequest(@NotNull ReviewDecision.Choice choice,
                                  @NotBlank @Size(max = 2000) String reason,
                                  LocalDate followUpDate) {}
    private final ReviewService reviews;
    private final IdempotencyService idempotency;
    public ReviewController(ReviewService reviews, IdempotencyService idempotency) { this.reviews = reviews; this.idempotency = idempotency; }
    @PostMapping
    public ReviewService.Detail create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        // Capture before acquiring the per-user write lock. Replays may read fresh sources,
        // but return the original stored response and never replace its evidence.
        var payload = reviews.capture(user.id(), request.period());
        return idempotency.execute(user.id(), "review-create", key, request, ReviewService.Detail.class,
                () -> reviews.save(user.id(), payload));
    }
    @GetMapping
    public List<ReviewService.Summary> list(@AuthenticationPrincipal AuthenticatedUser user) { return reviews.list(user.id()); }
    @GetMapping("/{id}")
    public ReviewService.Detail get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { return reviews.get(user.id(), id); }
    @PostMapping("/{id}/decisions")
    public ReviewService.Decision decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                         @Valid @RequestBody DecisionRequest request) {
        return reviews.decide(user.id(), id, request.choice(), request.reason(), request.followUpDate());
    }
    @GetMapping("/{id}/decisions")
    public List<ReviewService.Decision> decisions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reviews.decisions(user.id(), id);
    }
}

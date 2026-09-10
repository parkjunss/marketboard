package org.juns.marketboardbackend.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    public record CreateRequest(@NotNull Integer period) {}
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
}

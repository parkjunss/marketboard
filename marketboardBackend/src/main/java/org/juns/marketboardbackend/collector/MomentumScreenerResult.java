package org.juns.marketboardbackend.collector;

import java.util.List;

public record MomentumScreenerResult(
        int universeSize, int screenedCount, int candidateCount, List<MomentumScreenerCandidate> results) {
}

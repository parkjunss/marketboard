package org.juns.marketboardbackend.admin.dto;

import org.juns.marketboardbackend.collector.CollectorHealth;

public record CollectorStatusResponse(boolean reachable, CollectorHealth health) {

    public static CollectorStatusResponse unreachable() {
        return new CollectorStatusResponse(false, null);
    }

    public static CollectorStatusResponse of(CollectorHealth health) {
        return new CollectorStatusResponse(true, health);
    }
}

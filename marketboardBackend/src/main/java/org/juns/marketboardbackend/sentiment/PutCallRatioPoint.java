package org.juns.marketboardbackend.sentiment;

import java.time.Instant;

public record PutCallRatioPoint(Instant timestamp, double ratio) {}

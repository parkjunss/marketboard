package org.juns.marketboardbackend.collector;

public record PutCallRatioResponse(String ticker, int expirationsUsed, double callVolume, double putVolume, double putCallRatio) {
}

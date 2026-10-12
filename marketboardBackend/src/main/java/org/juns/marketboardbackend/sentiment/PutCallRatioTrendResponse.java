package org.juns.marketboardbackend.sentiment;

import java.util.List;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;

public record PutCallRatioTrendResponse(
        String ticker,
        int expirationsUsed,
        double callVolume,
        double putVolume,
        double putCallRatio,
        List<PutCallRatioPoint> history) {

    public static PutCallRatioTrendResponse from(PutCallRatioResponse current, List<PutCallRatioPoint> history) {
        return new PutCallRatioTrendResponse(
                current.ticker(),
                current.expirationsUsed(),
                current.callVolume(),
                current.putVolume(),
                current.putCallRatio(),
                history);
    }
}

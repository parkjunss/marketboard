package org.juns.marketboardbackend.collector;

import org.juns.marketboardbackend.backtest.dto.EtfComparisonRequest;
import tools.jackson.databind.JsonNode;

public record EtfResearchEvidence(String calculationVersion, String strategyId,
        EtfComparisonRequest configuration, EtfComparisonResult comparison, JsonNode inputBars) {}

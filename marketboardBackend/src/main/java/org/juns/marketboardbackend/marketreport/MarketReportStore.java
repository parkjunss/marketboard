package org.juns.marketboardbackend.marketreport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class MarketReportStore {
    private final MarketReportRepository repository;
    private final ObjectMapper mapper;
    public MarketReportStore(MarketReportRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    // This short transaction is separate from capture and from the unique-key conflict recovery.
    @Transactional
    public MarketReport insert(MarketReportService.Payload payload) {
        return repository.saveAndFlush(new MarketReport(payload.session().tradingDate(), 1,
                payload.checks().isEmpty() ? "READY" : "PARTIAL", mapper.writeValueAsString(payload)));
    }
}

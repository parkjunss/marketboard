package org.juns.marketboardbackend.symbol;

import java.util.Comparator;
import java.util.List;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.DuplicateSymbolException;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.symbol.dto.SymbolBulkActiveRequest;
import org.juns.marketboardbackend.symbol.dto.SymbolCreateRequest;
import org.juns.marketboardbackend.symbol.dto.SymbolResponse;
import org.juns.marketboardbackend.symbol.dto.SymbolUpdateRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SymbolAdminService {

    private static final String SYMBOLS_TOPIC = "/topic/symbols";

    private final SymbolRepository symbolRepository;
    private final CollectorClient collectorClient;
    private final SimpMessagingTemplate messagingTemplate;

    public SymbolAdminService(
            SymbolRepository symbolRepository, CollectorClient collectorClient, SimpMessagingTemplate messagingTemplate) {
        this.symbolRepository = symbolRepository;
        this.collectorClient = collectorClient;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<SymbolResponse> getAll() {
        return symbolRepository.findAll().stream()
                .sorted(Comparator.comparing(Symbol::getPriority))
                .map(SymbolResponse::from)
                .toList();
    }

    @Transactional
    public SymbolResponse create(SymbolCreateRequest request) {
        String ticker = request.ticker().toUpperCase();
        if (symbolRepository.findByTickerIgnoreCase(ticker).isPresent()) {
            throw new DuplicateSymbolException(ticker);
        }
        Symbol saved = symbolRepository.save(
                Symbol.builder().ticker(ticker).name(request.name()).exchange(request.exchange()).priority(request.priority()).build());
        return SymbolResponse.from(saved);
    }

    @Transactional
    public SymbolResponse update(Long id, SymbolUpdateRequest request) {
        Symbol symbol = symbolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown symbol id " + id));
        symbol.updateDetails(request.name(), request.exchange(), request.priority());
        if (request.active()) {
            symbol.activate();
        } else {
            symbol.deactivate();
        }
        return SymbolResponse.from(symbol);
    }

    /**
     * Sets `active` for many symbols in one go; the collector/STOMP sync happens once, separately,
     * after this transaction commits (see {@link #syncActiveSymbols()}), instead of once per symbol.
     */
    @Transactional
    public List<SymbolResponse> bulkSetActive(SymbolBulkActiveRequest request) {
        List<Symbol> symbols = symbolRepository.findAllById(request.ids());
        for (Symbol symbol : symbols) {
            if (request.active()) {
                symbol.activate();
            } else {
                symbol.deactivate();
            }
        }
        return symbols.stream().map(SymbolResponse::from).toList();
    }

    /**
     * Pushes the current active-symbol set to the collector (real-time WS resubscribe) and
     * broadcasts it over STOMP. Deliberately NOT {@code @Transactional} and called by the
     * controller only after create/update/bulkSetActive's own transaction has already committed:
     * the collector's own {@code ensure_symbols} may try to insert the very same brand-new ticker
     * row, and doing that while this service still holds an open, uncommitted transaction on that
     * row causes a cross-process lock-wait deadlock (found 2026-07-18 adding a new active symbol).
     */
    public void syncActiveSymbols() {
        List<String> activeTickers = symbolRepository.findByActiveTrueOrderByPriorityAsc().stream()
                .map(Symbol::getTicker)
                .toList();
        collectorClient.syncSubscriptions(activeTickers);
        messagingTemplate.convertAndSend(SYMBOLS_TOPIC, activeTickers);
    }
}

package org.juns.marketboardbackend.symbol;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.symbol.dto.SymbolBulkActiveRequest;
import org.juns.marketboardbackend.symbol.dto.SymbolCreateRequest;
import org.juns.marketboardbackend.symbol.dto.SymbolResponse;
import org.juns.marketboardbackend.symbol.dto.SymbolUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/symbols")
public class SymbolAdminController {

    private final SymbolAdminService symbolAdminService;

    public SymbolAdminController(SymbolAdminService symbolAdminService) {
        this.symbolAdminService = symbolAdminService;
    }

    @GetMapping
    public List<SymbolResponse> getAll() {
        return symbolAdminService.getAll();
    }

    @PostMapping
    public ResponseEntity<SymbolResponse> create(@Valid @RequestBody SymbolCreateRequest request) {
        SymbolResponse response = symbolAdminService.create(request);
        symbolAdminService.syncActiveSymbols();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public SymbolResponse update(@PathVariable Long id, @Valid @RequestBody SymbolUpdateRequest request) {
        SymbolResponse response = symbolAdminService.update(id, request);
        symbolAdminService.syncActiveSymbols();
        return response;
    }

    @PatchMapping("/bulk-active")
    public List<SymbolResponse> bulkSetActive(@Valid @RequestBody SymbolBulkActiveRequest request) {
        List<SymbolResponse> response = symbolAdminService.bulkSetActive(request);
        symbolAdminService.syncActiveSymbols();
        return response;
    }
}

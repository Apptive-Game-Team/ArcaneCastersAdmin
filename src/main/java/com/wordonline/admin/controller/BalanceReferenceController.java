package com.wordonline.admin.controller;

import com.wordonline.admin.service.BalanceReferenceService;
import com.wordonline.admin.service.BalanceSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('WORDONLINE_ADMIN')")
public class BalanceReferenceController {
    private final BalanceReferenceService references;

    @PostMapping("/api/admin/spread-sheets/references")
    public Map<String, Map<String, BalanceSheetService.ReferenceTarget>> save(
            @RequestBody BalanceReferenceService.Change change) {
        return references.save(change);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> invalidReference(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(error.getReason());
    }
}

package com.ipos.controller;

import com.ipos.service.CatalogueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/catalogue")
public class CatalogueController {

    private final CatalogueService catalogueService;

    public CatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /** CAT-US1 — register the catalogue once. */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, String>> initialize() {
        catalogueService.initializeCatalogue();
        return ResponseEntity.ok(Map.of("message", "Catalogue initialized"));
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("initialized", catalogueService.isCatalogueInitialized());
    }
}

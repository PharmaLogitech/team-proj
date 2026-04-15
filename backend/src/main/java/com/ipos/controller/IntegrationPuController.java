package com.ipos.controller;

import com.ipos.dto.ApproveCommercialApplicationRequest;
import com.ipos.dto.CommercialApplicationDecisionResponse;
import com.ipos.dto.CommercialApplicationDetailDto;
import com.ipos.dto.CommercialApplicationListItemDto;
import com.ipos.dto.InboundCommercialApplicationRequest;
import com.ipos.dto.InboundCommercialApplicationResponse;
import com.ipos.dto.RejectCommercialApplicationRequest;
import com.ipos.entity.CommercialApplication;
import com.ipos.entity.CommercialApplicationStatus;
import com.ipos.service.CommercialApplicationService;
import com.ipos.service.CommercialApplicationService.ApplicationNotFoundException;
import com.ipos.service.CommercialApplicationService.DecisionResult;
import com.ipos.service.CommercialApplicationService.DuplicateExternalReferenceException;
import com.ipos.service.CommercialApplicationService.InvalidStatusException;
import com.ipos.service.CommercialApplicationService.WebhookOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/integration-pu")
public class IntegrationPuController {

    private final CommercialApplicationService commercialApplicationService;

    public IntegrationPuController(CommercialApplicationService commercialApplicationService) {
        this.commercialApplicationService = commercialApplicationService;
    }

    /**
     * IPOS-PU submits a new commercial application (server-to-server).
     * Secured by {@code X-IPOS-Integration-Key}; CSRF-exempt.
     */
    @PostMapping("/inbound/applications")
    public ResponseEntity<?> inboundSubmit(@Valid @RequestBody InboundCommercialApplicationRequest request) {
        try {
            CommercialApplication saved = commercialApplicationService.submitFromPu(
                    request.externalReferenceId().trim(),
                    request.payload(),
                    request.callbackUrl());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new InboundCommercialApplicationResponse(saved.getId(), saved.getExternalReferenceId()));
        } catch (DuplicateExternalReferenceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Duplicate external reference",
                            "externalReferenceId", e.getExternalReferenceId()));
        }
    }

    @GetMapping("/applications")
    public List<CommercialApplicationListItemDto> listApplications(
            @RequestParam(required = false) String status) {
        Optional<CommercialApplicationStatus> st = Optional.empty();
        if (status != null && !status.isBlank()) {
            try {
                st = Optional.of(CommercialApplicationStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        }
        return commercialApplicationService.findAll(st).stream()
                .map(a -> new CommercialApplicationListItemDto(
                        a.getId(),
                        a.getExternalReferenceId(),
                        a.getStatus().name(),
                        a.getCreatedAt()))
                .toList();
    }

    @GetMapping("/applications/{id}")
    public CommercialApplicationDetailDto getApplication(@PathVariable Long id) {
        CommercialApplication app = commercialApplicationService.findByIdForDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        return toDetailDto(app);
    }

    @PostMapping("/applications/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveCommercialApplicationRequest body,
            Authentication authentication) {
        String username = requireAdminUsername(authentication);
        Optional<String> override = Optional.ofNullable(body)
                .map(ApproveCommercialApplicationRequest::emailBody)
                .filter(s -> s != null && !s.isBlank());
        try {
            DecisionResult result = commercialApplicationService.approve(id, override, username);
            return ResponseEntity.ok(toDecisionResponse(result));
        } catch (ApplicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InvalidStatusException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/applications/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectCommercialApplicationRequest body,
            Authentication authentication) {
        String username = requireAdminUsername(authentication);
        try {
            DecisionResult result = commercialApplicationService.reject(id, body.reason(), username);
            return ResponseEntity.ok(toDecisionResponse(result));
        } catch (ApplicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InvalidStatusException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String requireAdminUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return authentication.getName();
    }

    private static CommercialApplicationDetailDto toDetailDto(CommercialApplication app) {
        String decidedBy = null;
        if (app.getDecidedBy() != null) {
            decidedBy = app.getDecidedBy().getUsername();
        }
        return new CommercialApplicationDetailDto(
                app.getId(),
                app.getExternalReferenceId(),
                app.getStatus().name(),
                app.getPayloadJson(),
                app.getGeneratedEmailBody(),
                app.getRejectionReason(),
                app.getCreatedAt(),
                app.getDecidedAt(),
                decidedBy,
                app.getCallbackUrl());
    }

    private static CommercialApplicationDecisionResponse toDecisionResponse(DecisionResult result) {
        CommercialApplication app = result.getApplication();
        WebhookOutcome wh = result.getWebhook();
        return new CommercialApplicationDecisionResponse(
                app.getId(),
                app.getStatus().name(),
                app.getGeneratedEmailBody(),
                app.getRejectionReason(),
                wh.getStatus(),
                wh.getErrorDetail());
    }
}

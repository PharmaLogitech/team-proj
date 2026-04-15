package com.ipos.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCommercialApplicationRequest(
        @NotBlank(message = "reason is required") String reason
) {
}

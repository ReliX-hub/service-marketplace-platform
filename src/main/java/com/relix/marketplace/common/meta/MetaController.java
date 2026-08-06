package com.relix.marketplace.common.meta;

import com.relix.marketplace.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
@Tag(name = "API metadata", description = "Public metadata used to render filters, labels, and status badges")
public class MetaController {

    private final EnumMetadataService enumMetadataService;

    @GetMapping("/enums")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Get marketplace frontend metadata",
            description = "Returns declaration-ordered enum values plus stable media workflow error codes. Error codes are exposed separately and are not represented as enum options.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Frontend metadata for marketplace enums and media workflow errors")
    public ResponseEntity<ApiResponse<EnumMetadataResponse>> getEnums() {
        return ResponseEntity.ok(ApiResponse.success(enumMetadataService.getMetadata()));
    }
}

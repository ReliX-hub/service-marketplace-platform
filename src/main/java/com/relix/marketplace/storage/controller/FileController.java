package com.relix.marketplace.storage.controller;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.StoredObject;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Immutable managed image delivery with centralized visibility checks")
public class FileController {

    private static final String PUBLIC_CACHE = "public, max-age=31536000, immutable";
    private static final String PRIVATE_CACHE = "private, no-store";

    private final FileAccessService fileAccessService;
    private final FileStorage fileStorage;

    @GetMapping("/{key:.+}")
    @Operation(summary = "Read a managed image", description = "PUBLIC files allow anonymous reads; PRIVATE files require an authorized owner, participant, or administrator.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Raw normalized image bytes",
                    content = @Content(schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Authentication required for a private file"),
            @ApiResponse(responseCode = "403", description = "Authenticated caller is not authorized"),
            @ApiResponse(responseCode = "404", description = "File is absent or not attached")
    })
    public ResponseEntity<InputStreamResource> read(
            @PathVariable("key") String key,
            Authentication authentication) {
        StoredFile file = fileAccessService.requireReadable(key, authentication);
        StoredObject object = fileStorage.open(file.getStorageKey());
        InputStreamResource body = new InputStreamResource(object.inputStream());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(object.byteSize())
                .header(HttpHeaders.CACHE_CONTROL,
                        file.getVisibility() == FileVisibility.PUBLIC ? PUBLIC_CACHE : PRIVATE_CACHE)
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}

package com.relix.marketplace.storage.controller;

import com.relix.marketplace.common.exception.GlobalExceptionHandler;
import com.relix.marketplace.common.exception.UnauthorizedException;
import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.StoredObject;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    private static final String KEY = "test-image.jpg";
    private static final byte[] BYTES = new byte[]{1, 2, 3};

    @Mock
    private FileAccessService fileAccessService;
    @Mock
    private FileStorage fileStorage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileAccessService, fileStorage))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void servesPublicImageWithImmutableCacheAndSafetyHeaders() throws Exception {
        when(fileAccessService.requireReadable(eq(KEY), isNull())).thenReturn(file(FileVisibility.PUBLIC));
        when(fileStorage.open(KEY)).thenReturn(storedObject());

        mockMvc.perform(get("/api/files/{key}", KEY))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES))
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void servesPrivateImageWithoutSharedCaching() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(12L, null);
        when(fileAccessService.requireReadable(eq(KEY), any())).thenReturn(file(FileVisibility.PRIVATE));
        when(fileStorage.open(KEY)).thenReturn(storedObject());

        mockMvc.perform(get("/api/files/{key}", KEY).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void returnsStructuredUnauthorizedResponseForPrivateAnonymousRead() throws Exception {
        when(fileAccessService.requireReadable(eq(KEY), isNull()))
                .thenThrow(new UnauthorizedException("Authentication is required to access this file"));

        mockMvc.perform(get("/api/files/{key}", KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private StoredFile file(FileVisibility visibility) {
        return StoredFile.builder()
                .storageKey(KEY)
                .variant(FileVariant.LARGE)
                .visibility(visibility)
                .ownerType(FileOwnerType.TICKET_IMAGE)
                .ownerId(42L)
                .contentType("image/jpeg")
                .byteSize(3L)
                .width(1)
                .height(1)
                .build();
    }

    private StoredObject storedObject() {
        return new StoredObject(new ByteArrayInputStream(BYTES), BYTES.length);
    }
}

package com.relix.marketplace.storage;

import com.relix.marketplace.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesOpensAndDeletesImmutableObjects() throws IOException {
        LocalFileStorage storage = storageAt(temporaryDirectory.resolve("objects"));
        byte[] expected = "normalized-image".getBytes(StandardCharsets.UTF_8);

        storage.store(new StoredObjectRequest("550e8400-e29b-41d4-a716-446655440000.jpg", expected));

        try (StoredObject stored = storage.open("550e8400-e29b-41d4-a716-446655440000.jpg")) {
            assertThat(stored.byteSize()).isEqualTo(expected.length);
            assertThat(stored.inputStream().readAllBytes()).isEqualTo(expected);
        }

        storage.delete("550e8400-e29b-41d4-a716-446655440000.jpg");
        storage.delete("550e8400-e29b-41d4-a716-446655440000.jpg");

        assertThatThrownBy(() -> storage.open("550e8400-e29b-41d4-a716-446655440000.jpg"))
                .isInstanceOfSatisfying(StorageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORAGE_OBJECT_NOT_FOUND"));
    }

    @Test
    void neverOverwritesAnExistingObject() throws IOException {
        LocalFileStorage storage = storageAt(temporaryDirectory.resolve("objects"));
        String key = "6ba7b810-9dad-11d1-80b4-00c04fd430c8.png";
        byte[] original = "first".getBytes(StandardCharsets.UTF_8);

        storage.store(new StoredObjectRequest(key, original));

        assertThatThrownBy(() -> storage.store(new StoredObjectRequest(
                key,
                "second".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(StorageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORAGE_OBJECT_EXISTS"));

        try (StoredObject stored = storage.open(key)) {
            assertThat(stored.inputStream().readAllBytes()).isEqualTo(original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../outside",
            "..\\outside",
            "/absolute",
            "C:\\outside",
            "nested/file",
            ".hidden",
            "name.webp",
            "name.jpg.exe"
    })
    void rejectsTraversalAndNonCanonicalKeys(String unsafeKey) {
        LocalFileStorage storage = storageAt(temporaryDirectory.resolve("objects"));

        assertThatThrownBy(() -> storage.store(new StoredObjectRequest(unsafeKey, new byte[]{1})))
                .isInstanceOfSatisfying(StorageException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STORAGE_KEY_INVALID"));
    }

    @Test
    void requestDefensivelyCopiesContent() throws IOException {
        LocalFileStorage storage = storageAt(temporaryDirectory.resolve("objects"));
        byte[] mutable = new byte[]{1, 2, 3};
        StoredObjectRequest request = new StoredObjectRequest("550e8400-e29b-41d4-a716-446655440000", mutable);
        mutable[0] = 99;

        storage.store(request);

        try (StoredObject stored = storage.open(request.storageKey())) {
            assertThat(stored.inputStream().readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    private LocalFileStorage storageAt(Path root) {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRoot(root.toString());
        return new LocalFileStorage(properties);
    }
}

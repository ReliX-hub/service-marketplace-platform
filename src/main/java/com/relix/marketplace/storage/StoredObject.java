package com.relix.marketplace.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record StoredObject(InputStream inputStream, long byteSize) implements AutoCloseable {

    public StoredObject {
        Objects.requireNonNull(inputStream, "inputStream");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must not be negative");
        }
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}

package com.relix.marketplace.storage;

public interface FileStorage {

    void store(StoredObjectRequest request);

    StoredObject open(String storageKey);

    void delete(String storageKey);
}

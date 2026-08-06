package com.relix.marketplace.storage;

import com.relix.marketplace.storage.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private static final int MAX_KEY_LENGTH = 180;
    private static final Pattern SAFE_KEY = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_-]*(?:\\.(?:jpg|jpeg|png))?");
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = initializeRoot(properties.getLocal().getRoot());
    }

    @Override
    public void store(StoredObjectRequest request) {
        Path target = resolveKey(request.storageKey());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageException(
                    "A stored object already exists for the supplied key",
                    "STORAGE_OBJECT_EXISTS");
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            Files.write(
                    temporary,
                    request.content(),
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            restrictPermissions(temporary);
            moveWithoutReplacement(temporary, target);
            temporary = null;
            restrictPermissions(target);
        } catch (FileAlreadyExistsException e) {
            throw new StorageException(
                    "A stored object already exists for the supplied key",
                    "STORAGE_OBJECT_EXISTS",
                    e);
        } catch (IOException e) {
            throw new StorageException("Unable to store object", "STORAGE_WRITE_FAILED", e);
        } finally {
            deleteTemporaryQuietly(temporary);
        }
    }

    @Override
    public StoredObject open(String storageKey) {
        Path target = resolveKey(storageKey);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageException("Stored object was not found", "STORAGE_OBJECT_NOT_FOUND");
        }

        try {
            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            SeekableByteChannel channel = Files.newByteChannel(target, options);
            return new StoredObject(Channels.newInputStream(channel), channel.size());
        } catch (NoSuchFileException e) {
            throw new StorageException("Stored object was not found", "STORAGE_OBJECT_NOT_FOUND", e);
        } catch (IOException e) {
            throw new StorageException("Unable to open stored object", "STORAGE_READ_FAILED", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveKey(storageKey);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageException("Stored object is not a regular file", "STORAGE_OBJECT_UNSAFE");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Unable to delete stored object", "STORAGE_DELETE_FAILED", e);
        }
    }

    Path root() {
        return root;
    }

    private Path initializeRoot(String configuredRoot) {
        try {
            Path requested = Path.of(configuredRoot).toAbsolutePath().normalize();
            Files.createDirectories(requested);
            Path canonicalRoot = requested.toRealPath();
            if (!Files.isDirectory(canonicalRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new StorageException(
                        "Configured storage root is not a directory",
                        "STORAGE_ROOT_UNAVAILABLE");
            }

            Path probe = Files.createTempFile(canonicalRoot, ".write-probe-", ".tmp");
            Files.delete(probe);
            return canonicalRoot;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(
                    "Configured storage root is not writable",
                    "STORAGE_ROOT_UNAVAILABLE",
                    e);
        }
    }

    private Path resolveKey(String storageKey) {
        if (storageKey == null
                || storageKey.length() > MAX_KEY_LENGTH
                || !SAFE_KEY.matcher(storageKey).matches()) {
            throw new StorageException("Invalid storage key", "STORAGE_KEY_INVALID");
        }

        Path resolved = root.resolve(storageKey).normalize();
        if (!root.equals(resolved.getParent())) {
            throw new StorageException("Invalid storage key", "STORAGE_KEY_INVALID");
        }
        return resolved;
    }

    private void moveWithoutReplacement(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void restrictPermissions(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS);
        }
    }

    private void deleteTemporaryQuietly(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The storage layer never hides the original failure with cleanup noise.
        }
    }
}

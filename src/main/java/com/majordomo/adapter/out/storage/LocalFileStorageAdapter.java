package com.majordomo.adapter.out.storage;

import com.majordomo.domain.port.out.FileStoragePort;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem implementation of {@link FileStoragePort}.
 * Stores files under a configurable base directory. Protected by
 * Resilience4j circuit breaker and retry for I/O resilience.
 */
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final Path baseDir;

    /**
     * Constructs the adapter with the configured base directory.
     *
     * @param baseDir the base directory for file storage
     */
    public LocalFileStorageAdapter(
            @Value("${majordomo.storage.base-dir:./data/attachments}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    @Override
    @CircuitBreaker(name = "fileStorage", fallbackMethod = "storeFallback")
    @Retry(name = "fileStorage")
    public String store(String path, InputStream content) {
        try {
            Path target = baseDir.resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Stored file at {}", target);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + path, e);
        }
    }

    @Override
    @CircuitBreaker(name = "fileStorage")
    @Retry(name = "fileStorage")
    public InputStream load(String path) {
        try {
            Path target = baseDir.resolve(path);
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load file: " + path, e);
        }
    }

    @Override
    @CircuitBreaker(name = "fileStorage")
    @Retry(name = "fileStorage")
    public void delete(String path) {
        try {
            Path target = baseDir.resolve(path);
            Files.deleteIfExists(target);
            LOG.info("Deleted file at {}", target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + path, e);
        }
    }

    /**
     * Fallback when file storage fails due to circuit breaker.
     *
     * @param path    the storage path
     * @param content the file content
     * @param ex      the cause of failure
     * @return never returns normally
     */
    @SuppressWarnings("unused")
    String storeFallback(String path, InputStream content, Exception ex) {
        LOG.error("File storage circuit breaker open for path={}: {}", path, ex.getMessage());
        throw new UncheckedIOException("File storage unavailable",
                new IOException("Circuit breaker open", ex));
    }
}

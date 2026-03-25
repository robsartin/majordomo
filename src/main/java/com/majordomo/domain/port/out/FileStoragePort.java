package com.majordomo.domain.port.out;

import java.io.InputStream;

/**
 * Outbound port for file storage operations. Abstracts the storage backend
 * (local filesystem, S3, etc.) so the domain remains decoupled from
 * infrastructure concerns.
 */
public interface FileStoragePort {

    /**
     * Stores a file and returns the storage path.
     *
     * @param path    the relative storage path
     * @param content the file content
     * @return the storage path that was written
     */
    String store(String path, InputStream content);

    /**
     * Loads a file by its storage path.
     *
     * @param path the storage path
     * @return the file content as an input stream
     */
    InputStream load(String path);

    /**
     * Deletes a file by its storage path.
     *
     * @param path the storage path
     */
    void delete(String path);
}

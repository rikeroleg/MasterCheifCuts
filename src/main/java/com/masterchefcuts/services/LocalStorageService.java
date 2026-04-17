package com.masterchefcuts.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-dev implementation of StorageService.
 * Files are written to {java.io.tmpdir}/masterchefcuts-uploads/ and a
 * file:// URI is returned.  This lets photo upload work end-to-end in dev
 * without any cloud credentials.  Switch to the "gcp" profile in
 * staging/production to use real object storage.
 */
@Service
@Profile("local")
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path uploadRoot = Paths.get(System.getProperty("java.io.tmpdir"), "masterchefcuts-uploads");

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        try {
            Path target = uploadRoot.resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("[LocalStorageService] Saved upload to {}", target);
            return target.toUri().toString();
        } catch (IOException e) {
            throw new RuntimeException("LocalStorageService upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(uploadRoot.resolve(key));
        } catch (IOException e) {
            log.warn("[LocalStorageService] Could not delete {}: {}", key, e.getMessage());
        }
    }
}

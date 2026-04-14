package com.masterchefcuts.services;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Handles listing photo uploads to Google Cloud Storage.
 * Active when the "gcp" profile is enabled.
 */
@Service
@Profile("gcp")
public class GcsStorageService implements StorageService {

    private final Storage storage;
    private final String bucketName;

    public GcsStorageService(
            @Value("${gcs.bucket}") String bucketName,
            @Value("${gcp.project-id:}") String projectId) {
        this.bucketName = bucketName;

        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        this.storage = builder.build().getService();
    }

    @Override
    public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, key))
                .setContentType(contentType)
                .build();

        try {
            storage.create(blobInfo, inputStream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("GCS upload failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("GCS upload failed", e);
        }

        return "https://storage.googleapis.com/" + bucketName + "/" + key;
    }

    @Override
    public void delete(String key) {
        storage.delete(BlobId.of(bucketName, key));
    }
}
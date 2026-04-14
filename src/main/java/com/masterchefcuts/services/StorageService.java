package com.masterchefcuts.services;

import java.io.InputStream;

/**
 * Provider-agnostic object storage contract used by listing photo flows.
 */
public interface StorageService {

    String upload(String key, InputStream inputStream, long contentLength, String contentType);

    void delete(String key);
}
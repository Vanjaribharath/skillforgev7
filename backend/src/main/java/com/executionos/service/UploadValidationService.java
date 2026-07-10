package com.executionos.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadValidationService {
    private final long maxBytes;
    private final Set<String> allowedTypes;
    private final boolean virusScanEnabled;

    public UploadValidationService(
            @Value("${executionos.uploads.max-file-size-bytes}") long maxBytes,
            @Value("${executionos.uploads.allowed-content-types}") String allowedContentTypes,
            @Value("${executionos.uploads.virus-scan-enabled}") boolean virusScanEnabled) {
        this.maxBytes = maxBytes;
        this.allowedTypes = Arrays.stream(allowedContentTypes.split(",")).map(String::trim).collect(Collectors.toSet());
        this.virusScanEnabled = virusScanEnabled;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File exceeds upload limit");
        }
        if (!allowedTypes.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type is not allowed");
        }
        if (virusScanEnabled) {
            // Integration point for ClamAV, S3 Object Lambda, or provider-native malware scanning.
        }
    }
}

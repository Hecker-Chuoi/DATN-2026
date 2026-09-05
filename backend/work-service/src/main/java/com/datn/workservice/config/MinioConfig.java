package com.datn.workservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadminpassword}")
    private String secretKey;

    @Value("${minio.bucket-name:work-attachments}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client connecting to endpoint: {}", endpoint);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // Auto-create bucket if missing
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                log.info("MinIO bucket '{}' does not exist. Creating automatically...", bucketName);
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Successfully created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket '{}' is ready.", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket auto-check skipped/failed (will retry during runtime upload): {}", e.getMessage());
        }

        return client;
    }
}

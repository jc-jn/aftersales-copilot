package com.aftersales.copilot.common.storage;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class PrivateObjectStorage {
    private final MinioClient minio;
    private final String bucket;

    public PrivateObjectStorage(@Value("${app.storage.endpoint}") String endpoint,
                                @Value("${app.storage.access-key}") String access,
                                @Value("${app.storage.secret-key}") String secret,
                                @Value("${app.storage.bucket}") String bucket) {
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
        this.bucket = bucket;
    }

    public String put(String prefix, byte[] bytes, String type) {
        String key = prefix + "/" + UUID.randomUUID();
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            // A pre-existing bucket must also remain private.
            minio.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType(type).build());
            return key;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", e);
        }
    }

    public String download(String key, String filename) {
        try {
            minio.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
            String disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(bucket).object(key).expiry(300)
                    .extraQueryParams(Map.of("response-content-disposition", disposition,
                            "response-content-type", "application/octet-stream")).build());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", e);
        }
    }

    public void remove(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", e);
        }
    }
}

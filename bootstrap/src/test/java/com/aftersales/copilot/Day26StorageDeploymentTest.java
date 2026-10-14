package com.aftersales.copilot;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.common.storage.PrivateObjectStorage;
import com.aftersales.copilot.knowledge.application.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Day26StorageDeploymentTest {
    @Test
    void minioAcceptsBothSignedHostsAndRejectsAnUnsignedDownload() throws Exception {
        try (var minio = new GenericContainer<>("quay.io/minio/minio:RELEASE.2024-12-18T13-15-44Z")
                .withEnv("MINIO_ROOT_USER", "deployment-test")
                .withEnv("MINIO_ROOT_PASSWORD", "deployment-test-secret")
                .withCommand("server", "/data")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000))) {
            minio.start();
            String internal = "http://127.0.0.1:" + minio.getMappedPort(9000);
            String external = "http://localhost:" + minio.getMappedPort(9000);
            var storage = new PrivateObjectStorage(internal, external, "deployment-test",
                    "deployment-test-secret", "deployment-test-private");
            byte[] content = "# Deployment document\n".getBytes(StandardCharsets.UTF_8);
            String key = storage.put("knowledge", content, "text/markdown");
            String publicUrl = storage.download(key, "policy.md");
            String internalUrl = storage.internalDownload(key, "policy.md");
            assertThat(URI.create(publicUrl).getHost()).isEqualTo("localhost");
            assertThat(URI.create(internalUrl).getHost()).isEqualTo("127.0.0.1");
            var client = HttpClient.newHttpClient();
            for (String url : List.of(publicUrl, internalUrl)) {
                var response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(content);
                assertThat(response.headers().firstValue("content-disposition").orElse("")).contains("attachment");
            }
            var unsigned = client.send(HttpRequest.newBuilder(URI.create(publicUrl.split("\\?")[0])).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(unsigned.statusCode()).isEqualTo(403);
            // Signing must not try to connect to the browser-only host for region discovery.
            var browserOnly = new PrivateObjectStorage(internal, "http://browser-only.invalid:9000",
                    "deployment-test", "deployment-test-secret", "deployment-test-private");
            assertThat(browserOnly.download(key, "policy.md")).startsWith("http://browser-only.invalid:9000/");
        }
    }

    @Test
    void knowledgeDownloadsUseInternalHostOnlyForIndexerAndKeepAdminGuard() {
        var jdbc = mock(JdbcTemplate.class);
        var storage = mock(PrivateObjectStorage.class);
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of("object_key", "knowledge/key", "file_name", "policy.md")));
        when(storage.download("knowledge/key", "policy.md")).thenReturn("http://localhost:9000/public-signature");
        when(storage.internalDownload("knowledge/key", "policy.md")).thenReturn("http://minio:9000/internal-signature");
        var service = new KnowledgeDocumentService(jdbc, mock(SnowflakeIdGenerator.class), new ObjectMapper(), storage);
        var admin = new AuthenticatedUser(1L, "admin", null, "Admin", UserRole.ADMIN);
        var customer = new AuthenticatedUser(2L, "customer", null, "Customer", UserRole.CUSTOMER);
        assertThat(service.download(admin, 1).get("url")).isEqualTo("http://localhost:9000/public-signature");
        assertThat(service.indexingDownload(1).get("url")).isEqualTo("http://minio:9000/internal-signature");
        clearInvocations(storage, jdbc);
        assertThatThrownBy(() -> service.download(customer, 1)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage, jdbc);
    }
}

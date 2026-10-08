package com.aftersales.copilot.knowledge.application;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.common.storage.FileValidator;
import com.aftersales.copilot.common.storage.PrivateObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {
    private final JdbcTemplate jdbc;
    private final SnowflakeIdGenerator ids;
    private final PrivateObjectStorage storage;
    private final ObjectMapper mapper;

    public KnowledgeDocumentService(JdbcTemplate jdbc, SnowflakeIdGenerator ids, ObjectMapper mapper, PrivateObjectStorage storage) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.mapper = mapper;
        this.storage = storage;
    }

    @Transactional
    public Map<String, Object> upload(AuthenticatedUser user, MultipartFile file, String title,
                                      String documentType, String scopeType, Long scopeId, String versionLabel) {
        requireAdmin(user);
        if (title == null || title.isBlank() || title.length() > 200 || versionLabel == null
                || versionLabel.isBlank() || versionLabel.length() > 40
                || !Set.of("POLICY", "FAQ", "MANUAL").contains(documentType)
                || !Set.of("GLOBAL", "PRODUCT", "SKU", "CATEGORY").contains(scopeType)
                || (scopeType.equals("GLOBAL") ? scopeId != null : scopeId == null || scopeId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCUMENT_METADATA_INVALID");
        }
        if (file.getSize() > 20L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_INVALID");
        String key = null;
        try {
            byte[] bytes = file.getBytes();
            String type = FileValidator.validate(file.getOriginalFilename(), file.getContentType(), bytes, true);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            long documentId = ids.nextId(), taskId = ids.nextId();
            key = storage.put("knowledge", bytes, type);
            registerRollbackCleanup(storage, key);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            jdbc.update("INSERT INTO knowledge_base(id,name,code,status,created_at,updated_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)", 1, "Default Knowledge Base", "default", "ACTIVE", now, now);
            jdbc.update("INSERT INTO knowledge_document(id,knowledge_base_id,title,document_type,scope_type,scope_id,object_key,file_name,content_type,size_bytes,sha256,version_label,status,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    documentId, 1, title, documentType, scopeType, scopeId, key, file.getOriginalFilename(), type, bytes.length, sha, versionLabel, "UPLOADED", user.id(), now, now);
            Map<String, Object> data = Map.of("taskId", taskId, "documentId", documentId, "indexVersion", 1,
                    "fileName", file.getOriginalFilename(), "contentType", type, "contentSha256", sha,
                    "metadata", Map.of("documentType", documentType, "scopeType", scopeType, "scopeId", scopeId == null ? "" : scopeId));
            Map<String, Object> envelope = Map.of("eventId", UUID.randomUUID().toString(), "eventType", "knowledge.document.index.requested.v1",
                    "occurredAt", now.toString(), "traceId", UUID.randomUUID().toString(), "producer", "aftersales-server", "schemaVersion", 1, "data", data);
            jdbc.update("INSERT INTO ai_task(id,biz_type,biz_id,dedup_key,status,attempt_count,max_attempts,request_snapshot,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    taskId, "DOCUMENT_INDEX", documentId, "DOCUMENT_INDEX:" + documentId + ":1", "PENDING", 0, 3, mapper.writeValueAsString(data), now, now);
            jdbc.update("INSERT INTO outbox_event(event_id,aggregate_type,aggregate_id,event_type,routing_key,payload,status,retry_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,0,?,?)",
                    envelope.get("eventId"), "AI_TASK", taskId, envelope.get("eventType"), envelope.get("eventType"), mapper.writeValueAsString(envelope), "NEW", now, now);
            return Map.of("documentId", documentId, "taskId", taskId, "status", "UPLOADED", "sha256", sha);
        } catch (RuntimeException e) {
            if (key != null && !TransactionSynchronizationManager.isSynchronizationActive()) storage.remove(key);
            if (e instanceof DuplicateKeyException) throw new ResponseStatusException(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_EXISTS", e);
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("DOCUMENT_UPLOAD_FAILED", e);
        }
    }

    public Map<String, Object> download(AuthenticatedUser user, long id) {
        requireAdmin(user);
        return indexingDownload(id);
    }

    public Map<String, Object> indexingDownload(long id) {
        var document = jdbc.queryForList("SELECT object_key,file_name FROM knowledge_document WHERE id=? AND archived_at IS NULL", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND"));
        return Map.of("url", storage.download((String) document.get("object_key"), (String) document.get("file_name")), "expiresIn", 300);
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || user.role() != UserRole.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED");
    }

    private static void registerRollbackCleanup(PrivateObjectStorage storage, String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) storage.remove(key);
                }
            });
        }
    }
}

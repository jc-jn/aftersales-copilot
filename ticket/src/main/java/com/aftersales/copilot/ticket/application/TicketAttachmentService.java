package com.aftersales.copilot.ticket.application;

import com.aftersales.copilot.auth.application.TicketAccessGuard;
import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.common.storage.FileValidator;
import com.aftersales.copilot.common.storage.PrivateObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class TicketAttachmentService {
    private final JdbcTemplate jdbc;
    private final TicketAccessGuard access;
    private final PrivateObjectStorage storage;
    private final SnowflakeIdGenerator ids;

    public TicketAttachmentService(JdbcTemplate jdbc, TicketAccessGuard access, PrivateObjectStorage storage, SnowflakeIdGenerator ids) {
        this.jdbc = jdbc;
        this.access = access;
        this.storage = storage;
        this.ids = ids;
    }

    @Transactional
    public Map<String, Object> upload(AuthenticatedUser user, long ticketId, MultipartFile file) {
        access.requireAccess(user, ticketId);
        if (file.getSize() > 10L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_INVALID");
        try {
            byte[] bytes = file.getBytes();
            String type = FileValidator.validate(file.getOriginalFilename(), file.getContentType(), bytes, false);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String key = storage.put("attachments", bytes, type);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) storage.remove(key); }
                });
            }
            long id = ids.nextId();
            jdbc.update("INSERT INTO ticket_attachment(id,ticket_id,uploaded_by,object_key,file_name,content_type,size_bytes,sha256,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, ticketId, user.id(), key, file.getOriginalFilename(), type, bytes.length, sha, LocalDateTime.now(ZoneOffset.UTC));
            return Map.of("id", Long.toString(id), "fileName", file.getOriginalFilename(), "contentType", type, "sizeBytes", bytes.length);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", e);
        }
    }

    public List<Map<String, Object>> list(AuthenticatedUser user, long ticketId) {
        access.requireAccess(user, ticketId);
        return jdbc.queryForList("SELECT CAST(id AS CHAR) id,file_name fileName,content_type contentType,size_bytes sizeBytes,created_at createdAt FROM ticket_attachment WHERE ticket_id=? ORDER BY created_at DESC LIMIT 100", ticketId);
    }

    public Map<String, Object> download(AuthenticatedUser user, long ticketId, long attachmentId) {
        access.requireAccess(user, ticketId);
        var attachment = jdbc.queryForList("SELECT object_key,file_name FROM ticket_attachment WHERE id=? AND ticket_id=?", attachmentId, ticketId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"));
        return Map.of("url", storage.download((String) attachment.get("object_key"), (String) attachment.get("file_name")), "expiresIn", 300);
    }
}

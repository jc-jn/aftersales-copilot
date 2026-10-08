package com.aftersales.copilot.common.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

public final class FileValidator {
    private static final Map<String, String> TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "pdf", "application/pdf", "txt", "text/plain", "md", "text/markdown",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private FileValidator() {}

    public static String validate(String name, String declaredType, byte[] bytes, boolean knowledge) {
        long max = (knowledge ? 20L : 10L) * 1024 * 1024;
        if (bytes.length == 0 || bytes.length > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_INVALID");
        }
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/")
                || name.contains("\\") || name.contains(":") || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null || (knowledge ? type.startsWith("image/") : !(type.startsWith("image/") || extension.equals("pdf")))) {
            throw invalid();
        }
        String mime = declaredType == null ? "" : declaredType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mime.equals(type) && !(extension.equals("md") && mime.equals("text/plain"))) throw invalid();
        boolean valid = switch (extension) {
            case "pdf" -> starts(bytes, new byte[]{'%', 'P', 'D', 'F', '-'});
            case "png" -> starts(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "jpg", "jpeg" -> starts(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255});
            case "txt", "md" -> validText(bytes);
            case "docx" -> validDocx(bytes);
            default -> false;
        };
        if (!valid) throw invalid();
        return type;
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static boolean validText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validDocx(byte[] bytes) {
        boolean contentTypes = false, document = false;
        long expanded = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (++entries > 2000 || name.startsWith("/") || name.contains("..") || name.contains("\\")
                        || name.toLowerCase(Locale.ROOT).contains("vbaproject")) return false;
                contentTypes |= name.equals("[Content_Types].xml");
                document |= name.equals("word/document.xml");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 40L * 1024 * 1024) return false;
                }
            }
            return contentTypes && document;
        } catch (Exception e) {
            return false;
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "FILE_FORMAT_INVALID");
    }
}

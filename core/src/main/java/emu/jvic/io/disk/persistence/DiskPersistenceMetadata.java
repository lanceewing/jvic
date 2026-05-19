package emu.jvic.io.disk.persistence;

import emu.jvic.config.AppConfigItem;

/**
 * Diagnostic metadata stored next to a persisted disk image.
 */
public class DiskPersistenceMetadata {

    private final int schemaVersion = 1;
    private final String programKey;
    private final String programIdSource;
    private final String gameId;
    private final String name;
    private final String filePath;
    private final String entryName;
    private final String fileType;
    private final String machineType;
    private final String originalDiskHash;
    private final int originalDiskSize;
    private String persistedDiskHash;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private long persistenceActivatedAtEpochMs;

    public DiskPersistenceMetadata(AppConfigItem appConfigItem, DiskPersistenceKey key,
            String programIdSource, int originalDiskSize) {
        this.programKey = key.getProgramKey();
        this.programIdSource = programIdSource;
        this.gameId = appConfigItem.getGameId();
        this.name = appConfigItem.getName();
        this.filePath = appConfigItem.getFilePath();
        this.entryName = appConfigItem.getEntryName();
        this.fileType = appConfigItem.getFileType();
        this.machineType = appConfigItem.getMachineType();
        this.originalDiskHash = key.getOriginalDiskHash();
        this.originalDiskSize = originalDiskSize;
    }

    public void setPersistedDiskHash(String persistedDiskHash) {
        this.persistedDiskHash = persistedDiskHash;
    }

    public void setCreatedAtEpochMs(long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public void setPersistenceActivatedAtEpochMs(long persistenceActivatedAtEpochMs) {
        this.persistenceActivatedAtEpochMs = persistenceActivatedAtEpochMs;
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        appendNumber(json, "schemaVersion", schemaVersion);
        appendString(json, "programKey", programKey);
        appendString(json, "programIdSource", programIdSource);
        appendString(json, "gameId", gameId);
        appendString(json, "name", name);
        appendString(json, "filePath", filePath);
        appendString(json, "entryName", entryName);
        appendString(json, "fileType", fileType);
        appendString(json, "machineType", machineType);
        appendString(json, "originalDiskHash", originalDiskHash);
        appendString(json, "persistedDiskHash", persistedDiskHash);
        appendNumber(json, "originalDiskSize", originalDiskSize);
        appendNumber(json, "createdAtEpochMs", createdAtEpochMs);
        appendNumber(json, "updatedAtEpochMs", updatedAtEpochMs);
        appendNumber(json, "persistenceActivatedAtEpochMs", persistenceActivatedAtEpochMs);
        json.append('}');
        return json.toString();
    }

    private void appendString(StringBuilder json, String name, String value) {
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(name).append('"').append(':');
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(escapeJson(value)).append('"');
        }
    }

    private void appendNumber(StringBuilder json, String name, long value) {
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(name).append('"').append(':').append(value);
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(ch);
                    break;
            }
        }
        return escaped.toString();
    }
}
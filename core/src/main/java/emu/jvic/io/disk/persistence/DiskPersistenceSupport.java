package emu.jvic.io.disk.persistence;

import emu.jvic.config.AppConfigItem;

/**
 * Shared helpers for browser disk-image persistence key generation.
 */
public final class DiskPersistenceSupport {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private DiskPersistenceSupport() {
    }

    public static DiskPersistenceKey createKey(AppConfigItem appConfigItem, byte[] originalDiskImage) {
        return createKey(appConfigItem, originalDiskImage, null);
    }

    public static DiskPersistenceKey createKey(AppConfigItem appConfigItem,
            byte[] originalDiskImage, String normalizedPathOverride) {
        String programIdSource = getProgramIdSource(appConfigItem, normalizedPathOverride);
        String readablePrefix = buildReadablePrefix(appConfigItem, programIdSource,
                normalizedPathOverride);
        String identityHash = stableHashHex(buildIdentityString(appConfigItem,
                normalizedPathOverride));
        String originalDiskHash = stableHashHex(originalDiskImage);
        return new DiskPersistenceKey(readablePrefix + "--" + identityHash, originalDiskHash);
    }

    public static String getProgramIdSource(AppConfigItem appConfigItem) {
        return getProgramIdSource(appConfigItem, null);
    }

    public static String getProgramIdSource(AppConfigItem appConfigItem,
            String normalizedPathOverride) {
        if (!isBlank(appConfigItem.getGameId())) {
            return "gameId";
        }
        if (!isBlank(normalizedPathOverride)) {
            return "filePath";
        }
        if (!isBlank(appConfigItem.getFilePath())) {
            return "filePath";
        }
        if (!isBlank(appConfigItem.getName())) {
            return "name";
        }
        return "program";
    }

    public static String stableHashHex(byte[] data) {
        long hash = FNV_OFFSET_BASIS;
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                hash ^= (data[i] & 0xff);
                hash *= FNV_PRIME;
            }
        }
        return toPaddedHex(hash);
    }

    public static String stableHashHex(String value) {
        long hash = FNV_OFFSET_BASIS;
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= FNV_PRIME;
            }
        }
        return toPaddedHex(hash);
    }

    private static String buildReadablePrefix(AppConfigItem appConfigItem,
            String programIdSource, String normalizedPathOverride) {
        if ("gameId".equals(programIdSource)) {
            return "gid-" + slugify(appConfigItem.getGameId());
        }
        if ("filePath".equals(programIdSource)) {
            return "path-" + slugify(!isBlank(normalizedPathOverride)
                    ? normalizedPathOverride
                    : appConfigItem.getFilePath());
        }
        if ("name".equals(programIdSource)) {
            return "name-" + slugify(appConfigItem.getName());
        }
        return "program";
    }

    private static String buildIdentityString(AppConfigItem appConfigItem,
            String normalizedPathOverride) {
        StringBuilder value = new StringBuilder(128);
        appendPart(value, appConfigItem.getGameId());
        appendPart(value, !isBlank(normalizedPathOverride)
                ? normalizedPathOverride
                : appConfigItem.getFilePath());
        appendPart(value, appConfigItem.getEntryName());
        appendPart(value, appConfigItem.getFileType());
        return value.toString();
    }

    private static void appendPart(StringBuilder value, String part) {
        if (value.length() > 0) {
            value.append('|');
        }
        value.append(part != null ? part : "");
    }

    private static String slugify(String value) {
        if (isBlank(value)) {
            return "program";
        }

        String lowerValue = value.trim().toLowerCase();
        StringBuilder slug = new StringBuilder(lowerValue.length());
        boolean previousDash = false;
        for (int i = 0; i < lowerValue.length(); i++) {
            char ch = lowerValue.charAt(i);
            if (((ch >= 'a') && (ch <= 'z')) || ((ch >= '0') && (ch <= '9'))) {
                slug.append(ch);
                previousDash = false;
            } else if (!previousDash) {
                slug.append('-');
                previousDash = true;
            }
        }

        int length = slug.length();
        while ((length > 0) && (slug.charAt(length - 1) == '-')) {
            slug.deleteCharAt(length - 1);
            length--;
        }

        return slug.length() > 0 ? slug.toString() : "program";
    }

    private static boolean isBlank(String value) {
        return (value == null) || value.trim().isEmpty();
    }

    private static String toPaddedHex(long value) {
        String hex = Long.toHexString(value);
        StringBuilder padded = new StringBuilder(16);
        for (int i = hex.length(); i < 16; i++) {
            padded.append('0');
        }
        padded.append(hex);
        return padded.toString();
    }
}
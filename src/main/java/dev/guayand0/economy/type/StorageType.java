package dev.guayand0.economy.type;

import java.util.Locale;

public enum StorageType {
    YAML,
    JSON,
    SQLITE,
    MYSQL;

    public static StorageType fromConfig(String rawType) {
        try {
            return StorageType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid storage type: " + rawType, exception);
        }
    }
}

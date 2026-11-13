package org.example.serialization;

import java.util.List;
import java.util.Locale;

/** Реестр доступных форматов. GUI строит по нему фильтры FileChooser. */
public final class Serializers {

    private static final List<Serializer<CatalogData>> ALL = List.of(
            new JsonCatalogSerializer(),
            new BinaryCatalogSerializer());

    private Serializers() {
    }

    public static List<Serializer<CatalogData>> all() {
        return ALL;
    }

    /** Формат по расширению без точки ("json", "BIN"); null, если такого формата нет. */
    public static Serializer<CatalogData> byExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        for (Serializer<CatalogData> s : ALL) {
            if (s.extension().equals(ext)) {
                return s;
            }
        }
        return null;
    }

    /** Формат по имени файла; null, если расширение неизвестно. */
    public static Serializer<CatalogData> byFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : byExtension(fileName.substring(dot + 1));
    }
}

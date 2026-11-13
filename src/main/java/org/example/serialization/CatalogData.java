package org.example.serialization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"schema_version", "products"})   // версия — первой, чтобы её было видно в начале файла
public record CatalogData(@JsonProperty("schema_version") int schemaVersion, List<ProductRecord> products) {
    // Константы с версиями
    public static final int CURRENT_VERSION = 1;         // Текущая версия программы
    public static final int MIN_SUPPORTED_VERSION = 1;   // Самая старая версия, которую мы умеем читать

    public CatalogData {
        products = products == null ? List.of() : List.copyOf(products);
    }

    /** Данные текущей версии формата. */
    public static CatalogData current(List<ProductRecord> products) {
        return new CatalogData(CURRENT_VERSION, products);
    }
}

package org.example.serialization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Всё содержимое файла: версия схемы и список товаров.
 * Именно этот объект сохраняют и читают все реализации Serializer, поэтому GUI и тесты
 * не знают, что лежит внутри файла — JSON или байты.
 *
 * Структура B-дерева в файл не попадает: сохраняются только товары (в порядке возрастания артикула),
 * а при загрузке дерево строится заново. Так формат не зависит от внутреннего устройства дерева.
 */
@JsonPropertyOrder({"schema_version", "products"})   // версия — первой, чтобы её было видно в начале файла
public record CatalogData(
        @JsonProperty("schema_version") int schemaVersion,
        List<ProductRecord> products
) {
    /** Версия формата, которую пишет это приложение. */
    public static final int CURRENT_VERSION = 1;

    /** Самая старая версия, которую приложение умеет читать. */
    public static final int MIN_SUPPORTED_VERSION = 1;

    public CatalogData {
        products = products == null ? List.of() : List.copyOf(products);
    }

    /** Данные текущей версии формата. */
    public static CatalogData current(List<ProductRecord> products) {
        return new CatalogData(CURRENT_VERSION, products);
    }
}

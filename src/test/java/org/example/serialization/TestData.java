package org.example.serialization;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Общие данные для тестов: эталонный каталог и доступ к файлам-фикстурам. */
final class TestData {

    private TestData() {
    }

    /** Те же три товара, что лежат в fixtures/catalog-v1.json и fixtures/catalog-v1.bin. */
    static CatalogData sample() {
        return CatalogData.current(List.of(
                new ProductRecord("BASE", 1001, "Стол письменный", "Мебель", 8999.0, 12, null, null),
                new ProductRecord("WARRANTY", 1002, "Ноутбук", "Электроника", 79999.99, 15, 24, null),
                new ProductRecord("DISCONTINUED", 1003, "Плеер", "Электроника", 2999.0, 0, null, "2024-06-01")));
    }

    static Path fixture(String name) {
        try {
            return Path.of(TestData.class.getResource("/fixtures/" + name).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Нет фикстуры " + name, e);
        }
    }

    /** Оба формата — для параметризованных тестов. */
    static Stream<Serializer<CatalogData>> serializers() {
        return Serializers.all().stream();
    }
}

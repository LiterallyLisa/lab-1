package org.example.serialization;

import org.example.model.DiscontinuedProduct;
import org.example.model.Product;
import org.example.model.WarrantyProduct;
import org.example.structure.BTreeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Позитивные сценарии: данные не теряются при сохранении и загрузке. */
class SerializerRoundTripTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void roundTripKeepsAllTypesAndFields(Serializer<CatalogData> serializer) throws Exception {
        Path file = dir.resolve("catalog." + serializer.extension());

        serializer.save(TestData.sample(), file);
        CatalogData loaded = serializer.load(file);

        assertEquals(TestData.sample(), loaded);
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void roundTripOfEmptyCatalog(Serializer<CatalogData> serializer) throws Exception {
        Path file = dir.resolve("empty." + serializer.extension());

        serializer.save(CatalogData.current(List.of()), file);
        CatalogData loaded = serializer.load(file);

        assertTrue(loaded.products().isEmpty());
        assertEquals(CatalogData.CURRENT_VERSION, loaded.schemaVersion());
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void roundTripKeepsQuotesCyrillicAndEmoji(Serializer<CatalogData> serializer) throws Exception {
        CatalogData data = CatalogData.current(List.of(
                new ProductRecord("BASE", 5, "Кресло \"Босс\" \\ 日本 😀", "Мебель; \"офис\"", 1.5, 3, null, null)));
        Path file = dir.resolve("text." + serializer.extension());

        serializer.save(data, file);

        assertEquals(data, serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void saveReplacesExistingFileAndLeavesNoTempFile(Serializer<CatalogData> serializer) throws Exception {
        Path file = dir.resolve("again." + serializer.extension());
        serializer.save(CatalogData.current(List.of()), file);

        serializer.save(TestData.sample(), file);

        assertEquals(3, serializer.load(file).products().size());
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "во временной папке должен остаться только итоговый файл");
        }
    }

    @Test
    void jsonFixtureLoadsToExpectedCatalog() throws Exception {
        CatalogData loaded = new JsonCatalogSerializer().load(TestData.fixture("catalog-v1.json"));

        assertEquals(TestData.sample(), loaded);
    }

    @Test
    void binaryFixtureLoadsToExpectedCatalog() throws Exception {
        CatalogData loaded = new BinaryCatalogSerializer().load(TestData.fixture("catalog-v1.bin"));

        assertEquals(TestData.sample(), loaded);
    }

    @Test
    void jsonFileContainsSchemaVersion() throws Exception {
        Path file = dir.resolve("v.json");

        new JsonCatalogSerializer().save(TestData.sample(), file);

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"schema_version\""));
        assertTrue(text.contains("\"schema_version\" : " + CatalogData.CURRENT_VERSION));
    }

    @Test
    void binaryFileHasMagicAndVersionInHeader() throws Exception {
        Path file = dir.resolve("v.bin");

        new BinaryCatalogSerializer().save(TestData.sample(), file);

        byte[] bytes = Files.readAllBytes(file);
        assertEquals("SHOP", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        int version = ((bytes[4] & 0xFF) << 24) | ((bytes[5] & 0xFF) << 16) | ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        assertEquals(CatalogData.CURRENT_VERSION, version);
    }

    @Test
    void jsonCompactIsSmallerThanPretty() throws Exception {
        Path pretty = dir.resolve("p.json");
        Path compact = dir.resolve("c.json");

        new JsonCatalogSerializer(true).save(TestData.sample(), pretty);
        new JsonCatalogSerializer(false).save(TestData.sample(), compact);

        assertTrue(Files.size(compact) < Files.size(pretty));
        assertEquals(TestData.sample(), new JsonCatalogSerializer().load(compact));
    }

    @Test
    void registryFindsSerializerByFileName() {
        assertEquals("json", Serializers.byFileName("a.JSON").format());
        assertEquals("bin", Serializers.byFileName("backup.bin").format());
        assertNull(Serializers.byFileName("data.txt"));
        assertNull(Serializers.byFileName("noextension"));
    }

    @Test
    void productsSurviveFullCycleThroughTree() throws Exception {
        // дерево -> товары -> файл -> товары -> новое дерево
        BTreeStore<Integer, Product> source = new BTreeStore<>(Product::getArticle);
        source.add(new Product(30, "Стол", "Мебель", 100.5, 4));
        source.add(new WarrantyProduct(10, "Телефон", "Электроника", 999.0, 7, 12));
        source.add(new DiscontinuedProduct(20, "Факс", "Оргтехника", 50.0, 0, LocalDate.of(2023, 12, 15)));
        for (int i = 100; i < 140; i++) {
            source.add(new Product(i, "Товар " + i, "Разное", i, i % 5));
        }
        List<Product> snapshot = new ArrayList<>();
        source.forEach(snapshot::add);

        for (Serializer<CatalogData> serializer : Serializers.all()) {
            Path file = dir.resolve("tree." + serializer.extension());
            serializer.save(ProductMapper.toCatalog(snapshot), file);

            CatalogImporter.Result result = CatalogImporter.buildTree(serializer.load(file));

            assertTrue(result.errors().isEmpty());
            assertEquals(source.size(), result.tree().size());
            assertEquals(new DiscontinuedProduct(20, "Факс", "Оргтехника", 50.0, 0, LocalDate.of(2023, 12, 15)),
                    result.tree().findByKey(20));
            assertInstanceOf(WarrantyProduct.class, result.tree().findByKey(10));
            assertNull(result.tree().validateStructure(), "структура дерева после загрузки должна быть корректной");
        }
    }

    @Test
    void duplicateArticlesAreSkippedAndReported() {
        CatalogData data = CatalogData.current(List.of(
                new ProductRecord("BASE", 7, "Первый", "Мебель", 1.0, 1, null, null),
                new ProductRecord("BASE", 7, "Второй", "Мебель", 2.0, 2, null, null),
                new ProductRecord("BASE", 8, "Третий", "Мебель", 3.0, 3, null, null)));

        CatalogImporter.Result result = CatalogImporter.buildTree(data);

        assertEquals(2, result.tree().size());
        assertEquals("Первый", result.tree().findByKey(7).getName());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("7"));
    }
}

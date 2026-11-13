package org.example.serialization;

import org.example.serialization.SerializationException.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Негативные сценарии: плохие файлы и плохие данные дают понятную ошибку с нужным кодом. */
class SerializerNegativeTest {

    @TempDir
    Path dir;

    private static ErrorCode loadError(Serializer<CatalogData> serializer, Path file) {
        SerializationException e = assertThrows(SerializationException.class, () -> serializer.load(file));
        return e.getErrorCode();
    }

    private static final Serializer<CatalogData> JSON = new JsonCatalogSerializer();
    private static final Serializer<CatalogData> BIN = new BinaryCatalogSerializer();

    // ------------------------------------------------------------ общие для обоих форматов

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void emptyFileIsRejected(Serializer<CatalogData> serializer) throws IOException {
        Path file = Files.createFile(dir.resolve("empty." + serializer.extension()));

        assertEquals(ErrorCode.EMPTY_FILE, loadError(serializer, file));
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void missingFileGivesIoError(Serializer<CatalogData> serializer) {
        Path file = dir.resolve("nope." + serializer.extension());

        assertEquals(ErrorCode.IO_ERROR, loadError(serializer, file));
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void saveToMissingDirectoryGivesIoError(Serializer<CatalogData> serializer) {
        Path file = dir.resolve("no-such-dir").resolve("x." + serializer.extension());

        SerializationException e = assertThrows(SerializationException.class,
                () -> serializer.save(TestData.sample(), file));

        assertEquals(ErrorCode.IO_ERROR, e.getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void savingInvalidDataIsRefusedAndWritesNothing(Serializer<CatalogData> serializer) {
        CatalogData bad = CatalogData.current(List.of(
                new ProductRecord("BASE", 1, "Стол", "Мебель", -10.0, 1, null, null)));
        Path file = dir.resolve("bad." + serializer.extension());

        SerializationException e = assertThrows(SerializationException.class, () -> serializer.save(bad, file));

        assertEquals(ErrorCode.BAD_FIELD, e.getErrorCode());
        assertFalse(Files.exists(file));
    }

    @ParameterizedTest
    @MethodSource("org.example.serialization.TestData#serializers")
    void invalidFieldsAreRefusedOnSave(Serializer<CatalogData> serializer) {
        List<CatalogData> invalid = List.of(
                one(new ProductRecord("BASE", 0, "Стол", "Мебель", 1.0, 1, null, null)),          // артикул
                one(new ProductRecord("BASE", 1, " ", "Мебель", 1.0, 1, null, null)),             // название
                one(new ProductRecord("BASE", 1, "Стол", "", 1.0, 1, null, null)),                // категория
                one(new ProductRecord("BASE", 1, "Стол", "Мебель", Double.NaN, 1, null, null)),   // цена NaN
                one(new ProductRecord("BASE", 1, "Стол", "Мебель", 1.0, -1, null, null)),         // остаток
                one(new ProductRecord("WARRANTY", 1, "Стол", "Мебель", 1.0, 1, -3, null)),        // гарантия
                one(new ProductRecord("WARRANTY", 1, "Стол", "Мебель", 1.0, 1, null, null)),      // нет гарантии
                one(new ProductRecord("DISCONTINUED", 1, "Стол", "Мебель", 1.0, 1, null, "вчера")),
                one(new ProductRecord("GADGET", 1, "Стол", "Мебель", 1.0, 1, null, null)));
        for (CatalogData data : invalid) {
            Path file = dir.resolve("v" + invalid.indexOf(data) + "." + serializer.extension());
            SerializationException e = assertThrows(SerializationException.class,
                    () -> serializer.save(data, file), data.products().get(0).toString());
            assertEquals(ErrorCode.BAD_FIELD, e.getErrorCode());
        }
    }

    private static CatalogData one(ProductRecord r) {
        return CatalogData.current(List.of(r));
    }

    // ------------------------------------------------------------ JSON

    @ParameterizedTest
    @CsvSource({
            "broken.json,BAD_FORMAT",            // обрезанный JSON
            "not-an-object.json,BAD_FORMAT",     // массив вместо объекта
            "missing-version.json,BAD_FORMAT",   // нет schema_version
            "catalog-v99.json,UNSUPPORTED_VERSION",
            "negative-price.json,BAD_FIELD",
            "wrong-type.json,BAD_FIELD",         // "stock": "много"
            "unknown-type.json,BAD_FIELD",
            "blank-name.json,BAD_FIELD"
    })
    void brokenJsonFixturesGiveExpectedCode(String fixture, ErrorCode expected) {
        assertEquals(expected, loadError(JSON, TestData.fixture(fixture)));
    }

    @Test
    void jsonWithoutProductsArrayIsRejected() throws IOException {
        Path file = dir.resolve("noproducts.json");
        Files.writeString(file, "{\"schema_version\": 1}", StandardCharsets.UTF_8);

        assertEquals(ErrorCode.BAD_FIELD, loadError(JSON, file));
    }

    @Test
    void jsonWithUnknownFieldIsRejected() throws IOException {
        Path file = dir.resolve("extra.json");
        Files.writeString(file,
                "{\"schema_version\":1,\"products\":[{\"type\":\"BASE\",\"article\":1,\"name\":\"a\","
                        + "\"category\":\"b\",\"price\":1.0,\"stock\":1,\"color\":\"red\"}]}",
                StandardCharsets.UTF_8);

        assertEquals(ErrorCode.BAD_FIELD, loadError(JSON, file));
    }

    @Test
    void jsonWithOnlyWhitespaceIsTreatedAsEmpty() throws IOException {
        Path file = dir.resolve("blank.json");
        Files.writeString(file, "   \n  ", StandardCharsets.UTF_8);

        assertEquals(ErrorCode.EMPTY_FILE, loadError(JSON, file));
    }

    @Test
    void errorMessageNamesRecordAndReason() {
        SerializationException e = assertThrows(SerializationException.class,
                () -> JSON.load(TestData.fixture("negative-price.json")));

        assertTrue(e.getMessage().contains("1001"), e.getMessage());
        assertTrue(e.getMessage().contains("цена"), e.getMessage());
    }

    // ------------------------------------------------------------ бинарный формат

    private Path savedBinary() throws Exception {
        Path file = dir.resolve("ok.bin");
        BIN.save(TestData.sample(), file);
        return file;
    }

    @Test
    void textFileIsNotABinaryCatalog() throws IOException {
        Path file = dir.resolve("text.bin");
        Files.writeString(file, "это просто текст, а не каталог", StandardCharsets.UTF_8);

        assertEquals(ErrorCode.BAD_FORMAT, loadError(BIN, file));
    }

    @Test
    void jsonFileOpenedAsBinaryIsBadFormat() {
        assertEquals(ErrorCode.BAD_FORMAT, loadError(BIN, TestData.fixture("catalog-v1.json")));
    }

    @Test
    void truncatedBinaryFileIsCorrupted() throws Exception {
        byte[] bytes = Files.readAllBytes(savedBinary());
        Path file = dir.resolve("cut.bin");
        Files.write(file, Arrays.copyOf(bytes, bytes.length / 2));

        assertEquals(ErrorCode.CORRUPTED_FILE, loadError(BIN, file));
    }

    @Test
    void binaryWithOnlyMagicIsCorrupted() throws Exception {
        Path file = dir.resolve("magic.bin");
        Files.write(file, "SHOP".getBytes(StandardCharsets.US_ASCII));

        assertEquals(ErrorCode.CORRUPTED_FILE, loadError(BIN, file));
    }

    @Test
    void flippedByteBreaksChecksum() throws Exception {
        byte[] bytes = Files.readAllBytes(savedBinary());
        bytes[bytes.length / 2] ^= 0x55;
        Path file = dir.resolve("flip.bin");
        Files.write(file, bytes);

        assertEquals(ErrorCode.CORRUPTED_FILE, loadError(BIN, file));
    }

    @Test
    void trailingGarbageIsCorrupted() throws Exception {
        byte[] bytes = Files.readAllBytes(savedBinary());
        byte[] longer = Arrays.copyOf(bytes, bytes.length + 5);
        Path file = dir.resolve("tail.bin");
        Files.write(file, longer);

        assertEquals(ErrorCode.CORRUPTED_FILE, loadError(BIN, file));
    }

    @Test
    void futureBinaryVersionIsUnsupportedEvenWithBadChecksum() throws Exception {
        byte[] bytes = Files.readAllBytes(savedBinary());
        bytes[7] = 99;                       // версия 99; CRC намеренно не пересчитан
        Path file = dir.resolve("future.bin");
        Files.write(file, bytes);

        assertEquals(ErrorCode.UNSUPPORTED_VERSION, loadError(BIN, file));
    }

    @Test
    void zeroBinaryVersionIsUnsupported() throws Exception {
        byte[] bytes = Files.readAllBytes(savedBinary());
        bytes[7] = 0;
        Path file = dir.resolve("zero.bin");
        Files.write(file, bytes);

        assertEquals(ErrorCode.UNSUPPORTED_VERSION, loadError(BIN, file));
    }
}

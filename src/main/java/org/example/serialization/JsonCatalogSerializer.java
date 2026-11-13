package org.example.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.example.serialization.SerializationException.ErrorCode;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON-формат на Jackson. Версия хранится в поле schema_version.
 * Загрузка в два шага: сначала читается дерево JSON и проверяется версия (файл из будущего
 * отвергается до разбора полей), и только потом дерево превращается в CatalogData.
 */
public final class JsonCatalogSerializer implements Serializer<CatalogData> {

    private static final String FIELD_VERSION = "schema_version";
    private static final String FIELD_PRODUCTS = "products";

    /** ObjectMapper потокобезопасен после настройки, поэтому один на все вызовы. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean pretty;

    /** Формат с отступами — читается глазами. */
    public JsonCatalogSerializer() {
        this(true);
    }

    /** @param pretty true — с отступами, false — компактно (меньше размер, нужно для сравнения). */
    public JsonCatalogSerializer(boolean pretty) {
        this.pretty = pretty;
    }

    @Override
    public String format() {
        return "json";
    }

    @Override
    public String extension() {
        return "json";
    }

    @Override
    public String description() {
        return "JSON (*.json)";
    }

    @Override
    public void save(CatalogData data, Path file) throws SerializationException {
        CatalogValidator.validate(data);
        ObjectWriter writer = pretty ? MAPPER.writerWithDefaultPrettyPrinter() : MAPPER.writer();
        byte[] bytes;
        try {
            bytes = writer.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException(ErrorCode.BAD_FIELD,
                    "Не удалось преобразовать данные в JSON: " + e.getOriginalMessage(), e);
        }
        AtomicFiles.write(file, bytes);
    }

    @Override
    public CatalogData load(Path file) throws SerializationException {
        byte[] bytes = AtomicFiles.read(file);
        if (bytes.length == 0) {
            throw new SerializationException(ErrorCode.EMPTY_FILE, "Файл пустой");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(bytes);
        } catch (JsonProcessingException e) {
            throw new SerializationException(ErrorCode.BAD_FORMAT,
                    "Некорректный JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new SerializationException(ErrorCode.IO_ERROR, "Ошибка чтения: " + e.getMessage(), e);
        }

        if (root == null || root.isMissingNode()) {
            throw new SerializationException(ErrorCode.EMPTY_FILE, "Файл не содержит данных");
        }
        if (!root.isObject()) {
            throw new SerializationException(ErrorCode.BAD_FORMAT, "Ожидался JSON-объект верхнего уровня");
        }

        JsonNode versionNode = root.get(FIELD_VERSION);
        if (versionNode == null || !versionNode.isIntegralNumber()) {
            throw new SerializationException(ErrorCode.BAD_FORMAT,
                    "Нет числового поля " + FIELD_VERSION + ": версия файла неизвестна");
        }
        int version = versionNode.intValue();
        if (version < CatalogData.MIN_SUPPORTED_VERSION || version > CatalogData.CURRENT_VERSION) {
            throw new SerializationException(ErrorCode.UNSUPPORTED_VERSION,
                    "Версия формата " + version + " не поддерживается (поддерживаются версии "
                            + CatalogData.MIN_SUPPORTED_VERSION + "–" + CatalogData.CURRENT_VERSION + ")");
        }

        JsonNode products = root.get(FIELD_PRODUCTS);
        if (products == null || !products.isArray()) {
            throw new SerializationException(ErrorCode.BAD_FIELD, "Поле " + FIELD_PRODUCTS + " должно быть массивом");
        }

        CatalogData data;
        try {
            data = MAPPER.treeToValue(root, CatalogData.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            String reason = e instanceof JsonProcessingException jpe ? jpe.getOriginalMessage() : e.getMessage();
            throw new SerializationException(ErrorCode.BAD_FIELD, "Недопустимое значение в файле: " + reason, e);
        }

        CatalogValidator.validate(data);
        return data;
    }
}

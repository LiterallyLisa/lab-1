package org.example.serialization;

import java.nio.file.Path;

/**
 * Общий контракт сериализации. GUI и тесты работают только через него.
 * Главное свойство: load(save(x)) возвращает данные, равные x (roundtrip).
 *
 * @param <T> тип сохраняемых данных
 */
public interface Serializer<T> {

    /** Короткое имя формата: "json", "bin". */
    String format();

    /** Расширение файла без точки. */
    String extension();

    /** Подпись для диалога выбора файла. */
    String description();

    /** Записывает данные в файл. Существующий файл заменяется целиком или остаётся нетронутым. */
    void save(T data, Path file) throws SerializationException;

    /** Читает и проверяет данные из файла. */
    T load(Path file) throws SerializationException;
}

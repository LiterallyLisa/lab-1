package org.example.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Плоское представление одного товара для файла (DTO). Все числовые поля — обёртки, чтобы отсутствие поля
 * в файле было отличимо от нуля. Поле type выбирает конкретный класс товара при загрузке:
 * BASE, WARRANTY или DISCONTINUED (те же значения, что и в CSV).
 * Дата хранится строкой ISO (YYYY-MM-DD), поэтому Jackson-модуль для java.time не нужен.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductRecord(
        String type,
        Integer article,
        String name,
        String category,
        Double price,
        Integer stock,
        Integer warrantyMonths,
        String discontinuedDate
) {
    public static final String TYPE_BASE = "BASE";
    public static final String TYPE_WARRANTY = "WARRANTY";
    public static final String TYPE_DISCONTINUED = "DISCONTINUED";
}

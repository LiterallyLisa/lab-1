package org.example.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductRecord(
        String type,             // "BASE", "WARRANTY" или "DISCONTINUED"
        Integer article,         // артикул
        String name,             // название
        String category,         // категория
        Double price,            // цена
        Integer stock,           // остаток
        Integer warrantyMonths,  // гарантия (только для WARRANTY, иначе null)
        String discontinuedDate  // дата снятия (только для DISCONTINUED, иначе null)
) {
    public static final String TYPE_BASE = "BASE";
    public static final String TYPE_WARRANTY = "WARRANTY";
    public static final String TYPE_DISCONTINUED = "DISCONTINUED";
}

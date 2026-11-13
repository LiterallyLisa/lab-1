package org.example.serialization;

import org.example.serialization.SerializationException.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;

public final class CatalogValidator {

    private CatalogValidator() {
    }

    public static void validate(CatalogData data) throws SerializationException {
        int index = 0;
        for (ProductRecord r : data.products()) {
            index++;
            if (r == null) {
                throw bad(index, null, "пустая запись");
            }
            validateRecord(r, index);
        }
    }

    private static void validateRecord(ProductRecord r, int index) throws SerializationException {
        Integer article = r.article();
        if (article == null || article <= 0) {
            throw bad(index, article, "артикул должен быть положительным числом");
        }
        if (r.type() == null) {
            throw bad(index, article, "не указан тип товара");
        }
        if (isBlank(r.name())) {
            throw bad(index, article, "название не может быть пустым");
        }
        if (isBlank(r.category())) {
            throw bad(index, article, "категория не может быть пустой");
        }
        if (r.price() == null || r.price().isNaN() || r.price().isInfinite() || r.price() < 0) {
            throw bad(index, article, "цена должна быть неотрицательным числом");
        }
        if (r.stock() == null || r.stock() < 0) {
            throw bad(index, article, "остаток не может быть отрицательным");
        }
        switch (r.type()) {
            case ProductRecord.TYPE_BASE -> { }
            case ProductRecord.TYPE_WARRANTY -> {
                if (r.warrantyMonths() == null || r.warrantyMonths() < 0) {
                    throw bad(index, article, "срок гарантии должен быть неотрицательным числом");
                }
            }
            case ProductRecord.TYPE_DISCONTINUED -> {
                if (isBlank(r.discontinuedDate())) {
                    throw bad(index, article, "не указана дата снятия с продажи");
                }
                try {
                    LocalDate.parse(r.discontinuedDate());
                } catch (DateTimeException e) {
                    throw bad(index, article, "дата снятия с продажи должна быть в формате ГГГГ-ММ-ДД");
                }
            }
            default -> throw bad(index, article, "неизвестный тип товара «" + r.type() + "»");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static SerializationException bad(int index, Integer article, String reason) {
        String who = article == null ? "" : " (артикул " + article + ")";
        return new SerializationException(ErrorCode.BAD_FIELD, "Запись №" + index + who + ": " + reason);
    }
}

package org.example.serialization;

import org.example.model.DiscontinuedProduct;
import org.example.model.Product;
import org.example.model.WarrantyProduct;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Преобразование между моделью (Product и наследники) и плоскими записями файла. */
public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductRecord toRecord(Product p) {
        if (p instanceof DiscontinuedProduct dp) {
            LocalDate date = dp.getDiscontinuedDate();
            return new ProductRecord(ProductRecord.TYPE_DISCONTINUED, dp.getArticle(), dp.getName(),
                    dp.getCategory(), dp.getPrice(), dp.getStock(), null,
                    date == null ? null : date.toString());
        }
        if (p instanceof WarrantyProduct wp) {
            return new ProductRecord(ProductRecord.TYPE_WARRANTY, wp.getArticle(), wp.getName(),
                    wp.getCategory(), wp.getPrice(), wp.getStock(), wp.getWarrantyMonths(), null);
        }
        return new ProductRecord(ProductRecord.TYPE_BASE, p.getArticle(), p.getName(),
                p.getCategory(), p.getPrice(), p.getStock(), null, null);
    }

    /** Запись должна быть уже проверена CatalogValidator. */
    public static Product toProduct(ProductRecord r) {
        return switch (r.type()) {
            case ProductRecord.TYPE_WARRANTY -> new WarrantyProduct(r.article(), r.name(), r.category(),
                    r.price(), r.stock(), r.warrantyMonths());
            case ProductRecord.TYPE_DISCONTINUED -> new DiscontinuedProduct(r.article(), r.name(), r.category(),
                    r.price(), r.stock(), LocalDate.parse(r.discontinuedDate()));
            case ProductRecord.TYPE_BASE -> new Product(r.article(), r.name(), r.category(),
                    r.price(), r.stock());
            default -> throw new IllegalArgumentException("Неизвестный тип: " + r.type());
        };
    }

    public static CatalogData toCatalog(List<Product> products) {
        List<ProductRecord> records = new ArrayList<>(products.size());
        for (Product p : products) {
            records.add(toRecord(p));
        }
        return CatalogData.current(records);
    }

    /** Данные должны быть уже проверены CatalogValidator. */
    public static List<Product> toProducts(CatalogData data) {
        List<Product> result = new ArrayList<>(data.products().size());
        for (ProductRecord r : data.products()) {
            result.add(toProduct(r));
        }
        return result;
    }
}

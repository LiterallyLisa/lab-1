package org.example.io;

import org.example.model.Product;

import java.util.Collections;
import java.util.List;

public class CsvLoadResult {

    private final List<Product> products;
    private final List<String> errors;

    public CsvLoadResult(List<Product> products, List<String> errors) {
        this.products = products;
        this.errors = errors;
    }

    public List<Product> getProducts() {
        return Collections.unmodifiableList(products);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** true, если при загрузке не было ни одной ошибки */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /** Сколько строк было пропущено */
    public int getSkippedCount() {
        return errors.size();
    }
}
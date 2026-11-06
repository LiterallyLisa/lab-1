package org.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WarrantyProduct extends Product implements Editable {

    private int warrantyMonths;

    public WarrantyProduct(int article, String name, String category,
                           double price, int stock, int warrantyMonths) {
        super(article, name, category, price, stock);
        this.warrantyMonths = warrantyMonths;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (getArticle() <= 0) {
            errors.add("Артикул должен быть положительным числом");
        }
        if (getName() == null || getName().isBlank()) {
            errors.add("Название не может быть пустым");
        }
        if (getCategory() == null || getCategory().isBlank()) {
            errors.add("Категория не может быть пустой");
        }
        if (getPrice() <= 0) {
            errors.add("Цена должна быть больше нуля");
        }
        if (getStock() < 0) {
            errors.add("Остаток не может быть отрицательным");
        }
        if (warrantyMonths < 0) {
            errors.add("Срок гарантии не может быть отрицательным");
        }

        return errors;
    }

    @Override
    public boolean equals(Object o) {
        // super.equals уже проверяет getClass(), поэтому приведение безопасно
        return super.equals(o) && warrantyMonths == ((WarrantyProduct) o).warrantyMonths;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), warrantyMonths);
    }

    @Override
    public String toString() {
        return "WarrantyProduct{" +
                "article=" + getArticle() +
                ", name='" + getName() + '\'' +
                ", category='" + getCategory() + '\'' +
                ", price=" + getPrice() +
                ", stock=" + getStock() +
                ", warrantyMonths=" + warrantyMonths +
                '}';
    }
}
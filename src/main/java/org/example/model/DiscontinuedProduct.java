package org.example.model;

import java.time.LocalDate;
import java.util.Objects;

public class DiscontinuedProduct extends Product {

    private LocalDate discontinuedDate;

    public DiscontinuedProduct(int article, String name, String category,
                               double price, int stock, LocalDate discontinuedDate) {
        // super(...) вызывает конструктор родителя Product
        super(article, name, category, price, stock);
        this.discontinuedDate = discontinuedDate;
    }

    public LocalDate getDiscontinuedDate() {
        return discontinuedDate;
    }

    public void setDiscontinuedDate(LocalDate discontinuedDate) {
        this.discontinuedDate = discontinuedDate;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o)
                && Objects.equals(discontinuedDate, ((DiscontinuedProduct) o).discontinuedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), discontinuedDate);
    }

    @Override
    public String toString() {
        return "DiscontinuedProduct{" +
                "article=" + getArticle() +
                ", name='" + getName() + '\'' +
                ", category='" + getCategory() + '\'' +
                ", price=" + getPrice() +
                ", stock=" + getStock() +
                ", discontinuedDate=" + discontinuedDate +
                '}';
    }
}
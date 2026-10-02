package org.example.model;

import java.util.Objects;

public class Product {

    private int article;
    private String name;
    private String category;
    private double price;
    private int stock;

    public Product(int article, String name, String category, double price, int stock) {
        this.article = article;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public int getArticle() {
        return article;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public void setArticle(int article) {
        this.article = article;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "Product{" +
                "article=" + article +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", stock=" + stock +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return article == product.article
                && Double.compare(price, product.price) == 0
                && stock == product.stock
                && Objects.equals(name, product.name)
                && Objects.equals(category, product.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(article, name, category, price, stock);
    }
}
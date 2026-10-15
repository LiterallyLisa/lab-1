package org.example.structure;

import org.example.model.Product;

import java.util.Random;

/**
 * Генератор случайных товаров для тестирования B-дерева.
 */
public class DataGenerator {

    /** Категории для случайного выбора. */
    private static final String[] CATEGORIES = {
            "Электроника", "Мебель", "Оргтехника", "Освещение", "Бытовая техника"
    };

    /** Названия для случайного выбора. */
    private static final String[] NAMES = {
            "Стол", "Стул", "Ноутбук", "Телефон", "Планшет", "Лампа",
            "Кресло", "Шкаф", "Монитор", "Клавиатура", "Мышь", "Принтер"
    };

    private final Random random;

    public DataGenerator() {
        this.random = new Random(42);   // seed для воспроизводимости
    }

    /**
     * Генерирует count товаров с уникальными артикулами (1000..1000+count-1).
     * Не добавляет в дерево — возвращает список.
     */
    public Product[] generate(int count) {
        Product[] products = new Product[count];
        for (int i = 0; i < count; i++) {
            int article = 1000 + i;   // гарантированно уникальные
            String name = NAMES[random.nextInt(NAMES.length)];
            String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
            double price = 100 + random.nextDouble() * 9900;   // 100..10000
            int stock = random.nextInt(100);                   // 0..99
            products[i] = new Product(article, name, category, price, stock);
        }
        return products;
    }
}
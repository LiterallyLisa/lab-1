package org.example.structure;

import org.example.model.Product;

import java.util.Random;

/**
 * Генератор случайных товаров для тестирования B-дерева.
 * Артикулы уникальны и идут в случайном порядке (возрастающий порядок — худший случай
 * для заполненности узлов и делает визуализацию нетипичной).
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

    /** Артикул, с которого по умолчанию начинается нумерация. */
    public static final int DEFAULT_FIRST_ARTICLE = 1000;

    private final Random random;

    /** Генератор с фиксированным seed — результат воспроизводим. */
    public DataGenerator() {
        this(42);
    }

    public DataGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Возвращает count уникальных артикулов firstArticle..firstArticle+count-1 в случайном порядке.
     */
    public int[] shuffledArticles(int count, int firstArticle) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        if ((long) firstArticle + count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Артикулы выходят за пределы int");
        }
        int[] articles = new int[count];
        for (int i = 0; i < count; i++) {
            articles[i] = firstArticle + i;
        }
        for (int i = count - 1; i > 0; i--) {       // перемешивание Фишера — Йетса
            int j = random.nextInt(i + 1);
            int tmp = articles[i];
            articles[i] = articles[j];
            articles[j] = tmp;
        }
        return articles;
    }

    /** Создаёт один случайный товар с заданным артикулом. */
    public Product createProduct(int article) {
        String name = NAMES[random.nextInt(NAMES.length)];
        String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
        double price = 100 + random.nextDouble() * 9900;   // 100..10000
        int stock = random.nextInt(100);                   // 0..99
        return new Product(article, name, category, price, stock);
    }

    /** count товаров с уникальными артикулами, начиная с firstArticle, в случайном порядке. */
    public Product[] generate(int count, int firstArticle) {
        int[] articles = shuffledArticles(count, firstArticle);
        Product[] products = new Product[count];
        for (int i = 0; i < count; i++) {
            products[i] = createProduct(articles[i]);
        }
        return products;
    }

    /** То же, начиная с DEFAULT_FIRST_ARTICLE. */
    public Product[] generate(int count) {
        return generate(count, DEFAULT_FIRST_ARTICLE);
    }
}

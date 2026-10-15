package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.gui.MainView;
import org.example.model.Product;
import org.example.structure.BTreeStore;
import java.util.List;
import org.example.structure.TreeStats;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        view.show();
    }

    public static void main(String[] args) {
        // ===== Быстрый тест B-дерева =====
        BTreeStore<Integer, Product> tree = new BTreeStore<>();

        tree.add(new Product(1001, "Стол", "Мебель", 8999, 12));
        tree.add(new Product(1002, "Ноутбук", "Электроника", 79999, 15));
        tree.add(new Product(1003, "Плеер", "Электроника", 2999, 0));
        tree.add(new Product(1004, "Телефон", "Электроника", 54999, 8));
        tree.add(new Product(1005, "Кресло", "Мебель", 12499, 5));
        tree.add(new Product(1006, "Факс", "Оргтехника", 4999, 0));
        tree.add(new Product(1007, "Планшет", "Электроника", 32999, 20));
        tree.add(new Product(1008, "Лампа", "Освещение", 1499, 30));
        tree.add(new Product(1009, "Стиральная машина", "Бытовая техника", 39999, 3));
        tree.add(new Product(1010, "Видеомагнитофон", "Электроника", 8999, 0));

        // Тест удаления
        System.out.println("=== Тест удаления ===");
        System.out.println("Удаляем 1005: " + tree.removeByKey(1005));
        System.out.println("Удаляем 9999: " + tree.removeByKey(9999));
        System.out.println("Размер после удаления: " + tree.size());
        System.out.println("Ищем 1005 (должно быть null): " + tree.findByKey(1005));
        System.out.println("Размер дерева: " + tree.size());

        // Тест rangeScan
        System.out.println("\n=== Тест rangeScan ===");
        List<Product> inRange = tree.rangeScan(1002, 1007);
        System.out.println("В диапазоне [1002, 1007]: " + inRange.size() + " товаров");
        for (Product p : inRange) {
            System.out.println("  " + p.getArticle() + " — " + p.getName());
        }

        // Тест статистики
        System.out.println("\n=== Статистика ===");
        TreeStats stats = tree.getStats();
        System.out.println(stats);
        Product found = tree.findByKey(1005);
        System.out.println("Найдено 1005: " + found);

        Product notFound = tree.findByKey(9999);
        System.out.println("Найдено 9999: " + notFound);
        // Тест генерации
        System.out.println("\n=== Тест генерации ===");
        BTreeStore<Integer, Product> bigTree = new BTreeStore<>();
        bigTree.generateAndAdd(100);
        System.out.println("Сгенерировано и добавлено: " + bigTree.size());

        TreeStats bigStats = bigTree.getStats();
        System.out.println("Статистика большого дерева: " + bigStats);
        // ===== Конец теста =====

        launch(args);
    }
}
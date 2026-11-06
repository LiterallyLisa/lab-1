package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.gui.MainView;
import org.example.structure.Benchmark;
import org.example.structure.ConcurrencyDemo;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        view.show();
    }

    public static void main(String[] args) {
        // 1. Сравнение с java.util
        Benchmark.compare(1000, true);   // 1000, вставка по возрастанию
        Benchmark.compare(1000, false);  // 1000, вставка в случайном порядке
        Benchmark.compare(10000, true);
        Benchmark.compare(10000, false);

        // 2. Демонстрация concurrency
        try {
            ConcurrencyDemo.main(args);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 3. Запуск GUI
        launch(args);
    }
}
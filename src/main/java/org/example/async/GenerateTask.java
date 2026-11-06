package org.example.async;

import javafx.concurrent.Task;
import org.example.model.Product;
import org.example.structure.BTreeStore;
import org.example.structure.DataGenerator;

import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Задача генерации данных в фоне. Строит НОВОЕ дерево: копирует в него уже существующие товары,
 * затем добавляет count сгенерированных. Старое дерево только читается, поэтому при отмене
 * (результат null) пользователь ничего не теряет, а GUI-поток при успехе просто подменяет ссылку.
 * Артикулы генерируются начиная с (максимальный существующий + 1), так что дубликатов не бывает.
 */
public class GenerateTask extends Task<BuildResult> {

    private static final int UPDATE_EVERY = 1024;

    private final BTreeStore<Integer, Product> source;
    private final DataGenerator generator;
    private final int count;

    public GenerateTask(BTreeStore<Integer, Product> source, DataGenerator generator, int count) {
        this.source = source;
        this.generator = generator;
        this.count = count;
    }

    @Override
    protected BuildResult call() {
        long startNanos = System.nanoTime();
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        int total = source.size() + count;
        int[] done = {0};
        int[] maxArticle = {DataGenerator.DEFAULT_FIRST_ARTICLE - 1};

        // ---- 1. Копируем существующие товары ----
        updateMessage("Копирование существующих данных...");
        try {
            source.forEach(p -> {
                if (isCancelled()) {
                    throw new CancellationException();   // единственный способ прервать forEach
                }
                tree.add(p);
                maxArticle[0] = Math.max(maxArticle[0], p.getArticle());
                tick(done, total);
            });
        } catch (CancellationException e) {
            updateMessage("Отменено");
            return null;
        }

        // ---- 2. Генерируем и добавляем новые ----
        updateMessage("Генерация " + count + " товаров...");
        int first = maxArticle[0] + 1;
        int[] articles = generator.shuffledArticles(count, first);   // бросит исключение при переполнении int
        for (int article : articles) {
            if (isCancelled()) {
                updateMessage("Отменено");
                return null;
            }
            tree.add(generator.createProduct(article));
            tick(done, total);
        }

        updateProgress(1, 1);
        updateMessage("Сгенерировано: " + count + " товаров");
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        return new BuildResult(tree, List.of(), elapsedMillis);
    }

    private void tick(int[] done, int total) {
        done[0]++;
        if (done[0] % UPDATE_EVERY == 0) {
            updateProgress(done[0], total);
        }
    }
}

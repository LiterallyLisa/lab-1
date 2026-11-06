package org.example.async;

import javafx.concurrent.Task;
import org.example.io.CsvLoadResult;
import org.example.io.CsvLoader;
import org.example.model.Product;
import org.example.structure.BTreeStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Задача для фоновой загрузки CSV (из файла или из ресурса) и построения B-дерева.
 * Прогресс реальный: чтение файла занимает 0..70% (по прочитанным байтам),
 * построение дерева — 70..100% (по числу вставленных товаров).
 * Отмена работает на обеих стадиях; при отмене возвращается null, а текущее дерево не трогается.
 */
public class LoadTask extends Task<BuildResult> {

    /** Доля общего прогресса, отведённая на чтение файла. */
    private static final double READ_SHARE = 0.7;

    /** Как часто (в элементах) обновлять прогресс при построении дерева. */
    private static final int UPDATE_EVERY = 1024;

    private final Path file;           // ровно одно из двух полей не null
    private final String resourcePath;

    private LoadTask(Path file, String resourcePath) {
        this.file = file;
        this.resourcePath = resourcePath;
    }

    public static LoadTask fromFile(Path file) {
        return new LoadTask(Objects.requireNonNull(file), null);
    }

    public static LoadTask fromResource(String resourcePath) {
        return new LoadTask(null, Objects.requireNonNull(resourcePath));
    }

    @Override
    protected BuildResult call() throws Exception {
        long startNanos = System.nanoTime();
        CsvLoader loader = new CsvLoader();

        updateMessage(file != null ? "Чтение файла: " + file.getFileName() : "Чтение встроенного примера...");
        updateProgress(0, 1);

        // ---- 1. Чтение и разбор ----
        CsvLoadResult parsed;
        try {
            CsvLoader.ProgressListener listener = (bytesRead, totalBytes) -> {
                if (totalBytes > 0) {
                    updateProgress(READ_SHARE * bytesRead, totalBytes);
                } else {
                    updateProgress(-1, -1);          // размер неизвестен — «бегущий» индикатор
                }
                return !isCancelled();
            };
            parsed = (file != null)
                    ? loader.load(file, listener)
                    : loader.loadFromResource(resourcePath, listener);
        } catch (CancellationException e) {
            updateMessage("Отменено");
            return null;
        }

        // ---- 2. Построение дерева (в этом же фоновом потоке) ----
        updateMessage("Построение B-дерева...");
        List<String> errors = new ArrayList<>(parsed.getErrors());
        BTreeStore<Integer, Product> tree = new BTreeStore<>(Product::getArticle);
        List<Product> products = parsed.getProducts();
        int total = products.size();
        int done = 0;
        for (Product p : products) {
            if (isCancelled()) {
                updateMessage("Отменено");
                return null;
            }
            try {
                tree.add(p);
            } catch (IllegalArgumentException duplicate) {
                errors.add("Артикул " + p.getArticle() + " повторяется — запись пропущена");
            }
            done++;
            if (done % UPDATE_EVERY == 0) {
                updateProgress(READ_SHARE + (1 - READ_SHARE) * done / total, 1);
            }
        }

        updateProgress(1, 1);
        updateMessage("Загружено: " + tree.size() + " товаров" +
                (errors.isEmpty() ? "" : ", пропущено: " + errors.size()));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        return new BuildResult(tree, errors, elapsedMillis);
    }
}

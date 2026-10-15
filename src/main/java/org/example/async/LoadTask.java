package org.example.async;

import javafx.concurrent.Task;
import org.example.io.CsvLoadResult;
import org.example.io.CsvLoader;

import java.nio.file.Path;

/**
 * Задача для фоновой загрузки CSV-файла.
 * Возвращает CsvLoadResult.
 */
public class LoadTask extends Task<CsvLoadResult> {

    private final Path file;

    public LoadTask(Path file) {
        this.file = file;
    }

    @Override
    protected CsvLoadResult call() throws Exception {
        updateMessage("Загрузка файла: " + file.getFileName());
        updateProgress(0, 1);

        CsvLoader loader = new CsvLoader();
        CsvLoadResult result = loader.load(file);

        // Проверяем, не отменена ли задача
        if (isCancelled()) {
            updateMessage("Отменено");
            return null;
        }

        updateProgress(1, 1);
        updateMessage("Загружено: " + result.getProducts().size() + " товаров" +
                (result.getSkippedCount() > 0 ? ", пропущено: " + result.getSkippedCount() : ""));
        return result;
    }
}
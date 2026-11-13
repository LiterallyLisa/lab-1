package org.example.async;

import javafx.concurrent.Task;
import org.example.serialization.CatalogData;
import org.example.serialization.CatalogImporter;
import org.example.serialization.Serializer;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;

/**
 * Фоновый импорт каталога из JSON или бинарного файла.
 * Чтение и проверка файла — 0..40% прогресса, построение дерева — 40..100%.
 * Ошибки формата выбрасываются как SerializationException и показываются в GUI;
 * при отмене возвращается null, текущее дерево не трогается.
 */
public class ImportTask extends Task<BuildResult> {

    private static final double READ_SHARE = 0.4;

    private final Path file;
    private final Serializer<CatalogData> serializer;

    public ImportTask(Path file, Serializer<CatalogData> serializer) {
        this.file = file;
        this.serializer = serializer;
    }

    @Override
    protected BuildResult call() throws Exception {
        long start = System.nanoTime();

        updateMessage("Чтение файла: " + file.getFileName());
        updateProgress(-1, -1);
        CatalogData data = serializer.load(file);          // бросает SerializationException
        if (isCancelled()) {
            return null;
        }

        updateMessage("Построение B-дерева...");
        updateProgress(READ_SHARE, 1);
        CatalogImporter.Result built;
        try {
            built = CatalogImporter.buildTree(data, (done, total) -> {
                updateProgress(READ_SHARE + (1 - READ_SHARE) * done / Math.max(total, 1), 1);
                return !isCancelled();
            });
        } catch (CancellationException e) {
            updateMessage("Отменено");
            return null;
        }

        updateProgress(1, 1);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        return new BuildResult(built.tree(), built.errors(), elapsedMillis);
    }
}

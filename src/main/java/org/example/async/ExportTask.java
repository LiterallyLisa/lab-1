package org.example.async;

import javafx.concurrent.Task;
import org.example.model.Product;
import org.example.serialization.CatalogData;
import org.example.serialization.ProductMapper;
import org.example.serialization.Serializer;

import java.nio.file.Path;
import java.util.List;

/**
 * Фоновый экспорт каталога. Снимок товаров делает GUI-поток (дерево в фоне не читается),
 * а преобразование и запись на диск идут здесь. Возвращает число сохранённых товаров.
 */
public class ExportTask extends Task<Integer> {

    private final List<Product> snapshot;
    private final Serializer<CatalogData> serializer;
    private final Path file;

    public ExportTask(List<Product> snapshot, Serializer<CatalogData> serializer, Path file) {
        this.snapshot = snapshot;
        this.serializer = serializer;
        this.file = file;
    }

    @Override
    protected Integer call() throws Exception {
        updateMessage("Сохранение: " + file.getFileName());
        updateProgress(-1, -1);
        CatalogData data = ProductMapper.toCatalog(snapshot);
        serializer.save(data, file);                       // бросает SerializationException
        return snapshot.size();
    }
}

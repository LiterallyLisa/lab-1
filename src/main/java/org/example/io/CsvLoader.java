package org.example.io;

import org.example.model.DiscontinuedProduct;
import org.example.model.Product;
import org.example.model.WarrantyProduct;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

public class CsvLoader {

    /** Количество колонок в корректной строке. */
    private static final int EXPECTED_FIELDS = 8;

    /** Разделитель полей. */
    private static final String SEPARATOR = ";";

    /** Как часто (в строках) сообщать о прогрессе и проверять отмену. Должно быть степенью двойки. */
    private static final int PROGRESS_EVERY_LINES = 256;

    /**
     * Слушатель прогресса чтения. Вызывается из потока, который читает файл.
     * Вернув false, слушатель прерывает загрузку (CancellationException).
     */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * @param bytesRead  сколько байт уже прочитано
         * @param totalBytes общий размер или -1, если он неизвестен (например, ресурс из jar)
         * @return true — продолжать, false — отменить
         */
        boolean onProgress(long bytesRead, long totalBytes);
    }

    /** Заголовок CSV-файла. */
    private static final String HEADER =
            "type;article;name;category;price;stock;warrantyMonths;discontinuedDate";

    /** Загружает CSV из файла в файловой системе (для GUI) */
    public CsvLoadResult load(Path file) throws IOException {
        return load(file, null);
    }

    /**
     * Загружает CSV из файла с отчётом о прогрессе и возможностью отмены.
     * @param listener может быть null
     * @throws CancellationException если слушатель запросил отмену
     */
    public CsvLoadResult load(Path file, ProgressListener listener) throws IOException {
        long total = Files.size(file);
        try (CountingInputStream in = new CountingInputStream(Files.newInputStream(file));
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return loadFromReader(reader, in, total, listener);
        }
    }

    /** Загружает CSV из ресурса classpath (для тестов и встроенного примера) */
    public CsvLoadResult loadFromResource(String resourcePath) throws IOException {
        return loadFromResource(resourcePath, null);
    }

    /** То же с отчётом о прогрессе (общий размер неизвестен, поэтому totalBytes = -1). */
    public CsvLoadResult loadFromResource(String resourcePath, ProgressListener listener) throws IOException {
        try (InputStream raw = CsvLoader.class.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                throw new IOException("Ресурс не найден: " + resourcePath);
            }
            try (CountingInputStream in = new CountingInputStream(raw);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return loadFromReader(reader, in, -1, listener);
            }
        }
    }

    /** Общая логика чтения */
    private CsvLoadResult loadFromReader(BufferedReader reader, CountingInputStream counter,
                                         long totalBytes, ProgressListener listener) throws IOException {
        List<Product> products = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        String line = reader.readLine(); // заголовок
        int lineNumber = 1;
        if (line == null) {
            return new CsvLoadResult(products, errors);
        }

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (listener != null && (lineNumber & (PROGRESS_EVERY_LINES - 1)) == 0
                    && !listener.onProgress(counter.getCount(), totalBytes)) {
                throw new CancellationException("Загрузка отменена");
            }
            if (line.isBlank()) {
                continue;
            }
            try {
                Product product = parseLine(line, lineNumber);
                products.add(product);
            } catch (CsvLoadException e) {
                errors.add(e.toString());
            }
        }

        return new CsvLoadResult(products, errors);
    }

    /**
     * Разбирает одну строку CSV и создаёт объект нужного типа
     * Может бросить CsvLoadException с кодом ошибки и номером строки
     */
    private Product parseLine(String line, int lineNumber) throws CsvLoadException {
        String[] parts = line.split(SEPARATOR, -1);

        if (parts.length != EXPECTED_FIELDS) {
            throw new CsvLoadException(
                    CsvLoadException.ErrorCode.WRONG_FIELD_COUNT,
                    lineNumber,
                    "Ожидалось " + EXPECTED_FIELDS + " колонок, получено " + parts.length
            );
        }

        String type = parts[0].trim();
        int article = parseInt(parts[1], "article", lineNumber);
        String name = requireNonBlank(parts[2], "name", lineNumber);
        String category = requireNonBlank(parts[3], "category", lineNumber);
        double price = parseDouble(parts[4], "price", lineNumber);
        int stock = parseInt(parts[5], "stock", lineNumber);

        switch (type) {
            case "BASE":
                return new Product(article, name, category, price, stock);

            case "WARRANTY": {
                int warrantyMonths = parseInt(parts[6], "warrantyMonths", lineNumber);
                return new WarrantyProduct(article, name, category, price, stock, warrantyMonths);
            }

            case "DISCONTINUED": {
                LocalDate discontinuedDate = parseDate(parts[7], "discontinuedDate", lineNumber);
                return new DiscontinuedProduct(article, name, category, price, stock, discontinuedDate);
            }

            default:
                throw new CsvLoadException(
                        CsvLoadException.ErrorCode.UNKNOWN_TYPE,
                        lineNumber,
                        "Неизвестный тип товара: \"" + type + "\""
                );
        }
    }

    /** Сохраняет список товаров в CSV-файл (для GUI) */
    public void save(List<Product> products, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeAll(products, writer);
        }
    }

    /** Общая логика записи */
    private void writeAll(List<Product> products, BufferedWriter writer) throws IOException {
        writer.write(HEADER);
        writer.newLine();

        for (Product p : products) {
            String line = toCsvLine(p);
            writer.write(line);
            writer.newLine();
        }
    }

    /** Преобразует один товар в строку CSV */
    private String toCsvLine(Product p) {
        if (p instanceof DiscontinuedProduct dp) {
            return String.join(SEPARATOR,
                    "DISCONTINUED",
                    String.valueOf(dp.getArticle()),
                    dp.getName(),
                    dp.getCategory(),
                    String.valueOf(dp.getPrice()),
                    String.valueOf(dp.getStock()),
                    "",                                       // warrantyMonths — пусто
                    dp.getDiscontinuedDate().toString()       // YYYY-MM-DD
            );
        } else if (p instanceof WarrantyProduct wp) {
            return String.join(SEPARATOR,
                    "WARRANTY",
                    String.valueOf(wp.getArticle()),
                    wp.getName(),
                    wp.getCategory(),
                    String.valueOf(wp.getPrice()),
                    String.valueOf(wp.getStock()),
                    String.valueOf(wp.getWarrantyMonths()),
                    ""                                        // discontinuedDate — пусто
            );
        } else {
            return String.join(SEPARATOR,
                    "BASE",
                    String.valueOf(p.getArticle()),
                    p.getName(),
                    p.getCategory(),
                    String.valueOf(p.getPrice()),
                    String.valueOf(p.getStock()),
                    "",
                    ""
            );
        }
    }

    private int parseInt(String value, String fieldName, int lineNumber) throws CsvLoadException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvLoadException(
                    CsvLoadException.ErrorCode.BAD_NUMBER,
                    lineNumber,
                    "Не удалось распарсить " + fieldName + ": \"" + value + "\"",
                    e
            );
        }
    }

    private double parseDouble(String value, String fieldName, int lineNumber) throws CsvLoadException {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvLoadException(
                    CsvLoadException.ErrorCode.BAD_NUMBER,
                    lineNumber,
                    "Не удалось распарсить " + fieldName + ": \"" + value + "\"",
                    e
            );
        }
    }

    private LocalDate parseDate(String value, String fieldName, int lineNumber) throws CsvLoadException {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CsvLoadException(
                    CsvLoadException.ErrorCode.BAD_DATE,
                    lineNumber,
                    "Не удалось распарсить " + fieldName + ": \"" + value + "\"",
                    e
            );
        }
    }

    private String requireNonBlank(String value, String fieldName, int lineNumber) throws CsvLoadException {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new CsvLoadException(
                    CsvLoadException.ErrorCode.MISSING_FIELD,
                    lineNumber,
                    "Поле " + fieldName + " не может быть пустым"
            );
        }
        return trimmed;
    }

    /** Считает байты, прошедшие через поток, — для расчёта прогресса. */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long getCount() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) count += n;
            return n;
        }
    }
}

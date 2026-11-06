package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.async.LoadTask;
import org.example.io.CsvLoadResult;
import org.example.io.CsvLoader;
import org.example.model.DiscontinuedProduct;
import org.example.model.Editable;
import org.example.model.Product;
import org.example.model.WarrantyProduct;
import org.example.structure.BTreeStore;
import org.example.structure.TreeStats;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Главное окно приложения «Каталог магазина».
 * Основное хранилище — BTreeStore (B-дерево порядка 4).
 * Фоновая загрузка через Task с прогрессом.
 */
public class MainView {

    private final Stage stage;

    /** Основное хранилище — B-дерево. */
    private BTreeStore<Integer, Product> tree = new BTreeStore<>();

    /** Список для отображения в таблице (зеркало дерева). */
    private final ObservableList<Product> products = FXCollections.observableArrayList();

    private final TableView<Product> table = new TableView<>();
    private final CsvLoader csvLoader = new CsvLoader();

    private Button editBtn;
    private Button deleteBtn;

    public MainView(Stage stage) {
        this.stage = stage;
        buildUI();
    }

    private void buildUI() {
        table.setItems(products);

        TableColumn<Product, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(p.getClass().getSimpleName());
        });
        typeCol.setPrefWidth(140);

        TableColumn<Product, Integer> articleCol = new TableColumn<>("Артикул");
        articleCol.setCellValueFactory(new PropertyValueFactory<>("article"));
        articleCol.setPrefWidth(80);

        TableColumn<Product, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<Product, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(140);

        TableColumn<Product, Double> priceCol = new TableColumn<>("Цена");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(100);

        TableColumn<Product, Integer> stockCol = new TableColumn<>("Остаток");
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stock"));
        stockCol.setPrefWidth(80);

        // --- ДОБАВЛЕННЫЕ КОЛОНКИ ---

        TableColumn<Product, String> warrantyCol = new TableColumn<>("Гарантия (мес.)");
        warrantyCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            String text = (p instanceof WarrantyProduct wp)
                    ? String.valueOf(wp.getWarrantyMonths())
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        warrantyCol.setPrefWidth(120);

        TableColumn<Product, String> discontinuedCol = new TableColumn<>("Снят с продажи");
        discontinuedCol.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            String text = (p instanceof DiscontinuedProduct dp && dp.getDiscontinuedDate() != null)
                    ? dp.getDiscontinuedDate().toString()
                    : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        discontinuedCol.setPrefWidth(130);

        // --- ДОБАВЛЯЕМ ВСЕ КОЛОНКИ В ТАБЛИЦУ ---
        table.getColumns().addAll(typeCol, articleCol, nameCol, categoryCol,
                priceCol, stockCol, warrantyCol, discontinuedCol);

        table.setPlaceholder(new Label("Нет данных. Нажмите «Загрузить пример»."));

        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateButtonsState()
        );

        Button loadExampleBtn = new Button("Загрузить пример");
        Button loadBtn = new Button("Загрузить из CSV");
        Button saveBtn = new Button("Сохранить в CSV");
        Button addBtn = new Button("Добавить");
        editBtn = new Button("Изменить");
        deleteBtn = new Button("Удалить");
        Button statsBtn = new Button("Статистика");
        Button showTreeBtn = new Button("Показать дерево");
        Button generateBtn = new Button("Сгенерировать N");

        loadExampleBtn.setOnAction(e -> loadFromResource("/products.csv"));
        loadBtn.setOnAction(e -> loadFromFileChooser());
        saveBtn.setOnAction(e -> saveToFileChooser());
        addBtn.setOnAction(e -> onAdd());
        editBtn.setOnAction(e -> onEdit());
        deleteBtn.setOnAction(e -> onDelete());
        statsBtn.setOnAction(e -> onShowStats());
        showTreeBtn.setOnAction(e -> onShowTree());
        generateBtn.setOnAction(e -> onGenerate());

        HBox buttons = new HBox(10,
                loadExampleBtn, loadBtn, saveBtn, addBtn, editBtn, deleteBtn,
                statsBtn, showTreeBtn, generateBtn);
        buttons.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(table);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 1300, 600); // Расширил окно под новые колонки
        stage.setTitle("Каталог магазина (B-дерево)");
        stage.setScene(scene);

        updateButtonsState();
    }

    public void show() {
        stage.show();
    }

    private void refreshTable() {
        products.setAll(tree.getAll());
    }

    private void updateButtonsState() {
        Product selected = table.getSelectionModel().getSelectedItem();
        boolean isEditable = selected instanceof Editable;
        editBtn.setDisable(!isEditable);
        deleteBtn.setDisable(selected == null);
    }

    // ============================================================
    //                        ДОБАВЛЕНИЕ
    // ============================================================

    private void onAdd() {
        ProductDialog dialog = new ProductDialog(stage, null);
        Optional<Product> result = dialog.showAndWait();
        if (result.isPresent()) {
            tree.add(result.get());
            refreshTable();
            showInfo("Товар добавлен: " + result.get().getName() +
                    "\nВсего в дереве: " + tree.size());
        }
    }

    // ============================================================
    //                       РЕДАКТИРОВАНИЕ
    // ============================================================

    private void onEdit() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!(selected instanceof Editable)) {
            showError("Нельзя редактировать",
                    "Товар «" + selected.getName() + "» доступен только для чтения.");
            return;
        }

        ProductDialog dialog = new ProductDialog(stage, selected);
        Optional<Product> result = dialog.showAndWait();
        if (result.isPresent()) {
            tree.removeByKey(selected.getArticle());
            tree.add(result.get());
            refreshTable();
            showInfo("Товар изменён: " + result.get().getName());
        }
    }

    // ============================================================
    //                         УДАЛЕНИЕ
    // ============================================================

    private void onDelete() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Подтверждение удаления");
        confirm.setHeaderText("Удалить товар?");
        confirm.setContentText("Товар: " + selected.getName() +
                " (артикул " + selected.getArticle() + ")");

        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            boolean removed = tree.removeByKey(selected.getArticle());
            if (removed) {
                refreshTable();
                showInfo("Товар удалён: " + selected.getName() +
                        "\nВсего в дереве: " + tree.size());
            } else {
                showError("Ошибка", "Не удалось удалить товар.");
            }
        }
    }

    // ============================================================
    //                       СТАТИСТИКА
    // ============================================================

    private void onShowStats() {
        TreeStats stats = tree.getStats();
        String message = String.format(
                "Статистика B-дерева:%n" +
                        "  Всего узлов: %d%n" +
                        "  Листьев: %d%n" +
                        "  Внутренних: %d%n" +
                        "  Высота: %d%n" +
                        "  Среднее ключей в узле: %.2f%n" +
                        "  Полных узлов: %d%n" +
                        "  Неполных узлов: %d%n" +
                        "  Всего элементов: %d",
                stats.getTotalNodes(),
                stats.getLeafNodes(),
                stats.getInternalNodes(),
                stats.getHeight(),
                stats.getAvgKeysPerNode(),
                stats.getFullNodes(),
                stats.getNotFullNodes(),
                stats.getTotalKeys()
        );
        showInfo(message);
    }

    // ============================================================
    //                       ВИЗУАЛИЗАЦИЯ
    // ============================================================

    private void onShowTree() {
        TreeViewWindow window = new TreeViewWindow(stage, tree);
        window.show();
    }

    // ============================================================
    //                       ГЕНЕРАЦИЯ ДАННЫХ
    // ============================================================

    private void onGenerate() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.initOwner(stage);
        dialog.setTitle("Генерация данных");
        dialog.setHeaderText("Сколько товаров сгенерировать?");
        dialog.setContentText("Число:");

        Optional<String> input = dialog.showAndWait();
        if (!input.isPresent()) return;

        try {
            int count = Integer.parseInt(input.get().trim());
            if (count <= 0) {
                showError("Ошибка", "Число должно быть положительным.");
                return;
            }
            if (count > 100000) {
                showError("Ошибка", "Максимум 100000 товаров за раз.");
                return;
            }

            long start = System.currentTimeMillis();
            tree.generateAndAdd(count);
            long elapsed = System.currentTimeMillis() - start;

            refreshTable();

            showInfo("Сгенерировано: " + count + " товаров" +
                    "\nВремя: " + elapsed + " мс" +
                    "\nВсего в дереве: " + tree.size());

        } catch (NumberFormatException ex) {
            showError("Ошибка", "Неверный формат числа: " + input.get());
        }
    }

    // ============================================================
    //                     ЗАГРУЗКА ИЗ CSV (с Task)
    // ============================================================

    private void loadFromResource(String resourcePath) {
        loadResourceWithProgress(resourcePath);
    }

    private void loadFromFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите CSV-файл");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        loadFileWithProgress(file);
    }

    /** Загрузка из ресурса в фоне. */
    private void loadResourceWithProgress(String resourcePath) {
        Task<CsvLoadResult> task = new Task<CsvLoadResult>() {
            @Override
            protected CsvLoadResult call() throws Exception {
                updateMessage("Загрузка встроенного примера...");
                updateProgress(0, 1);
                CsvLoadResult result = csvLoader.loadFromResource(resourcePath);
                updateProgress(1, 1);
                updateMessage("Загружено: " + result.getProducts().size() + " товаров");
                return result;
            }
        };

        showTaskDialog(task, "Загрузка примера", "Пример загружен");
    }

    /** Загрузка из файла в фоне через LoadTask. */
    private void loadFileWithProgress(File file) {
        LoadTask task = new LoadTask(file.toPath());
        showTaskDialog(task, "Загрузка файла: " + file.getName(),
                "Файл загружен: " + file.getName());
    }

    /**
     * Показывает диалог с прогресс-баром и запускает Task в фоне.
     */
    private void showTaskDialog(Task<CsvLoadResult> task, String title, String successMessage) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setPrefWidth(350);

        Label statusLabel = new Label();
        statusLabel.textProperty().bind(task.messageProperty());

        Button cancelBtn = new Button("Отмена");
        cancelBtn.setOnAction(e -> task.cancel());

        VBox content = new VBox(10, statusLabel, progressBar, cancelBtn);
        content.setPadding(new Insets(15));

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setScene(new Scene(content, 420, 150));
        dialog.setOnCloseRequest(e -> task.cancel());

        task.setOnSucceeded(e -> {
            dialog.close();
            CsvLoadResult result = task.getValue();
            if (result != null) {
                applyLoadResult(result, successMessage);
            }
        });

        task.setOnFailed(e -> {
            dialog.close();
            Throwable ex = task.getException();
            showError("Ошибка загрузки",
                    ex != null ? ex.getMessage() : "Неизвестная ошибка");
        });

        task.setOnCancelled(e -> {
            dialog.close();
            showInfo("Загрузка отменена");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        dialog.show();
    }

    private void applyLoadResult(CsvLoadResult result, String successMessage) {
        tree = new BTreeStore<>();
        for (Product p : result.getProducts()) {
            tree.add(p);
        }
        refreshTable();

        if (result.isSuccess()) {
            showInfo(successMessage + "\nЗагружено: " + result.getProducts().size() +
                    " товаров\nВсего в дереве: " + tree.size());
        } else {
            showError(
                    "Загружено с ошибками",
                    successMessage + "\n" +
                            "Успешно: " + result.getProducts().size() + "\n" +
                            "Пропущено строк: " + result.getSkippedCount() + "\n\n" +
                            String.join("\n", result.getErrors())
            );
        }
    }

    // ============================================================
    //                     СОХРАНЕНИЕ В CSV
    // ============================================================

    private void saveToFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить в CSV");
        fc.setInitialFileName("products_export.csv");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));

        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            List<Product> toSave = tree.getAll();
            csvLoader.save(toSave, file.toPath());
            showInfo("Файл сохранён: " + file.getName() +
                    "\nСохранено: " + toSave.size() + " товаров");
        } catch (Exception ex) {
            showError("Ошибка сохранения файла", ex.getMessage());
        }
    }

    // ============================================================
    //                        ДИАЛОГИ
    // ============================================================

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
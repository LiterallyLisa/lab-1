package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.io.CsvLoadResult;
import org.example.io.CsvLoader;
import org.example.model.Editable;
import org.example.model.Product;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Главное окно приложения «Каталог магазина».
 */
public class MainView {

    private final Stage stage;
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final TableView<Product> table = new TableView<>();
    private final CsvLoader csvLoader = new CsvLoader();

    // Кнопки, состояние которых зависит от выделения
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

        table.getColumns().addAll(typeCol, articleCol, nameCol, categoryCol, priceCol, stockCol);
        table.setPlaceholder(new Label("Нет данных. Нажмите «Загрузить пример»."));

        // Слушатель выделения: при выборе строки обновляем состояние кнопок
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateButtonsState()
        );

        //Кнопки
        Button loadExampleBtn = new Button("Загрузить пример");
        Button loadBtn = new Button("Загрузить из CSV");
        Button saveBtn = new Button("Сохранить в CSV");
        Button addBtn = new Button("Добавить");
        editBtn = new Button("Изменить");
        deleteBtn = new Button("Удалить");

        loadExampleBtn.setOnAction(e -> loadFromResource("/products.csv"));
        loadBtn.setOnAction(e -> loadFromFileChooser());
        saveBtn.setOnAction(e -> saveToFileChooser());
        addBtn.setOnAction(e -> onAdd());
        editBtn.setOnAction(e -> onEdit());
        deleteBtn.setOnAction(e -> onDelete());

        HBox buttons = new HBox(10,
                loadExampleBtn, loadBtn, saveBtn, addBtn, editBtn, deleteBtn);
        buttons.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(table);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 900, 500);
        stage.setTitle("Каталог магазина");
        stage.setScene(scene);

        // Изначально кнопки редактирования/удаления выключены
        updateButtonsState();
    }

    public void show() {
        stage.show();
    }

    //ОБНОВЛЕНИЕ СОСТОЯНИЯ КНОПОК
    /**
     * Обновляет доступность кнопок в зависимости от выделения.
     * Кнопка «Изменить» активна только если выделенный объект реализует Editable.
     */
    private void updateButtonsState() {
        Product selected = table.getSelectionModel().getSelectedItem();
        boolean isEditable = selected instanceof Editable;
        editBtn.setDisable(!isEditable);
        deleteBtn.setDisable(selected == null);
    }


    //ДОБАВЛЕНИЕ
    private void onAdd() {
        ProductDialog dialog = new ProductDialog(stage, null);
        Optional<Product> result = dialog.showAndWait();
        if (result.isPresent()) {
            products.add(result.get());
            showInfo("Товар добавлен: " + result.get().getName());
        }
    }

    //РЕДАКТИРОВАНИЕ
    private void onEdit() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!(selected instanceof Editable)) {
            showError("Нельзя редактировать",
                    "Товар «" + selected.getName() + "» доступен только для чтения.");
            return;
        }

        // Открываем диалог с текущими данными
        ProductDialog dialog = new ProductDialog(stage, selected);
        Optional<Product> result = dialog.showAndWait();
        if (result.isPresent()) {
            int index = products.indexOf(selected);
            products.set(index, result.get());
            table.getSelectionModel().select(index);
            showInfo("Товар изменён: " + result.get().getName());
        }
    }

    //УДАЛЕНИЕ

    private void onDelete() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Подтверждение удаления");
        confirm.setHeaderText("Удалить товар?");
        confirm.setContentText("Товар: " + selected.getName() +
                " (артикул " + selected.getArticle() + ")");

        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            products.remove(selected);
            showInfo("Товар удалён: " + selected.getName());
        }
    }

    //ЗАГРУЗКА ИЗ CSV
    private void loadFromResource(String resourcePath) {
        try {
            CsvLoadResult result = csvLoader.loadFromResource(resourcePath);
            applyLoadResult(result, "Пример загружен");
        } catch (Exception ex) {
            showError("Ошибка загрузки ресурса", ex.getMessage());
        }
    }

    private void loadFromFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите CSV-файл");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;
        try {
            CsvLoadResult result = csvLoader.load(file.toPath());
            applyLoadResult(result, "Файл загружен: " + file.getName());
        } catch (Exception ex) {
            showError("Ошибка загрузки файла", ex.getMessage());
        }
    }

    private void applyLoadResult(CsvLoadResult result, String successMessage) {
        products.setAll(result.getProducts());
        if (result.isSuccess()) {
            showInfo(successMessage + "\nЗагружено: " + result.getProducts().size() + " товаров");
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


    //СОХРАНЕНИЕ В CSV
    private void saveToFileChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить в CSV");
        fc.setInitialFileName("products_export.csv");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));

        File file = fc.showSaveDialog(stage);
        if (file == null) return;

        try {
            List<Product> toSave = products;
            csvLoader.save(toSave, file.toPath());
            showInfo("Файл сохранён: " + file.getName() +
                    "\nСохранено: " + toSave.size() + " товаров");
        } catch (Exception ex) {
            showError("Ошибка сохранения файла", ex.getMessage());
        }
    }


    //ДИАЛОГИ
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
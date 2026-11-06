package org.example.gui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.example.model.Product;
import org.example.structure.BTreeNode;
import org.example.structure.BTreeStore;

import java.util.Set;

/**
 * Отдельное (немодальное) окно для визуализации B-дерева.
 * Главное окно вызывает update(...) после каждой операции, поэтому картинка всегда актуальна,
 * даже если дерево было заменено целиком (загрузка, генерация).
 */
public class TreeViewWindow {

    private final Stage window;
    private final TreeCanvas treeCanvas;
    private final Label info = new Label();

    /** Последнее показанное дерево — для кнопки «Обновить». */
    private BTreeStore<Integer, Product> tree;

    public TreeViewWindow(Stage owner) {
        this.window = new Stage();
        this.window.initOwner(owner);
        this.window.setTitle("Визуализация B-дерева");

        this.treeCanvas = new TreeCanvas(900, 600);

        // Кнопка «Обновить» — перерисовка без подсветки
        Button refreshBtn = new Button("Обновить");
        refreshBtn.setOnAction(e -> update(tree, Set.of()));

        // Кнопка «Закрыть»
        Button closeBtn = new Button("Закрыть");
        closeBtn.setOnAction(e -> window.close());

        HBox buttons = new HBox(10, refreshBtn, closeBtn);
        buttons.setPadding(new Insets(10));

        info.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(info);
        root.setCenter(new ScrollPane(treeCanvas.getCanvas()));
        root.setBottom(buttons);

        window.setScene(new Scene(root, 920, 680));
    }

    public boolean isShowing() {
        return window.isShowing();
    }

    /** Показать окно (вынести на передний план) и сразу нарисовать дерево. */
    public void show(BTreeStore<Integer, Product> tree, Set<BTreeNode<?, ?>> highlighted) {
        window.show();
        window.toFront();
        update(tree, highlighted);
    }

    /** Перерисовать окно для (возможно, нового) дерева с подсветкой указанных узлов. */
    public void update(BTreeStore<Integer, Product> tree, Set<BTreeNode<?, ?>> highlighted) {
        this.tree = tree;
        String note = treeCanvas.draw(tree, highlighted);

        StringBuilder text = new StringBuilder("Элементов в дереве: " + tree.size());
        if (!highlighted.isEmpty()) {
            text.append("   |   Подсвечено узлов: ").append(highlighted.size());
        }
        if (!note.isEmpty()) {
            text.append("   |   ").append(note);
        }
        info.setText(text.toString());
    }
}

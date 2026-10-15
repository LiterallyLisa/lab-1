package org.example.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.example.structure.BTreeNode;
import org.example.structure.BTreeStore;

/**
 * Панель для визуализации B-дерева.
 * Рисует узлы (прямоугольники с ключами) и связи между ними.
 */
public class TreeCanvas {

    /** Размеры. */
    private static final double NODE_WIDTH = 60;
    private static final double NODE_HEIGHT = 30;
    private static final double LEVEL_HEIGHT = 80;
    private static final double HORIZONTAL_SPACING = 20;

    private final Canvas canvas;

    public TreeCanvas(double width, double height) {
        this.canvas = new Canvas(width, height);
    }

    /** Возвращает Canvas — чтобы встроить в layout. */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Рисует дерево.
     * @param tree дерево для отрисовки
     */
    public void draw(BTreeStore<?, ?> tree) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (tree == null || tree.size() == 0) {
            g.setFill(Color.GRAY);
            g.setFont(Font.font(14));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("Дерево пустое", canvas.getWidth() / 2, canvas.getHeight() / 2);
            return;
        }

        BTreeNode<?, ?> root = tree.getRoot();
        double startX = canvas.getWidth() / 2;
        double startY = 40;
        drawNode(g, root, startX, startY, canvas.getWidth() / 2);
    }

    /**
     * Рисует узел и рекурсивно его детей.
     */
    private void drawNode(GraphicsContext g, BTreeNode<?, ?> node, double x, double y, double width) {
        if (node == null) return;

        int keyCount = node.getKeyCount();
        if (keyCount == 0) return;

        // Рисуем прямоугольник узла
        double totalWidth = keyCount * NODE_WIDTH;
        double nodeX = x - totalWidth / 2;
        double nodeY = y;

        g.setFill(Color.LIGHTBLUE);
        g.setStroke(Color.DARKBLUE);
        g.setLineWidth(2);
        g.fillRect(nodeX, nodeY, totalWidth, NODE_HEIGHT);
        g.strokeRect(nodeX, nodeY, totalWidth, NODE_HEIGHT);

        // Рисуем ключи
        g.setFill(Color.BLACK);
        g.setFont(Font.font(12));
        g.setTextAlign(TextAlignment.CENTER);

        for (int i = 0; i < keyCount; i++) {
            double cellX = nodeX + i * NODE_WIDTH;
            g.strokeLine(cellX, nodeY, cellX, nodeY + NODE_HEIGHT);
            g.fillText(
                    String.valueOf(node.getKey(i)),
                    cellX + NODE_WIDTH / 2,
                    nodeY + NODE_HEIGHT / 2 + 4
            );
        }

        // Рисуем детей
        if (!node.isLeaf()) {
            double childWidth = width / 2;
            for (int i = 0; i <= keyCount; i++) {
                BTreeNode<?, ?> child = node.getChild(i);
                if (child == null) continue;

                double childX = x + (i - keyCount / 2.0) * width / (keyCount + 1);
                double childY = y + LEVEL_HEIGHT;

                // Линия от узла к ребёнку
                g.setStroke(Color.GRAY);
                g.setLineWidth(1);
                g.strokeLine(x, nodeY + NODE_HEIGHT, childX, childY);

                // Рекурсивно рисуем ребёнка
                drawNode(g, child, childX, childY, childWidth);
            }
        }
    }
}
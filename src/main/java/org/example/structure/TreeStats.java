package org.example.structure;

/**
 * Статистика B-дерева.
 * Immutable — после создания не меняется.
 */
public class TreeStats {
    private final int totalNodes;
    private final int leafNodes;
    private final int internalNodes;
    private final int height;
    private final int totalKeys;
    private final int[] keysHistogram;   // keysHistogram[k] = сколько узлов содержит ровно k ключей

    // Производные величины
    private final int fullNodes;
    private final int notFullNodes;
    private final double avgKeysPerNode;
    private final double fillFactor;

    public TreeStats(int totalNodes, int leafNodes, int internalNodes, int height,
                     int totalKeys, int[] keysHistogram) {
        this.totalNodes = totalNodes;
        this.leafNodes = leafNodes;
        this.internalNodes = internalNodes;
        this.height = height;
        this.totalKeys = totalKeys;
        this.keysHistogram = keysHistogram.clone();

        this.fullNodes = keysHistogram[keysHistogram.length - 1];
        this.notFullNodes = totalNodes - fullNodes;
        this.avgKeysPerNode = totalNodes > 0 ? (double) totalKeys / totalNodes : 0;
        int capacity = totalNodes * (keysHistogram.length - 1);
        this.fillFactor = capacity > 0 ? (double) totalKeys / capacity : 0;
    }

    /** Статистика пустого дерева: ни одного узла. */
    public static TreeStats empty() {
        return new TreeStats(0, 0, 0, 0, 0, new int[BTreeNode.MAX_KEYS + 1]);
    }

    public int getTotalNodes() { return totalNodes; }
    public int getLeafNodes() { return leafNodes; }
    public int getInternalNodes() { return internalNodes; }
    public int getHeight() { return height; }
    public double getAvgKeysPerNode() { return avgKeysPerNode; }
    public int getFullNodes() { return fullNodes; }
    public int getNotFullNodes() { return notFullNodes; }
    public int getTotalKeys() { return totalKeys; }

    /** Доля занятых ячеек: ключей / (узлов * MAX_KEYS), от 0 до 1. */
    public double getFillFactor() { return fillFactor; }

    /** Копия гистограммы: элемент k — число узлов с ровно k ключами. */
    public int[] getKeysHistogram() { return keysHistogram.clone(); }

    @Override
    public String toString() {
        return "TreeStats{" +
                "totalNodes=" + totalNodes +
                ", leafNodes=" + leafNodes +
                ", internalNodes=" + internalNodes +
                ", height=" + height +
                ", avgKeysPerNode=" + String.format("%.2f", avgKeysPerNode) +
                ", fillFactor=" + String.format("%.1f%%", fillFactor * 100) +
                ", fullNodes=" + fullNodes +
                ", notFullNodes=" + notFullNodes +
                ", totalKeys=" + totalKeys +
                '}';
    }
}

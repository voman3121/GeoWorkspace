package model;

public class Node {
    private long id;

    private int gridX;
    private int gridY;

    private double screenX;
    private double screenY;

    private String label;

    private int degree;
    private String adjacentNodes;

    public Node(long id,
                int gridX,
                int gridY,
                double screenX,
                double screenY,
                String label,
                int degree,
                String adjacentNodes) {

        this.id = id;
        this.gridX = gridX;
        this.gridY = gridY;
        this.screenX = screenX;
        this.screenY = screenY;
        this.label = label;
        this.degree = degree;
        this.adjacentNodes = adjacentNodes;
    }

    public Node(int gridX, int gridY, double screenX, double screenY, String label) {
        this(0, gridX, gridY, screenX, screenY, label, 0, "");
    }

    public long getId() {
        return id;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public double getScreenX() {
        return screenX;
    }

    public double getScreenY() {
        return screenY;
    }

    public String getLabel() {
        return label;
    }

    public int getDegree() {
        return degree;
    }

    public String getAdjacentNodes() {
        return adjacentNodes;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setDegree(int degree) {
        this.degree = degree;
    }

    public void setAdjacentNodes(String adjacentNodes) {
        this.adjacentNodes = adjacentNodes;
    }
}
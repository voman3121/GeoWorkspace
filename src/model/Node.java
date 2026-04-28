package model;

public class Node {
    private long id;
    private double x;
    private double y;
    private String label;

    public Node(long id, double x, double y, String label) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.label = label;
    }

    public Node(double x, double y, String label) {
        this(0, x, y, label);
    }

    public long getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public String getLabel() { return label; }

    public void setId(long id) { this.id = id; }
}
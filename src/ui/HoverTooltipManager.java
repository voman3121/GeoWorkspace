package ui;

import model.Edge;
import model.Node;

import java.util.List;
import java.util.stream.Collectors;

public class HoverTooltipManager {

    public static String buildNodeTooltip(Node node, List<Node> adjacent) {
        String neighbors = adjacent.isEmpty()
                ? "none"
                : adjacent.stream().map(Node::getLabel).collect(Collectors.joining(", "));

        return "<html><div style='font-family:monospace; font-size:11px; padding:2px 4px;'>"
                + "<b style='color:#00d4b4'>NODE " + node.getLabel() + "</b><br>"
                + "<span style='color:#aaaaaa'>id&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span> " + node.getId() + "<br>"
                + "<span style='color:#aaaaaa'>x, y&nbsp;&nbsp;&nbsp;</span> " + (int)node.getX() + ", " + (int)node.getY() + "<br>"
                + "<span style='color:#aaaaaa'>degree&nbsp;</span> " + adjacent.size() + " / 4<br>"
                + "<span style='color:#aaaaaa'>adj&nbsp;&nbsp;&nbsp;&nbsp;</span> " + neighbors
                + "</div></html>";
    }

    public static String buildEdgeTooltip(Edge edge, Node a, Node b) {
        double length = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
        return "<html><div style='font-family:monospace; font-size:11px; padding:2px 4px;'>"
                + "<b style='color:#00d4b4'>EDGE #" + edge.getId() + "</b><br>"
                + "<span style='color:#aaaaaa'>from&nbsp;&nbsp;</span> " + a.getLabel() + " (id " + a.getId() + ")<br>"
                + "<span style='color:#aaaaaa'>to&nbsp;&nbsp;&nbsp;&nbsp;</span> " + b.getLabel() + " (id " + b.getId() + ")<br>"
                + "<span style='color:#aaaaaa'>length </span> " + String.format("%.1f", length) + " px"
                + "</div></html>";
    }
}

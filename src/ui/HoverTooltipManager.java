package ui;

import model.Edge;
import model.Node;

public class HoverTooltipManager {

    public static String buildNodeTooltip(Node node) {
        return "<html><div style='font-family:monospace; font-size:11px; padding:2px 4px;'>"
                + "<b style='color:#00d4b4'>NODE " + node.getLabel() + "</b><br>"
                + "<span style='color:#aaaaaa'>id&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span> " + node.getId() + "<br>"
                + "<span style='color:#aaaaaa'>grid&nbsp;&nbsp;&nbsp;</span> (" + node.getGridX() + ", " + node.getGridY() + ")<br>"
                + "<span style='color:#aaaaaa'>degree&nbsp;</span> " + node.getDegree() + " / 4<br>"
                + "<span style='color:#aaaaaa'>adj&nbsp;&nbsp;&nbsp;&nbsp;</span> "
                + (node.getAdjacentNodes().isBlank() ? "none" : node.getAdjacentNodes())
                + "</div></html>";
    }

    public static String buildEdgeTooltip(Edge edge, Node a, Node b) {
        return "<html><div style='font-family:monospace; font-size:11px; padding:2px 4px;'>"
                + "<b style='color:#00d4b4'>EDGE #" + edge.getId() + "</b><br>"
                + "<span style='color:#aaaaaa'>from&nbsp;&nbsp;</span> " + a.getLabel() + "<br>"
                + "<span style='color:#aaaaaa'>to&nbsp;&nbsp;&nbsp;&nbsp;</span> " + b.getLabel() + "<br>"
                + "<span style='color:#aaaaaa'>length </span> " + String.format("%.1f", edge.getLength()) + " px"
                + "</div></html>";
    }
}
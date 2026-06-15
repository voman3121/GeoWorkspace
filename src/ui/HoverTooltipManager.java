package ui;
import model.Node; import java.util.List;
public class HoverTooltipManager {
    public static String buildNodeTooltip(Node node,List<Node> adjacent){
        StringBuilder sb=new StringBuilder("<html><b style='color:#00c8ff'>"+node.getLabel()+"</b><br>");
        sb.append("id:").append(node.getId()).append("<br>pos:(").append((int)node.getX()).append(",").append((int)node.getY()).append(")<br>");
        sb.append("degree:").append(node.degree()).append("/4<br>adj:");
        if(adjacent.isEmpty())sb.append("none");
        else for(int i=0;i<adjacent.size();i++){if(i>0)sb.append(", ");sb.append(adjacent.get(i).getLabel());}
        sb.append("</html>");return sb.toString();}
}
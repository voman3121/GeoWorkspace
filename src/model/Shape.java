package model;

public class Shape {
    private long id;
    private String label, shapeType, extraData;
    private long[] nodeIds;
    private double area, perimeter;

    public Shape(long id,String label,String shapeType,long[] nodeIds,double area,double perimeter,String extraData){
        this.id=id;this.label=label;this.shapeType=shapeType;
        this.nodeIds=nodeIds;this.area=area;this.perimeter=perimeter;
        this.extraData=extraData!=null?extraData:"";
    }
    public Shape(String label,String shapeType,long[] nodeIds,double area,double perimeter,String extraData){
        this(0,label,shapeType,nodeIds,area,perimeter,extraData);
    }

    public long   getId()        {return id;}
    public String getLabel()     {return label;}
    public String getShapeType() {return shapeType;}
    public long[] getNodeIds()   {return nodeIds;}
    public double getArea()      {return area;}
    public double getPerimeter() {return perimeter;}
    public String getExtraData() {return extraData!=null?extraData:"";}
    public void   setId(long id) {this.id=id;}

    public static String encodeIds(long[] ids){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<ids.length;i++){if(i>0)sb.append(',');sb.append(ids[i]);}
        return sb.toString();
    }
    public static long[] decodeIds(String s){
        if(s==null||s.isBlank()) return new long[0];
        String[] parts=s.split(",");
        long[] ids=new long[parts.length];
        try{for(int i=0;i<parts.length;i++) ids[i]=Long.parseLong(parts[i].trim());}
        catch(NumberFormatException e){return new long[0];}
        return ids;
    }
}
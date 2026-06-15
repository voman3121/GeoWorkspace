package model;
public class Node {
    private long id; private double x,y; private String label;
    private long adj1,adj2,adj3,adj4;
    public Node(long id,double x,double y,String label,long adj1,long adj2,long adj3,long adj4){
        this.id=id;this.x=x;this.y=y;this.label=label;
        this.adj1=adj1;this.adj2=adj2;this.adj3=adj3;this.adj4=adj4;
    }
    public Node(double x,double y,String label){this(0,x,y,label,0,0,0,0);}
    public long getId(){return id;} public double getX(){return x;} public double getY(){return y;}
    public String getLabel(){return label;} public void setId(long id){this.id=id;}
    public long getAdj1(){return adj1;} public long getAdj2(){return adj2;}
    public long getAdj3(){return adj3;} public long getAdj4(){return adj4;}
    public int degree(){int d=0;if(adj1!=0)d++;if(adj2!=0)d++;if(adj3!=0)d++;if(adj4!=0)d++;return d;}
    public long[] adjacentIds(){long[]all={adj1,adj2,adj3,adj4};int c=degree();long[]r=new long[c];int i=0;for(long a:all)if(a!=0)r[i++]=a;return r;}
    public boolean hasNeighbour(long nid){return adj1==nid||adj2==nid||adj3==nid||adj4==nid;}
    public int firstFreeSlot(){if(adj1==0)return 1;if(adj2==0)return 2;if(adj3==0)return 3;if(adj4==0)return 4;return -1;}
}
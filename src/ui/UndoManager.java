package ui;
import java.util.ArrayDeque; import java.util.Deque;
public class UndoManager {
    public static class Operation {
        public final String description; public final Runnable undo,redo;
        public Operation(String d,Runnable u,Runnable r){description=d;undo=u;redo=r;}
    }
    private static final int MAX=10;
    private final Deque<Operation> undoStack=new ArrayDeque<>();
    private final Deque<Operation> redoStack=new ArrayDeque<>();
    public void push(Operation op){
        undoStack.push(op);
        if(undoStack.size()>MAX){ArrayDeque<Operation>tmp=new ArrayDeque<>(undoStack);tmp.pollLast();undoStack.clear();undoStack.addAll(tmp);}
        redoStack.clear();System.out.println("[UNDO] "+op.description);}
    public boolean canUndo(){return!undoStack.isEmpty();}
    public boolean canRedo(){return!redoStack.isEmpty();}
    public void undo(){if(!canUndo())return;Operation op=undoStack.pop();System.out.println("[UNDO] Undoing: "+op.description);op.undo.run();redoStack.push(op);}
    public void redo(){if(!canRedo())return;Operation op=redoStack.pop();System.out.println("[UNDO] Redoing: "+op.description);op.redo.run();undoStack.push(op);}
    public String peekUndo(){return undoStack.isEmpty()?null:undoStack.peek().description;}
    public String peekRedo(){return redoStack.isEmpty()?null:redoStack.peek().description;}
}
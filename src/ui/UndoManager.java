package ui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple undo/redo manager. Each operation is a pair of Runnables:
 * one that undoes it, one that redoes it.
 * Maximum 10 undo steps.
 */
public class UndoManager {

    public static class Operation {
        public final String  description;
        public final Runnable undo;
        public final Runnable redo;
        public Operation(String desc, Runnable undo, Runnable redo) {
            this.description = desc;
            this.undo = undo;
            this.redo = redo;
        }
    }

    private static final int MAX_HISTORY = 10;
    private final Deque<Operation> undoStack = new ArrayDeque<>();
    private final Deque<Operation> redoStack = new ArrayDeque<>();

    public void push(Operation op) {
        undoStack.push(op);
        if (undoStack.size() > MAX_HISTORY) {
            // Remove oldest (bottom of stack)
            ArrayDeque<Operation> temp = new ArrayDeque<>(undoStack);
            temp.pollLast();
            undoStack.clear();
            undoStack.addAll(temp);
        }
        redoStack.clear(); // new action clears redo history
        System.out.println("[UNDO] Pushed: " + op.description
                + " (stack size=" + undoStack.size() + ")");
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (!canUndo()) return;
        Operation op = undoStack.pop();
        System.out.println("[UNDO] Undoing: " + op.description);
        op.undo.run();
        redoStack.push(op);
    }

    public void redo() {
        if (!canRedo()) return;
        Operation op = redoStack.pop();
        System.out.println("[UNDO] Redoing: " + op.description);
        op.redo.run();
        undoStack.push(op);
    }

    public String peekUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().description;
    }
    public String peekRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().description;
    }
}
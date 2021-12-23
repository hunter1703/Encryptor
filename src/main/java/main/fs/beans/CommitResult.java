package main.fs.beans;

public class CommitResult {

    private final int orphaned;
    private final int dangling;

    public CommitResult(int orphaned, int dangling) {
        this.orphaned = orphaned;
        this.dangling = dangling;
    }

    public int getOrphaned() {
        return orphaned;
    }

    public int getDangling() {
        return dangling;
    }

}

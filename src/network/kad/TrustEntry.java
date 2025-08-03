package network.kad;

public class TrustEntry {
    int success = 0;
    int failure = 0;
    long lastInteraction = System.currentTimeMillis();

    public double getScore() {
        return (success + 1.0) / (success + failure + 2.0);
    }

    public void setScore(double score) {
        int total = 10;
        this.success = (int) Math.round(score * total);
        this.failure = total - this.success;
    }
}

package caves.generator;

/**
 * Provides locking mechanism for locking the chunks for preventing simultaneous access from main
 * and generator threads, while allowing multiple generator workers to access the chunk
 * asynchronously.
 * <p>
 * Note that all methods are synchronized.
 */
public class ChunkLock {
    private int generatorClaims;
    private boolean claimedFromMain;

    public synchronized void claimFromGenerator() {
        if (this.claimedFromMain) {
            try {
                this.wait();
            } catch (final InterruptedException ignored) {
            }
        }

        this.generatorClaims++;
    }

    public synchronized void claimFromMain() {
        if (this.generatorClaims > 0) {
            try {
                this.wait();
            } catch (final InterruptedException ignored) {
            }
        }

        this.claimedFromMain = true;
    }

    public synchronized void freeFromGenerator() {
        --this.generatorClaims;
        if (this.generatorClaims == 0) {
            this.notifyAll();
        }
    }

    public synchronized void freeFromMain() {
        this.claimedFromMain = false;
        this.notifyAll();
    }
}

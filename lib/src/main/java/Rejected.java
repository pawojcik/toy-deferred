class Rejected extends Exception {
    Rejected(Throwable cause) {
        super("The promise was rejected", cause);
    }
}

public class Main {
    public static void main(String[] args) {
        try {
            new cli.CommandDispatcher().dispatch(args);
        } catch (Exception exception) {
            System.err.println("fatal: " + exception.getMessage());
            System.exit(1);
        }
    }
}

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.err.println("Logs from your program will appear here!");

    // TODO: Uncomment the code below to pass the first stage
    
    final String command = args[0];
    
    switch (command) {
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");
    
        try {
          head.createNewFile();
          Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
          System.out.println("Initialized git directory");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      case "cat-file" -> {
        final String hash = args[1];
        final File objectFile = new File(".git/objects", hash);
    
        if (!objectFile.exists()) {
          System.out.println("Object not found: " + hash);
          return;
        }
    
        try {
          final byte[] content = Files.readAllBytes(objectFile.toPath());
          System.out.write(content);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }
}

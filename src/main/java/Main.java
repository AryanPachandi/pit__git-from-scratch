import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

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
        final String hash = args[2];
        // final File objectFile = new File(".git/objects", hash);
        String dirhash = hash.substring(0, 2);
        String filehash = hash.substring(2);
        File blobFile = new File("./.git/objects/" + dirhash + "/" + filehash);

        if (!blobFile.exists()) {
          System.out.println("Object not found: " + hash);
          return;
        }
    
        try {
          String blob =new BufferedReader(new InputStreamReader(new InflaterInputStream(new FileInputStream(blobFile)))).readLine();
          String content = blob.substring(blob.indexOf("\0") + 1);
          // final byte[] content = Files.readAllBytes(blobFile.toPath());
          System.out.print(content);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
        case "hash-object" -> {
                String fileName = args[2];
                try {
                    Path path = Paths.get(fileName);
                    String fileContent = Files.readString(path);
                    long fileSize = Files.size(path);

                    String header = "blob " + fileSize + "\0";
                    String combinedData = header + fileContent;

                    String hash = DigestUtils.sha1Hex(combinedData);
                    String blobPath = String.format(".git/objects/%s/%s",
                            hash.substring(0, 2),
                            hash.substring(2));

                    File blobFile = new File(blobPath);
                    blobFile.getParentFile().mkdirs();

                    try (DeflaterOutputStream out =
                                 new DeflaterOutputStream(new FileOutputStream(blobFile))) {
                        // Write the combined data (header + file content)
                        out.write(combinedData.getBytes());
                    }

                    System.out.println(hash);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
      default -> System.out.println("Unknown command: " + command);
    }
  }
}

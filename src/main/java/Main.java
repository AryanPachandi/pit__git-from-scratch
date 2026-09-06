import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class Main {
      static class PackObject {
        int type; // 1: commit, 2: tree, 3: blob, 4: tag, 6: OFS_DELTA, 7: REF_DELTA
        byte[] data;
        byte[] baseSha;
        byte[] finalSha;
        String typeName;
    }
    public static void main(String[] args) throws Exception {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.err.println("Logs from your program will appear here!");

    // TODO: Uncomment the code below to pass the first stage
    
    final String command = args[0];
    
    switch (command) {
      case "init" -> {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs/heads").mkdirs();
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
        String fileName = args.length > 2 ? args[2] : args[1];
                try {
                        Path path = Paths.get(fileName);
                        byte[] fileContent = Files.readAllBytes(path);
                        byte[] combinedData = createFullObjectData("blob", fileContent);
                        String hash = HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-1").digest(combinedData));
                    String blobPath = String.format(".git/objects/%s/%s",
                            hash.substring(0, 2),
                            hash.substring(2));

                    File blobFile = new File(blobPath);
                    blobFile.getParentFile().mkdirs();

                    try (DeflaterOutputStream out =
                                 new DeflaterOutputStream(new FileOutputStream(blobFile))) {
                        // Write the combined data (header + file content)
                        out.write(combinedData);
                    }

                    System.out.println(hash);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
              case "ls-tree" -> {
        // Locate file
        final File treeObject = getFileFromObjectSha(args[2]);
        // Decompress
                byte[] content = decompressObject(treeObject);
                int position = objectHeaderEnd(content);
                while (position < content.length) {
                    int nameEnd = indexOf(content, (byte) 0, position);
                    if (nameEnd < 0 || nameEnd + 20 >= content.length) {
                        break;
                    }
                    String entry = new String(content, position, nameEnd - position,
                                    StandardCharsets.UTF_8);
                    int separator = entry.indexOf(' ');
                    if (separator < 0) {
                        break;
                    }
                    System.out.println(entry.substring(separator + 1));
                    position = nameEnd + 1 + 20;
                }

      }
           case "write-tree" -> {
                byte[] sha = writeTree(new File("."));
                System.out.print(HexFormat.of().formatHex(sha));
                System.out.flush();
            }
            		case "commit-tree" -> {
				String treeSHA = args[1];
				String commitSHA = args[3];
				String commitMessage = args[5];

				byte[] sha = writeCommit(treeSHA, commitSHA, commitMessage);
				System.out.println(toHexSHA(sha));
			}
         case "clone" -> {
                    if (args.length < 2) {
                        System.err.println("Usage: git clone <url> [dir]");
                        return;
                    }

                    String url = args[1];
                    String dir;
                    if (args.length >= 3) {
                        dir = args[2];
                    } else {
                        String[] parts = url.split("/");
                        dir = parts[parts.length - 1];
                        if (dir.endsWith(".git")) {
                            dir = dir.substring(0, dir.length() - 4);
                        }
                    }
                    Path repoPath = Path.of(dir);

                    try {
                        Files.createDirectories(repoPath);
                        Files.createDirectories(repoPath.resolve(".git/objects"));
                        Files.createDirectories(repoPath.resolve(".git/refs/heads"));

                        HttpClient client = HttpClient.newHttpClient();

                        // 1. Ref Discovery
                        String refsUrl =
                                url.endsWith("/")
                                        ? url + "info/refs?service=git-upload-pack"
                                        : url + "/info/refs?service=git-upload-pack";
                        HttpRequest refsReq =
                                HttpRequest.newBuilder().uri(URI.create(refsUrl)).GET().build();

                        HttpResponse<byte[]> refsResp =
                                client.send(refsReq, HttpResponse.BodyHandlers.ofByteArray());
                        byte[] refsData = refsResp.body();

                        String targetSha = null;
                        String defaultBranch = "main";

                        // Parse pkt-lines from discovery response
                        ByteArrayInputStream in = new ByteArrayInputStream(refsData);
                        while (in.available() > 0) {
                            String line = readPktLine(in);
                            if (line == null) continue;
                            if (line.contains("HEAD") && targetSha == null) {
                                String[] parts = line.split(" ");
                                if (parts[0].length() >= 40) {
                                    targetSha = parts[0].substring(parts[0].length() - 40);
                                }
                            }
                            if (line.contains("refs/heads/")) {
                                int idx = line.indexOf("refs/heads/");
                                String b =
                                        line.substring(idx + 11)
                                                .split("\0")[0]
                                                .split(" ")[0]
                                                .split("\n")[0]
                                                .trim();
                                if (line.contains("HEAD")) {
                                    defaultBranch = b;
                                } else if (targetSha != null && line.startsWith(targetSha)) {
                                    defaultBranch = b;
                                }
                            }
                        }

                        if (targetSha == null) {
                            System.err.println("Could not resolve HEAD reference from remote.");
                            return;
                        }

                        // Write HEAD and default branch ref
                        Files.writeString(
                                repoPath.resolve(".git/HEAD"),
                                "ref: refs/heads/" + defaultBranch + "\n",
                                StandardCharsets.UTF_8);
                        Files.writeString(
                                repoPath.resolve(".git/refs/heads/" + defaultBranch),
                                targetSha + "\n",
                                StandardCharsets.UTF_8);

                        // 2. Fetch Packfile
                        String uploadPackUrl =
                                url.endsWith("/")
                                        ? url + "git-upload-pack"
                                        : url + "/git-upload-pack";
                        String wantLine = "0032want " + targetSha + "\n00000009done\n";

                        HttpRequest fetchReq =
                                HttpRequest.newBuilder()
                                        .uri(URI.create(uploadPackUrl))
                                        .header(
                                                "Content-Type",
                                                "application/x-git-upload-pack-request")
                                        .POST(HttpRequest.BodyPublishers.ofString(wantLine))
                                        .build();

                        HttpResponse<byte[]> fetchResp =
                                client.send(fetchReq, HttpResponse.BodyHandlers.ofByteArray());
                        byte[] packData = extractPackfileData(fetchResp.body());

                        // 3. Parse and Unpack objects
                        List<PackObject> objects = parsePackfile(packData);
                        Map<String, byte[]> objectStore = new HashMap<>();

                        // Write objects to .git/objects
                        for (PackObject obj : objects) {
                            String typeStr =
                                    switch (obj.type) {
                                        case 1 -> "commit";
                                        case 2 -> "tree";
                                        case 3 -> "blob";
                                        case 4 -> "tag";
                                        default -> "blob";
                                    };
                            byte[] rawSha = writeObjectInDir(repoPath, typeStr, obj.data);
                            String hexSha = HexFormat.of().formatHex(rawSha);
                            obj.finalSha = rawSha;
                            obj.typeName = typeStr;
                            objectStore.put(hexSha, obj.data);
                        }

                        // 4. Checkout HEAD tree to working directory
                        byte[] commitData =
                                readObjectFromStoreOrDisk(repoPath, targetSha, objectStore);
                        String commitText = new String(commitData, StandardCharsets.UTF_8);
                        String treeSha = null;
                        for (String l : commitText.split("\n")) {
                            if (l.startsWith("tree ")) {
                                treeSha = l.substring(5).trim();
                                break;
                            }
                        }

                        if (treeSha != null) {
                            checkoutTree(repoPath, repoPath, treeSha, objectStore);
                        }

                    } catch (Exception e) {
                        System.err.println("Error during clone: " + e.getMessage());
                        e.printStackTrace();
                    }

                }
      default -> System.out.println("Unknown command: " + command);
    }
  }
    private static byte[] writeObject(byte[] store) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(store);
        String hash = HexFormat.of().formatHex(digest);

        Path dir = Files.createDirectories(Path.of(".git/objects", hash.substring(0, 2)));
        Path out = dir.resolve(hash.substring(2));
        if (!Files.exists(out)) {
            try (var dos = new DeflaterOutputStream(Files.newOutputStream(out))) {
                dos.write(store);
            }
        }
        return digest;
    }

    // 디렉토리를 tree로 만들어 저장하고 raw 20바이트 SHA 반환
    private static byte[] writeTree(File dir) throws Exception {
        File[] files = dir.listFiles();
        Arrays.sort(files, Comparator.comparing(File::getName)); // 이름순 정렬

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (File f : files) {
            if (f.getName().equals(".git")) continue; // .git 제외

            String mode;
            byte[] sha;
            if (f.isDirectory()) {
                mode = "40000";
                sha = writeTree(f); // 재귀
            } else {
                mode = "100644";
                sha = writeBlob(f);
            }

            // <mode> <name>\0<20바이트 raw sha>
            body.write((mode + " " + f.getName() + "\0").getBytes());
            body.write(sha);
        }

        byte[] content = body.toByteArray();
        byte[] header = ("tree " + content.length + "\0").getBytes();
        byte[] store = new byte[header.length + content.length];
        System.arraycopy(header, 0, store, 0, header.length);
        System.arraycopy(content, 0, store, header.length, content.length);

        return writeObject(store);
    }

    // 파일을 blob으로 만들어 저장하고 raw 20바이트 SHA 반환
    private static byte[] writeBlob(File f) throws Exception {
        byte[] c = Files.readAllBytes(f.toPath());
        byte[] header = ("blob " + c.length + "\0").getBytes();
        byte[] store = new byte[header.length + c.length];
        System.arraycopy(header, 0, store, 0, header.length);
        System.arraycopy(c, 0, store, header.length, c.length);
        return writeObject(store);
    }
     private static byte[] writeCommit(
                            String treeSHA, String commitSHA,
                            String commitMessage) throws Exception {
                          String author =
                              "James Gosling <james@nighthacks.com>";

                          ByteArrayOutputStream out =
                              new ByteArrayOutputStream();

                          out.write(("tree " + treeSHA + "\n").getBytes());
                          out.write(("parent " + commitSHA + "\n").getBytes());
                          out.write(("author " + author + "\n").getBytes());
                          out.write(("committer " + author + "\n").getBytes());
                          out.write(("\n").getBytes());
                          out.write((commitMessage + "\n").getBytes());

                                                    return writeObject(createFullObjectData("commit", out.toByteArray()));
                        }
                            private static String readPktLine(InputStream in) throws IOException {
        byte[] lenBytes = in.readNBytes(4);
        if (lenBytes.length < 4) return null;
        String lenHex = new String(lenBytes, StandardCharsets.UTF_8);

        int len;
        try {
            len = Integer.parseInt(lenHex, 16);
        } catch (NumberFormatException e) {
            return null;
        }

        if (len == 0) return "";
        if (len < 4) return "";

        byte[] lineBytes = in.readNBytes(len - 4);
        return new String(lineBytes, StandardCharsets.UTF_8);
    }

    private static byte[] extractPackfileData(byte[] payload) throws IOException {
        // Check if 'PACK' magic bytes exist directly in payload
        int packPos = -1;
        for (int i = 0; i <= payload.length - 4; i++) {
            if (payload[i] == 'P'
                    && payload[i + 1] == 'A'
                    && payload[i + 2] == 'C'
                    && payload[i + 3] == 'K') {
                packPos = i;
                break;
            }
        }

        if (packPos == 0) {
            return payload;
        }

        if (packPos > 0 && payload[packPos - 1] != 1) {
            return Arrays.copyOfRange(payload, packPos, payload.length);
        }

        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        ByteArrayOutputStream packStream = new ByteArrayOutputStream();

        while (in.available() > 0) {
            byte[] lenBytes = in.readNBytes(4);
            if (lenBytes.length < 4) break;

            int len;
            try {
                len = Integer.parseInt(new String(lenBytes, StandardCharsets.UTF_8), 16);
            } catch (NumberFormatException e) {
                break;
            }

            if (len == 0) continue;
            if (len < 4) continue;

            byte[] line = in.readNBytes(len - 4);
            if (line.length > 0 && line[0] == 1) { // Sideband channel 1: packfile binary data
                packStream.write(line, 1, line.length - 1);
            }
        }

        byte[] result = packStream.toByteArray();
        return result.length > 0 ? result : payload;
    }

    private static List<PackObject> parsePackfile(byte[] pack) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(pack);
        byte[] header = in.readNBytes(12); // 'PACK', version (4), num_objects (4)
        int numObjects =
                ((header[8] & 0xFF) << 24)
                        | ((header[9] & 0xFF) << 16)
                        | ((header[10] & 0xFF) << 8)
                        | (header[11] & 0xFF);

        List<PackObject> resolvedList = new ArrayList<>();
        Map<String, PackObject> bySha = new HashMap<>();
        Map<Long, PackObject> byOffset = new HashMap<>();

        for (int i = 0; i < numObjects; i++) {
            long objOffset = pack.length - in.available();
            PackObject obj = new PackObject();
            int c = in.read();
            int type = (c >> 4) & 7;
            long size = c & 15;
            int shift = 4;

            while ((c & 0x80) != 0) {
                c = in.read();
                size |= ((long) (c & 0x7F)) << shift;
                shift += 7;
            }

            obj.type = type;

            if (type == 6) { // OFS_DELTA
                long offset = 0;
                c = in.read();
                offset = c & 0x7F;
                while ((c & 0x80) != 0) {
                    c = in.read();
                    offset = ((offset + 1) << 7) | (c & 0x7F);
                }
                byte[] deltaData = decompressZlib(in);
                long baseOffset = objOffset - offset;
                PackObject baseObj = byOffset.get(baseOffset);
                byte[] baseContent = (baseObj != null) ? baseObj.data : new byte[0];
                obj.data = applyDelta(baseContent, deltaData);
                obj.type = (baseObj != null) ? baseObj.type : inferObjectType(obj.data);
            } else if (type == 7) { // REF_DELTA
                obj.baseSha = in.readNBytes(20);
                byte[] deltaData = decompressZlib(in);

                String baseHex = HexFormat.of().formatHex(obj.baseSha);
                PackObject baseObj = bySha.get(baseHex);
                byte[] baseContent = (baseObj != null) ? baseObj.data : new byte[0];
                obj.data = applyDelta(baseContent, deltaData);
                obj.type = (baseObj != null) ? baseObj.type : inferObjectType(obj.data);
            } else {
                obj.data = decompressZlib(in);
            }

            // Calculate SHA-1 and store
            String typeStr =
                    switch (obj.type) {
                        case 1 -> "commit";
                        case 2 -> "tree";
                        case 3 -> "blob";
                        case 4 -> "tag";
                        default -> "blob";
                    };

            byte[] fullObj = createFullObjectData(typeStr, obj.data);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha1 = md.digest(fullObj);
            String shaHex = HexFormat.of().formatHex(sha1);
            obj.finalSha = sha1;

            bySha.put(shaHex, obj);
            byOffset.put(objOffset, obj);
            resolvedList.add(obj);
        }

        return resolvedList;
    }

    private static int inferObjectType(byte[] data) {
        String s = new String(data, 0, Math.min(data.length, 50), StandardCharsets.UTF_8);
        if (s.startsWith("tree ")) return 1;
        if (s.startsWith("object ") || s.startsWith("type ")) return 4;
        if (s.contains("author ") || s.contains("committer ")) return 1; // Commit
        return 3; // Blob / Tree fallbacks handled during checkout
    }

    private static byte[] decompressZlib(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Inflater inflater = new Inflater();
        byte[] buffer = new byte[4096];

        in.mark(Integer.MAX_VALUE);
        byte[] inputBuf = in.readAllBytes();
        in.reset();

        inflater.setInput(inputBuf);
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) break;
            out.write(buffer, 0, count);
        }

        in.skip(inflater.getBytesRead());
        inflater.end();
        return out.toByteArray();
    }

    private static byte[] applyDelta(byte[] base, byte[] delta) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(delta);
        readVarInt(in); // Base size
        readVarInt(in); // Result size

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (in.available() > 0) {
            int cmd = in.read();
            if ((cmd & 0x80) != 0) { // Copy instruction
                int offset = 0;
                int size = 0;

                if ((cmd & 0x01) != 0) offset |= in.read();
                if ((cmd & 0x02) != 0) offset |= (in.read() << 8);
                if ((cmd & 0x04) != 0) offset |= (in.read() << 16);
                if ((cmd & 0x08) != 0) offset |= (in.read() << 24);

                if ((cmd & 0x10) != 0) size |= in.read();
                if ((cmd & 0x20) != 0) size |= (in.read() << 8);
                if ((cmd & 0x40) != 0) size |= (in.read() << 16);

                if (size == 0) size = 0x10000;
                out.write(base, offset, size);
            } else if (cmd > 0) { // Insert instruction
                out.write(in.readNBytes(cmd));
            }
        }

        return out.toByteArray();
    }

    private static long readVarInt(InputStream in) throws IOException {
        long value = 0;
        int shift = 0;
        int b;
        do {
            b = in.read();
            value |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    private static byte[] createFullObjectData(String type, byte[] content) {
        byte[] header = (type + " " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[header.length + content.length];
        System.arraycopy(header, 0, full, 0, header.length);
        System.arraycopy(content, 0, full, header.length, content.length);
        return full;
    }

    private static byte[] writeObjectInDir(Path repoPath, String type, byte[] content)
            throws Exception {
        byte[] uncompressedData = createFullObjectData(type, content);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] sha1Raw = md.digest(uncompressedData);
        String sha1Hex = HexFormat.of().formatHex(sha1Raw);

        Path objectPath =
                repoPath.resolve(
                        ".git/objects/" + sha1Hex.substring(0, 2) + "/" + sha1Hex.substring(2));
        if (!Files.exists(objectPath)) {
            Files.createDirectories(objectPath.getParent());
            try (OutputStream fileOut = Files.newOutputStream(objectPath);
                    DeflaterOutputStream zipStream = new DeflaterOutputStream(fileOut)) {
                zipStream.write(uncompressedData);
            }
        }
        return sha1Raw;
    }

    private static byte[] readObjectFromStoreOrDisk(
            Path repoPath, String shaHex, Map<String, byte[]> store) throws Exception {
        if (store.containsKey(shaHex)) {
            return store.get(shaHex);
        }
        Path objectPath =
                repoPath.resolve(
                        ".git/objects/" + shaHex.substring(0, 2) + "/" + shaHex.substring(2));
        try (InputStream fileStream = Files.newInputStream(objectPath);
                InflaterInputStream inflaterStream = new InflaterInputStream(fileStream);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] readBuffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inflaterStream.read(readBuffer)) != -1) {
                buffer.write(readBuffer, 0, bytesRead);
            }

            byte[] decompressed = buffer.toByteArray();
            int nullIdx = -1;
            for (int i = 0; i < decompressed.length; i++) {
                if (decompressed[i] == 0) {
                    nullIdx = i;
                    break;
                }
            }
            return Arrays.copyOfRange(decompressed, nullIdx + 1, decompressed.length);
        }
    }

    private static byte[] decompressObject(File objectFile) throws IOException {
        try (InputStream fileStream = new FileInputStream(objectFile);
                InflaterInputStream inflaterStream = new InflaterInputStream(fileStream);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            inflaterStream.transferTo(buffer);
            return buffer.toByteArray();
        }
    }

    private static File getFileFromObjectSha(String sha) {
        if (sha.length() < 3) {
            throw new IllegalArgumentException("Invalid object SHA: " + sha);
        }
        return new File(".git/objects/" + sha.substring(0, 2) + "/" + sha.substring(2));
    }

    private static String toHexSHA(byte[] sha) {
        return HexFormat.of().formatHex(sha);
    }

    private static int objectHeaderEnd(byte[] object) {
        int nullIndex = indexOf(object, (byte) 0, 0);
        return nullIndex < 0 ? 0 : nullIndex + 1;
    }

    private static int indexOf(byte[] data, byte value, int fromIndex) {
        for (int i = fromIndex; i < data.length; i++) {
            if (data[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] objectContent(byte[] object) {
        return Arrays.copyOfRange(object, objectHeaderEnd(object), object.length);
    }

    private static void checkoutTree(
            Path repoPath, Path currentDir, String treeSha, Map<String, byte[]> store)
            throws Exception {
        byte[] treeObject = store.containsKey(treeSha)
                ? createFullObjectData("tree", store.get(treeSha))
                : decompressObject(repoPath.resolve(
                    ".git/objects/" + treeSha.substring(0, 2) + "/" + treeSha.substring(2)).toFile());
        byte[] treeContent = objectContent(treeObject);
        int position = 0;
        while (position < treeContent.length) {
            int nameEnd = indexOf(treeContent, (byte) 0, position);
            if (nameEnd < 0 || nameEnd + 20 >= treeContent.length) {
                throw new IOException("Malformed tree object " + treeSha);
            }
            String entry = new String(treeContent, position, nameEnd - position,
                    StandardCharsets.UTF_8);
            int separator = entry.indexOf(' ');
            if (separator < 0) {
                throw new IOException("Malformed tree entry in " + treeSha);
            }
            String mode = entry.substring(0, separator);
            String name = entry.substring(separator + 1);
            String entrySha = HexFormat.of().formatHex(
                    Arrays.copyOfRange(treeContent, nameEnd + 1, nameEnd + 21));
            Path entryPath = currentDir.resolve(name);
            if (mode.equals("40000") || mode.equals("040000")) {
                Files.createDirectories(entryPath);
                checkoutTree(repoPath, entryPath, entrySha, store);
            } else {
                byte[] blobObject = store.containsKey(entrySha)
                        ? store.get(entrySha)
                        : objectContent(decompressObject(repoPath.resolve(
                            ".git/objects/" + entrySha.substring(0, 2) + "/" + entrySha.substring(2)).toFile()));
                Files.write(entryPath, blobObject);
            }
            position = nameEnd + 21;
        }
    }

}

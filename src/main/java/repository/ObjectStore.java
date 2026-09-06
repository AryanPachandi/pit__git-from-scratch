package repository;

import objects.GitObject;
import utils.CompressionUtils;
import utils.HashUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ObjectStore {
    private final Path gitDirectory;

    public ObjectStore(Path gitDirectory) { this.gitDirectory = gitDirectory; }

    public byte[] write(String type, byte[] content) throws Exception {
        return writeFull(GitObject.encode(type, content));
    }

    public byte[] writeFull(byte[] object) throws Exception {
        byte[] digest = HashUtils.sha1(object);
        String hash = HashUtils.hex(digest);
        Path output = pathFor(hash);
        Files.createDirectories(output.getParent());
        if (!Files.exists(output)) Files.write(output, CompressionUtils.compress(object));
        return digest;
    }

    public byte[] readFull(String hash) throws IOException {
        try (InputStream input = Files.newInputStream(pathFor(hash))) {
            return CompressionUtils.decompress(input);
        }
    }

    public byte[] readContent(String hash) throws IOException {
        byte[] full = readFull(hash);
        return java.util.Arrays.copyOfRange(full, GitObject.contentStart(full), full.length);
    }

    public Path pathFor(String hash) {
        return gitDirectory.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2));
    }
}
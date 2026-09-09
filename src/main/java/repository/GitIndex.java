package repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** A small, versioned staging index used by this implementation. */
public class GitIndex {
    private static final byte[] MAGIC = "PITINDEX".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;

    private final Path indexPath;
    private final Map<String, GitIndexEntry> entries;

    public GitIndex(Path indexPath) throws IOException {
        this.indexPath = indexPath.toAbsolutePath().normalize();
        this.entries = loadEntries(this.indexPath);
    }

    public static GitIndex load(Path indexPath) throws IOException {
        return new GitIndex(indexPath);
    }

    public void addEntry(String path, String hash) {
        updateEntry(new GitIndexEntry(path, hash));
    }

    public Map<String, GitIndexEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public void updateEntry(GitIndexEntry entry) {
        entries.put(entry.getPath(), entry);
    }

    public void removeEntry(String path) {
        entries.remove(path);
    }

    public GitIndexEntry getEntry(String path) {
        return entries.get(path);
    }

    public boolean containsPath(String path) {
        return entries.containsKey(path);
    }

    public void save() throws IOException {
        Path parent = indexPath.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "index-", ".tmp");
        try {
            writeTo(temporary);
            try {
                Files.move(temporary, indexPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, indexPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeTo(Path path) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(entries.size());
            for (GitIndexEntry entry : entries.values()) {
                writeString(output, entry.getPath());
                writeString(output, entry.getHash());
            }
        }
    }

    private static Map<String, GitIndexEntry> loadEntries(Path path) throws IOException {
        Map<String, GitIndexEntry> loaded = new TreeMap<>();
        if (!Files.exists(path)) return loaded;

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("unsupported index format: " + path);
            }
            if (input.readInt() != VERSION) {
                throw new IOException("unsupported index version: " + path);
            }
            int entryCount = input.readInt();
            if (entryCount < 0) throw new IOException("invalid index entry count: " + path);
            for (int i = 0; i < entryCount; i++) {
                String entryPath = readString(input, path);
                String hash = readString(input, path);
                loaded.put(entryPath, new GitIndexEntry(entryPath, hash));
            }
            if (input.read() != -1) throw new IOException("trailing data in index: " + path);
        } catch (EOFException exception) {
            throw new IOException("truncated index: " + path, exception);
        }
        return loaded;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, Path path) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 16 * 1024 * 1024) {
            throw new IOException("invalid string length in index: " + path);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}   
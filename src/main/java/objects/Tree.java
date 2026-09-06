package objects;

import repository.ObjectStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class Tree {
    public record Entry(String mode, String name, byte[] sha) {}

    private Tree() {}

    public static byte[] write(File directory, ObjectStore store) throws Exception {
        File[] files = directory.listFiles();
        if (files == null) throw new IllegalStateException("Cannot read directory: " + directory);
        Arrays.sort(files, Comparator.comparing(File::getName));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (File file : files) {
            if (file.getName().equals(".git")) continue;
            String mode = file.isDirectory() ? "40000" : "100644";
            byte[] sha = file.isDirectory() ? write(file, store) : Blob.write(file, store);
            body.write((mode + " " + file.getName() + "\0").getBytes(StandardCharsets.UTF_8));
            body.write(sha);
        }
        return store.write("tree", body.toByteArray());
    }

    public static List<Entry> entries(byte[] fullObject) {
        byte[] content = Arrays.copyOfRange(fullObject, GitObject.contentStart(fullObject), fullObject.length);
        List<Entry> result = new ArrayList<>();
        int position = 0;
        while (position < content.length) {
            int nameEnd = indexOf(content, (byte) 0, position);
            if (nameEnd < 0 || nameEnd + 20 >= content.length) break;
            String entry = new String(content, position, nameEnd - position, StandardCharsets.UTF_8);
            int separator = entry.indexOf(' ');
            if (separator < 0) break;
            result.add(new Entry(entry.substring(0, separator), entry.substring(separator + 1),
                    Arrays.copyOfRange(content, nameEnd + 1, nameEnd + 21)));
            position = nameEnd + 21;
        }
        return result;
    }

    private static int indexOf(byte[] data, byte value, int start) {
        for (int i = start; i < data.length; i++) if (data[i] == value) return i;
        return -1;
    }
}
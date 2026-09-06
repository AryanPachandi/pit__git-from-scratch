package objects;

import java.nio.charset.StandardCharsets;

public final class GitObject {
    private GitObject() {}

    public static byte[] encode(String type, byte[] content) {
        byte[] header = (type + " " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[header.length + content.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(content, 0, result, header.length, content.length);
        return result;
    }

    public static int contentStart(byte[] object) {
        for (int i = 0; i < object.length; i++) {
            if (object[i] == 0) return i + 1;
        }
        return 0;
    }
}
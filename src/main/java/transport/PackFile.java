package transport;

import objects.GitObject;
import utils.HashUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

public final class PackFile {
    private PackFile() {}

    public static List<PackObject> parse(byte[] pack) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(pack);
        byte[] header = input.readNBytes(12);
        int objectCount = ((header[8] & 0xff) << 24) | ((header[9] & 0xff) << 16)
                | ((header[10] & 0xff) << 8) | (header[11] & 0xff);
        List<PackObject> objects = new ArrayList<>();
        Map<String, PackObject> bySha = new HashMap<>();
        Map<Long, PackObject> byOffset = new HashMap<>();
        for (int i = 0; i < objectCount; i++) {
            long objectOffset = pack.length - input.available();
            PackObject object = new PackObject();
            int current = input.read();
            object.type = (current >> 4) & 7;
            long size = current & 15;
            int shift = 4;
            while ((current & 0x80) != 0) {
                current = input.read();
                size |= (long) (current & 0x7f) << shift;
                shift += 7;
            }
            if (object.type == 6) {
                long offset = input.read() & 0x7f;
                while ((current & 0x80) != 0) {
                    current = input.read();
                    offset = ((offset + 1) << 7) | (current & 0x7f);
                }
                byte[] delta = decompress(input);
                PackObject base = byOffset.get(objectOffset - offset);
                object.data = applyDelta(base == null ? new byte[0] : base.data, delta);
                object.type = base == null ? inferType(object.data) : base.type;
            } else if (object.type == 7) {
                object.baseSha = input.readNBytes(20);
                byte[] delta = decompress(input);
                PackObject base = bySha.get(HashUtils.hex(object.baseSha));
                object.data = applyDelta(base == null ? new byte[0] : base.data, delta);
                object.type = base == null ? inferType(object.data) : base.type;
            } else {
                object.data = decompress(input);
            }
            String type = typeName(object.type);
            object.finalSha = HashUtils.sha1(GitObject.encode(type, object.data));
            bySha.put(HashUtils.hex(object.finalSha), object);
            byOffset.put(objectOffset, object);
            objects.add(object);
        }
        return objects;
    }

    public static String typeName(int type) {
        return switch (type) {
            case 1 -> "commit";
            case 2 -> "tree";
            case 3 -> "blob";
            case 4 -> "tag";
            default -> "blob";
        };
    }

    private static byte[] decompress(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Inflater inflater = new Inflater();
        input.mark(Integer.MAX_VALUE);
        byte[] inputBuffer = input.readAllBytes();
        input.reset();
        inflater.setInput(inputBuffer);
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) break;
            output.write(buffer, 0, count);
        }
        input.skip(inflater.getBytesRead());
        inflater.end();
        return output.toByteArray();
    }

    private static byte[] applyDelta(byte[] base, byte[] delta) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(delta);
        readVarInt(input);
        readVarInt(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (input.available() > 0) {
            int command = input.read();
            if ((command & 0x80) != 0) {
                int offset = 0;
                int size = 0;
                if ((command & 1) != 0) offset |= input.read();
                if ((command & 2) != 0) offset |= input.read() << 8;
                if ((command & 4) != 0) offset |= input.read() << 16;
                if ((command & 8) != 0) offset |= input.read() << 24;
                if ((command & 0x10) != 0) size |= input.read();
                if ((command & 0x20) != 0) size |= input.read() << 8;
                if ((command & 0x40) != 0) size |= input.read() << 16;
                if (size == 0) size = 0x10000;
                output.write(base, offset, size);
            } else if (command > 0) {
                output.write(input.readNBytes(command));
            }
        }
        return output.toByteArray();
    }

    private static long readVarInt(InputStream input) throws IOException {
        long value = 0;
        int shift = 0;
        int current;
        do {
            current = input.read();
            value |= (long) (current & 0x7f) << shift;
            shift += 7;
        } while ((current & 0x80) != 0);
        return value;
    }

    private static int inferType(byte[] data) {
        String text = new String(data, 0, Math.min(data.length, 50), StandardCharsets.UTF_8);
        if (text.startsWith("tree ")) return 2;
        if (text.startsWith("object ") || text.startsWith("type ")) return 4;
        if (text.contains("author ") || text.contains("committer ")) return 1;
        return 3;
    }
}
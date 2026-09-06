package utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class CompressionUtils {
    private CompressionUtils() {}

    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(data);
        }
        return output.toByteArray();
    }

    public static byte[] decompress(InputStream input) throws IOException {
        try (InflaterInputStream inflater = new InflaterInputStream(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            inflater.transferTo(output);
            return output.toByteArray();
        }
    }
}
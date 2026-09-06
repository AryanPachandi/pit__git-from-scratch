package utils;

import java.security.MessageDigest;
import java.util.HexFormat;

public final class HashUtils {
    private HashUtils() {}

    public static byte[] sha1(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(data);
    }

    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }
}
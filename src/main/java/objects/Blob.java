package objects;

import repository.ObjectStore;
import java.io.File;
import java.nio.file.Files;

public final class Blob {
    private Blob() {}

    public static byte[] write(File file, ObjectStore store) throws Exception {
        return store.write("blob", Files.readAllBytes(file.toPath()));
    }
}
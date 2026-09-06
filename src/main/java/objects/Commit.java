package objects;

import repository.ObjectStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class Commit {
    private Commit() {}

    public static byte[] write(String treeSha, String parentSha, String message,
            ObjectStore store) throws Exception {
        String author = "James Gosling <james@nighthacks.com>";
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.write(("tree " + treeSha + "\n").getBytes(StandardCharsets.UTF_8));
        content.write(("parent " + parentSha + "\n").getBytes(StandardCharsets.UTF_8));
        content.write(("author " + author + "\n").getBytes(StandardCharsets.UTF_8));
        content.write(("committer " + author + "\n\n").getBytes(StandardCharsets.UTF_8));
        content.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        return store.write("commit", content.toByteArray());
    }
}
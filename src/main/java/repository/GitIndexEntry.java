package repository;

public class GitIndexEntry {

    private final String path;
    private final String hash;

    public GitIndexEntry(String path, String hash) {
        this.path = path;
        this.hash = hash;
    }

    public String getPath() {
        return path;
    }

    public String getHash() {
        return hash;
    }
}
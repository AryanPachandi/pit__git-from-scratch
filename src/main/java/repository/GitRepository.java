package repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitRepository {
    private final Path root;
    private final Path gitDirectory;
    private final ObjectStore objectStore;

    public GitRepository(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.gitDirectory = this.root.resolve(".git");
        this.objectStore = new ObjectStore(gitDirectory);
    }

    public Path root() { return root; }
    public Path gitDirectory() { return gitDirectory; }
    public ObjectStore objects() { return objectStore; }

    public Path indexPath() { return gitDirectory.resolve("index"); }

    public GitIndex index() throws IOException { return GitIndex.load(indexPath()); }

    public static GitRepository open(Path start) throws IOException {
        Path candidate = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(candidate)) candidate = candidate.getParent();
        while (candidate != null) {
            GitRepository repository = new GitRepository(candidate);
            if (Files.isDirectory(repository.gitDirectory())
                    && Files.isDirectory(repository.gitDirectory().resolve("objects"))
                    && Files.isRegularFile(repository.head())) {
                return repository;
            }
            candidate = candidate.getParent();
        }
        throw new IOException("not a valid pit repository (or any parent directory): " + start);
    }

    public void initialize() throws IOException {
        Files.createDirectories(gitDirectory.resolve("objects"));
        Files.createDirectories(gitDirectory.resolve("refs/heads"));
        Files.writeString(gitDirectory.resolve("HEAD"), "ref: refs/heads/main\n",
                StandardCharsets.UTF_8);
    }

    public Path head() { return gitDirectory.resolve("HEAD"); }
    public Path branch(String name) { return gitDirectory.resolve("refs/heads").resolve(name); }
}
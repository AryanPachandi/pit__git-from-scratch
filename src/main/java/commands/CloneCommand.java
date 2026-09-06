package commands;

import objects.GitObject;
import objects.Tree;
import repository.GitRepository;
import repository.ObjectStore;
import transport.GitHttpClient;
import transport.PackFile;
import transport.PackObject;
import utils.HashUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CloneCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: git clone <url> [dir]");
            return;
        }
        String url = args[1];
        String directory = args.length >= 3 ? args[2] : defaultDirectory(url);
        Path repositoryPath = Path.of(directory);
        GitRepository repository = new GitRepository(repositoryPath);
        Files.createDirectories(repositoryPath);
        Files.createDirectories(repository.gitDirectory().resolve("objects"));
        Files.createDirectories(repository.gitDirectory().resolve("refs/heads"));

        try {
            GitHttpClient client = new GitHttpClient();
            GitHttpClient.RemoteRefs refs = client.discover(url);
            if (refs.headSha() == null) {
                System.err.println("Could not resolve HEAD reference from remote.");
                return;
            }
            Files.writeString(repository.head(), "ref: refs/heads/" + refs.defaultBranch() + "\n",
                    StandardCharsets.UTF_8);
            Files.writeString(repository.branch(refs.defaultBranch()), refs.headSha() + "\n",
                    StandardCharsets.UTF_8);
            for (PackObject object : PackFile.parse(client.fetchPack(url, refs.headSha()))) {
                repository.objects().write(PackFile.typeName(object.type), object.data);
            }
            String treeSha = findTree(repository.objects().readContent(refs.headSha()));
            if (treeSha != null) checkoutTree(repository, repository.root(), treeSha);
        } catch (Exception exception) {
            System.err.println("Error during clone: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private static String defaultDirectory(String url) {
        String directory = url.substring(url.lastIndexOf('/') + 1);
        return directory.endsWith(".git") ? directory.substring(0, directory.length() - 4) : directory;
    }

    private static String findTree(byte[] commitContent) {
        for (String line : new String(commitContent, StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("tree ")) return line.substring(5).trim();
        }
        return null;
    }

    private static void checkoutTree(GitRepository repository, Path currentDirectory, String treeSha)
            throws Exception {
        ObjectStore store = repository.objects();
        for (Tree.Entry entry : Tree.entries(GitObject.encode("tree", store.readContent(treeSha)))) {
            String entrySha = HashUtils.hex(entry.sha());
            Path entryPath = currentDirectory.resolve(entry.name());
            if (entry.mode().equals("40000") || entry.mode().equals("040000")) {
                Files.createDirectories(entryPath);
                checkoutTree(repository, entryPath, entrySha);
            } else {
                Files.write(entryPath, store.readContent(entrySha));
            }
        }
    }
}
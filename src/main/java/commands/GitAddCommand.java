package commands;

import repository.GitIndex;
import repository.GitRepository;
import utils.HashUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class GitAddCommand implements GitCommand {
  @Override
  public void execute(String[] args) throws Exception {
    if (args.length < 2) throw new IllegalArgumentException("usage: pit add <file>...");

    GitRepository repository = GitRepository.open(Path.of("."));
    GitIndex index = repository.index();
    List<Path> files = new ArrayList<>();
    for (int argumentIndex = 1; argumentIndex < args.length; argumentIndex++) {
      collectFiles(repository, Path.of(args[argumentIndex]), files);
    }

    files.sort(Comparator.comparing(path -> normalizePath(repository, path)));
    for (Path file : files) stageFile(repository, index, file);
    index.save();
  }

  private static void collectFiles(GitRepository repository, Path input, List<Path> files)
      throws IOException {
    Path path = input.toAbsolutePath().normalize();
    if (!path.startsWith(repository.root())) {
      throw new IOException("pathspec '" + input + "' is outside the repository");
    }
    if (Files.isSymbolicLink(path)) {
      throw new IOException("pathspec '" + input + "' is a symbolic link");
    }
    if (!Files.exists(path)) {
      throw new IOException("pathspec '" + input + "' did not match any files");
    }

    Path relative = repository.root().relativize(path);
    if (relative.toString().equals(".git") || relative.startsWith(".git")) {
      throw new IOException("pathspec '" + input + "' is inside the repository metadata");
    }
    if (Files.isRegularFile(path)) {
      files.add(path);
      return;
    }
    if (!Files.isDirectory(path)) {
      throw new IOException("pathspec '" + input + "' is not a regular file or directory");
    }

    try (Stream<Path> stream = Files.walk(path)) {
      stream.filter(candidate -> !Files.isSymbolicLink(candidate))
          .filter(candidate -> !candidate.equals(repository.gitDirectory()))
          .filter(candidate -> !candidate.startsWith(repository.gitDirectory()))
          .filter(Files::isRegularFile)
          .forEach(files::add);
    }
  }

  private static void stageFile(GitRepository repository, GitIndex index, Path file) throws Exception {
    String relativePath = normalizePath(repository, file);
    byte[] sha = repository.objects().write("blob", Files.readAllBytes(file));
    String hash = HashUtils.hex(sha);
    if (index.getEntry(relativePath) == null
        || !hash.equals(index.getEntry(relativePath).getHash())) {
      index.addEntry(relativePath, hash);
    }
  }

  private static String normalizePath(GitRepository repository, Path path) {
    return repository.root().relativize(path.toAbsolutePath().normalize())
        .toString().replace(path.getFileSystem().getSeparator(), "/");
  }
}

package commands;

import repository.GitRepository;
import java.nio.charset.StandardCharsets;

public class CatFileCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        String hash = args[2];
        GitRepository repository = new GitRepository(java.nio.file.Path.of("."));
        if (!java.nio.file.Files.exists(repository.objects().pathFor(hash))) {
            System.out.println("Object not found: " + hash);
            return;
        }
        System.out.print(new String(repository.objects().readContent(hash), StandardCharsets.UTF_8));
    }
}
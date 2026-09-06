package commands;

import objects.Tree;
import repository.GitRepository;
import utils.HashUtils;

public class LsTreeCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        String hash = args[2];
        GitRepository repository = new GitRepository(java.nio.file.Path.of("."));
        for (Tree.Entry entry : Tree.entries(repository.objects().readFull(hash))) {
            System.out.println(entry.name());
        }
    }
}
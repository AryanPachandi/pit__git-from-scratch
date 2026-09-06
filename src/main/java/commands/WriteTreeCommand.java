package commands;

import objects.Tree;
import repository.GitRepository;
import utils.HashUtils;

public class WriteTreeCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        byte[] sha = Tree.write(java.nio.file.Path.of(".").toFile(),
                new GitRepository(java.nio.file.Path.of(".")).objects());
        System.out.print(HashUtils.hex(sha));
        System.out.flush();
    }
}
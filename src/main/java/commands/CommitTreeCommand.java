package commands;

import objects.Commit;
import repository.GitRepository;
import utils.HashUtils;

public class CommitTreeCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        GitRepository repository = new GitRepository(java.nio.file.Path.of("."));
        byte[] sha = Commit.write(args[1], args[3], args[5], repository.objects());
        System.out.println(HashUtils.hex(sha));
    }
}
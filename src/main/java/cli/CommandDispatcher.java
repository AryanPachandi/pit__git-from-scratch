package cli;

import commands.CatFileCommand;
import commands.CloneCommand;
import commands.CommitTreeCommand;
import commands.GitCommand;
import commands.HashObjectCommand;
import commands.InitCommand;
import commands.LsTreeCommand;
import commands.WriteTreeCommand;
import java.util.Map;
import commands.*;

public class CommandDispatcher {
    private final Map<String, GitCommand> commands = Map.of(
            "init", new InitCommand(),
            "hash-object", new HashObjectCommand(),
            "cat-file", new CatFileCommand(),
            "write-tree", new WriteTreeCommand(),
            "ls-tree", new LsTreeCommand(),
            "commit-tree", new CommitTreeCommand(),
            "clone", new CloneCommand(),
            "add" , new GitAddCommand()
            "status", new GitStatusCommand(),
            );

    public void dispatch(String[] args) throws Exception {
        if (args.length == 0) return;
        GitCommand command = commands.get(args[0]);
        if (command == null) {
            System.out.println("Unknown command: " + args[0]);
            return;
        }
        System.err.println("Logs from your program will appear here!");
        command.execute(args);
    }
}

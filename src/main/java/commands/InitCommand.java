package commands;

import repository.GitRepository;

public class InitCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        new GitRepository(java.nio.file.Path.of(".")).initialize();
        System.out.println("Initialized git directory");
    }
}
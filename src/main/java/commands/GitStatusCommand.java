package commands;

public class GitStatusCommand implements GitCommand {
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("On branch master");
        System.out.println("nothing to commit, working tree clean");
    }
    
}

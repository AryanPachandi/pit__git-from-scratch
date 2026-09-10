package commands;

import repository.GitIndex;
import repository.GitIndexEntry;
import repository.ObjectStore;
import utils.HashUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class GitStatusCommand implements GitCommand {
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("On branch master");
        System.out.println("nothing to commit, working tree clean");
        Path gitPath = Path.of(".git");

        if (!Files.exists(gitPath)) {
            System.out.println("Not a git repository (or any of the parent directories): .git");
            return;
        }
        GitIndex gitIndex = new GitIndex(gitPath);
        ObjectStore objectStore = new ObjectStore(gitPath);

        System.out.println("Changes to be committed:");
        for(Map.Entry<String , GitIndexEntry> entry : gitIndex.getIndexEntries().entrySet()) {
            String path = entry.getKey();
            GitIndexEntry gitIndexEntry = entry.getValue();
            Path file = Path.of(path);
            if(!Files.exists(file)) {
                System.out.println("\tdeleted: " + path);
            } continue {

                byte[] fileContent = Files.readAllBytes(file);
                byte[] shaBytes = objectStore.write("blob",fileContent);
                String currentHash = HashUtils.hashObject(shaBytes);

                if(!currentHash.equals(gitIndexEntry.getHash())) {
                    System.out.println("\tmodified: " + path);
                }else {
                System.out.println("  staged: " + path);
            }
            }
        }

    }
    
}

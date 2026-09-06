package commands;

import objects.Blob;
import repository.GitRepository;
import java.nio.file.Path;
import utils.HashUtils;

public class HashObjectCommand implements GitCommand {
    public void execute(String[] args) throws Exception {
        String fileName = args.length > 2 ? args[2] : args[1];
        byte[] sha = Blob.write(Path.of(fileName).toFile(), new GitRepository(Path.of(".")).objects());
        System.out.println(HashUtils.hex(sha));
    }
}
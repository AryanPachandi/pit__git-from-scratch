package transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GitHttpClient {
    public record RemoteRefs(String headSha, String defaultBranch) {}

    private final HttpClient client = HttpClient.newHttpClient();

    public RemoteRefs discover(String url) throws Exception {
        String refsUrl = url.endsWith("/")
                ? url + "info/refs?service=git-upload-pack"
                : url + "/info/refs?service=git-upload-pack";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(refsUrl)).GET().build();
        byte[] response = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        String targetSha = null;
        String defaultBranch = "main";
        ByteArrayInputStream input = new ByteArrayInputStream(response);
        while (input.available() > 0) {
            String line = readPktLine(input);
            if (line == null) continue;
            if (line.contains("HEAD") && targetSha == null) {
                String[] parts = line.split(" ");
                if (parts[0].length() >= 40) targetSha = parts[0].substring(parts[0].length() - 40);
            }
            if (line.contains("refs/heads/")) {
                int index = line.indexOf("refs/heads/");
                String branch = line.substring(index + 11).split("\0")[0].split(" ")[0]
                        .split("\n")[0].trim();
                if (line.contains("HEAD")) defaultBranch = branch;
                else if (targetSha != null && line.startsWith(targetSha)) defaultBranch = branch;
            }
        }
        return new RemoteRefs(targetSha, defaultBranch);
    }

    public byte[] fetchPack(String url, String targetSha) throws Exception {
        String uploadPackUrl = url.endsWith("/") ? url + "git-upload-pack" : url + "/git-upload-pack";
        String wantLine = "0032want " + targetSha + "\n00000009done\n";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uploadPackUrl))
                .header("Content-Type", "application/x-git-upload-pack-request")
                .POST(HttpRequest.BodyPublishers.ofString(wantLine)).build();
        byte[] response = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        return extractPackfileData(response);
    }

    public static String readPktLine(InputStream input) throws IOException {
        byte[] lengthBytes = input.readNBytes(4);
        if (lengthBytes.length < 4) return null;
        int length;
        try {
            length = Integer.parseInt(new String(lengthBytes, StandardCharsets.UTF_8), 16);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (length == 0 || length < 4) return "";
        return new String(input.readNBytes(length - 4), StandardCharsets.UTF_8);
    }

    private static byte[] extractPackfileData(byte[] payload) throws IOException {
        int packPosition = -1;
        for (int i = 0; i <= payload.length - 4; i++) {
            if (payload[i] == 'P' && payload[i + 1] == 'A' && payload[i + 2] == 'C' && payload[i + 3] == 'K') {
                packPosition = i;
                break;
            }
        }
        if (packPosition == 0) return payload;
        if (packPosition > 0 && payload[packPosition - 1] != 1) {
            return java.util.Arrays.copyOfRange(payload, packPosition, payload.length);
        }
        ByteArrayInputStream input = new ByteArrayInputStream(payload);
        ByteArrayOutputStream pack = new ByteArrayOutputStream();
        while (input.available() > 0) {
            byte[] lengthBytes = input.readNBytes(4);
            if (lengthBytes.length < 4) break;
            int length;
            try {
                length = Integer.parseInt(new String(lengthBytes, StandardCharsets.UTF_8), 16);
            } catch (NumberFormatException exception) {
                break;
            }
            if (length == 0) continue;
            if (length < 4) continue;
            byte[] line = input.readNBytes(length - 4);
            if (line.length > 0 && line[0] == 1) pack.write(line, 1, line.length - 1);
        }
        byte[] result = pack.toByteArray();
        return result.length > 0 ? result : payload;
    }
}
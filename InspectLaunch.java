import java.nio.file.*;
import java.util.regex.*;

public class InspectLaunch {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("/tmp/manifest.json"));
        Matcher m = Pattern.compile("\"id\": \"([^\"]+)\"").matcher(json);
        while (m.find()) {
            System.out.println("VERSION ID: " + m.group(1));
            if (m.group(1).startsWith("26")) {
                int start = m.start();
                System.out.println("FOUND: " + json.substring(start, start + 300));
            }
        }
    }
}

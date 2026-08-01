import java.nio.file.*;
import java.util.regex.*;

public class FindUrl {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("/tmp/mc1211.json"));
        Matcher m = Pattern.compile("https://piston-data.mojang.com[^\"]*server.jar").matcher(json);
        if (m.find()) {
            System.out.println("SERVER: " + m.group());
        }
    }
}

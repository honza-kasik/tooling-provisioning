import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

class MavenTestUtils {

    static File findMvnBinary() throws IOException, InterruptedException {
        String command = System.getProperty("os.name").toLowerCase().contains("win") ? "where mvn" : "which mvn";
        Process process = Runtime.getRuntime().exec(command);

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String mvnPath = reader.readLine();
        process.waitFor();

        if (mvnPath != null && !mvnPath.isEmpty()) {
            return new File(mvnPath);
        } else {
            return null;
        }
    }

}

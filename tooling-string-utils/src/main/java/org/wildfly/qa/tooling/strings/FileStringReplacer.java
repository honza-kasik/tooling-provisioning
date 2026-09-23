package org.wildfly.qa.tooling.strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility for replacing strings in file in-place
 */
public class FileStringReplacer {

    private Path fileToReplaceStringsIn;
    private ReplacementPairs replacementPairs;

    public FileStringReplacer(Path fileToReplaceStringsIn, ReplacementPairs replacementPairs) {
        this.fileToReplaceStringsIn = fileToReplaceStringsIn;
        this.replacementPairs = replacementPairs;
    }

    /**
     * Replace all pairs in the path
     * @return path with replaced pairs
     */
    public Path replaceAll() throws ReplacementException {
        String fileContent = null;
        try {
            fileContent = String.join("\n", Files.readAllLines(this.fileToReplaceStringsIn));
        } catch (IOException e) {
            throw new ReplacementException(e);
        }
        for (Map.Entry<String, String> pair : this.replacementPairs.getPairs().entrySet()) {
            fileContent = fileContent.replaceAll(pair.getKey(), pair.getValue());
        }
        try {
            Files.write(this.fileToReplaceStringsIn, fileContent.getBytes());
        } catch (IOException e) {
            throw new ReplacementException(e);
        }
        return this.fileToReplaceStringsIn;
    }
}

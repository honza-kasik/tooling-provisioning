package org.wildfly.qa.tooling.zip;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZipUtilsTest {

	final static String FILE_CONTENT = "Hello File";

	@Test
	public void test() throws IOException {
		// prepare directory to zip
		Path inputDir = Files.createTempDirectory("inputDir");
		FileUtils.writeStringToFile(new File(inputDir.toFile(), "test1.txt"), FILE_CONTENT, StandardCharsets.UTF_8);
		FileUtils.writeStringToFile(new File(inputDir.toFile(), "test2.txt"), FILE_CONTENT, StandardCharsets.UTF_8);
		// zip
		Path tmpDir = Files.createTempDirectory("tmpDir");
		File zipFile = new File(tmpDir.toFile(), "finalzip.zip");
		ZipUtils.zip(inputDir, zipFile);
		Assertions.assertTrue(zipFile.exists());
		// unzip
		Path outputDir = Files.createTempDirectory("outputDir");
		ZipUtils.unzip(zipFile.toPath(), outputDir);
		Assertions.assertTrue(new File(outputDir.toFile(), "test1.txt").exists());
		Assertions.assertTrue(new File(outputDir.toFile(), "test1.txt").exists());
	}
}

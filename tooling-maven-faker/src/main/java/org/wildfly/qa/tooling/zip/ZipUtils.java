package org.wildfly.qa.tooling.zip;


import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ZipUtils {


	/**
	 * Zip a directory recursively without adding a root folder in the zip
	 *
	 * @param inputDir directory to zip
	 * @param zipFile  zip file to produce
	 * @throws IOException if anything goes wrong
	 */
	public static void zip(Path inputDir, File zipFile) throws IOException {
		ZipArchiver zipArchiver = new ZipArchiver();
		zipArchiver.setDestFile(zipFile);
		zipArchiver.addDirectory(
				inputDir.toFile(),
				new String[]{"**/*"}, null);
		zipArchiver.createArchive();
	}

	/**
	 * Unzip a zip file in an output directory
	 *
	 * @param zipFile   zip file
	 * @param outputDir output directory
	 */
	public static void unzip(Path zipFile, Path outputDir) {
		ZipUnArchiver unArchiver = new ZipUnArchiver();
		unArchiver.setSourceFile(zipFile.toFile());
		unArchiver.extract("", outputDir.toFile());
	}
}

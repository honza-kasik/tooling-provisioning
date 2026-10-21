/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.qa.tooling.sbom.scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallationScannerTest {

    @TempDir
    Path root;

    @BeforeEach
    void createInstallation() throws IOException {
        touch("jboss-modules.jar");
        touch("modules/a/a-1.0.jar");
        touch("docs/examples/b-1.0.jar");
        touch("standalone/tmp/c-1.0.jar");
        touch(".installation/cache/d-1.0.jar");
        touch("modules/a/readme.txt");
    }

    @Test
    void skipsTheInstallationDirectoryByDefault() throws IOException {
        assertEquals(Set.of("jboss-modules.jar", "a-1.0.jar", "b-1.0.jar", "c-1.0.jar"),
                names(new InstallationScanner(root)));
    }

    @Test
    void skipsExactlyTheGivenPaths() throws IOException {
        InstallationScanner scanner = new InstallationScanner(root,
                List.of(Path.of("docs"), Path.of("standalone/tmp")));

        // .installation is not excluded any more, because it was not passed
        assertEquals(Set.of("jboss-modules.jar", "a-1.0.jar", "d-1.0.jar"), names(scanner));
    }

    @Test
    void defaultsCanBeKeptAlongsideOthers() throws IOException {
        InstallationScanner scanner = new InstallationScanner(root,
                List.of(InstallationScanner.DEFAULT_EXCLUDED.get(0), Path.of("docs")));

        assertEquals(Set.of("jboss-modules.jar", "a-1.0.jar", "c-1.0.jar"), names(scanner));
    }

    @Test
    void nothingIsSkippedWhenNothingIsExcluded() throws IOException {
        assertEquals(Set.of("jboss-modules.jar", "a-1.0.jar", "b-1.0.jar", "c-1.0.jar", "d-1.0.jar"),
                names(new InstallationScanner(root, List.of())));
    }

    @Test
    void anExcludedPathDoesNotExcludeSiblingsThatShareItsPrefix() throws IOException {
        touch("modules/abc/f-1.0.jar");
        InstallationScanner scanner = new InstallationScanner(root, List.of(Path.of("modules/a")));

        Set<String> names = names(scanner);
        assertFalse(names.contains("a-1.0.jar"), names.toString());
        assertTrue(names.contains("f-1.0.jar"), names.toString());
    }

    @Test
    void aPathThatDoesNotExistExcludesNothing() throws IOException {
        assertEquals(Set.of("jboss-modules.jar", "a-1.0.jar", "b-1.0.jar", "c-1.0.jar", "d-1.0.jar"),
                names(new InstallationScanner(root, List.of(Path.of("no/such/dir")))));
    }

    @Test
    void anAbsolutePathIsUsedAsItIs() throws IOException {
        InstallationScanner scanner = new InstallationScanner(root, List.of(root.resolve("docs")));

        assertFalse(names(scanner).contains("b-1.0.jar"));
    }

    @Test
    void pathsWithDotSegmentsAreNormalized() throws IOException {
        InstallationScanner scanner = new InstallationScanner(root, List.of(Path.of("modules/../docs/./examples")));

        assertFalse(names(scanner).contains("b-1.0.jar"));
    }

    private void touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private static Set<String> names(InstallationScanner scanner) throws IOException {
        return scanner.scan().stream().map(InstalledArtifact::jarFileName).collect(Collectors.toSet());
    }
}

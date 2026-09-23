package org.wildfly.qa.tooling.mavenfaker;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Create and install fake Maven artifacts
 *
 * This class was copied (and then slightly updated) from `tests-bootable-jar` project, see the readme for the details
 */
public class MavenArtifactFaker {

    public static final String DEFAULT_FAKE_ARTIFACT_TYPE = "jar";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String mavenRepoLocal;
    private final String packaging;
    private final String classifier;

    private Path artifactPathInMavenRepo;

    private MavenArtifactFaker(final Builder builder) {
        this.groupId = builder.groupId;
        this.artifactId = builder.artifactId;
        this.version = builder.version;
        this.mavenRepoLocal = builder.mavenRepoLocal;
        this.packaging = builder.packaging;
        this.classifier = builder.classifier;
    }

    /**
     * Install empty fake artifact in local maven repository
     * @throws IOException thrown when file operation fails
     */
    public void installFakeArtifactInLocalMavenRepo() throws IOException {
        installFakeArtifactInLocalMavenRepo(DEFAULT_FAKE_ARTIFACT_TYPE);
    }

    /**
     * Install empty fake artifact in local maven repository
     * @param fileExtension Extension which will be used for fake artifact
     * @throws IOException thrown when file operation fails
     */
    public void installFakeArtifactInLocalMavenRepo(final String fileExtension) throws IOException {
        final JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class, composeJarName(fileExtension))
                .add(new StringAsset("qux"), "foobar"); //one non-empty asset
        this.artifactPathInMavenRepo = composePathInMavenRepo(javaArchive.getName());
        Files.createDirectories(this.artifactPathInMavenRepo.getParent());
        javaArchive.as(ZipExporter.class).exportTo(this.artifactPathInMavenRepo.toFile(), true);
        writeSha1Sum(this.artifactPathInMavenRepo);
    }

    /**
     * Install empty "real" fake artifact in local maven repository. This artifact uses supplied archive as the "real"
     * fake artifact.
     * @throws IOException thrown when file operation fails
     */
    public void installRealFakeArtifactInLocalMavenRepo(Archive<?> archive) throws IOException {
        final String archiveName = this.artifactId + "-" + this.version + "." + FilenameUtils.getExtension(archive.getName());
        this.artifactPathInMavenRepo = composePathInMavenRepo(archiveName);
        Files.createDirectories(this.artifactPathInMavenRepo.getParent());
        archive.as(ZipExporter.class).exportTo(this.artifactPathInMavenRepo.toFile());
        writeSha1Sum(this.artifactPathInMavenRepo);
    }

    /**
     * Install empty "real" fake artifact in local maven repository. This artifact uses supplied archive as the "real"
     * fake artifact.
     * @throws IOException thrown when file operation fails
     */
    public void installRealFakeArtifactInLocalMavenRepo(String content, String extension) throws IOException {
        final String archiveName = this.artifactId + "-" + this.version + extension;
        this.artifactPathInMavenRepo = composePathInMavenRepo(archiveName);
        Files.createDirectories(this.artifactPathInMavenRepo.getParent());
        Files.writeString(this.artifactPathInMavenRepo, content);
        writeSha1Sum(this.artifactPathInMavenRepo);
    }

    public static void writeSha1Sum(final Path artifactPathInMavenRepo) throws IOException {
        Files.writeString(artifactPathInMavenRepo.getParent().resolve(artifactPathInMavenRepo.getFileName() + ".sha1"),
                DigestUtils.sha1Hex(Files.readAllBytes(artifactPathInMavenRepo)));
    }

    /**
     * Install "real" fake artifact in local maven repository; uses supplied "artifact" as the "real"
     * fake artifact.<br>
     * It creates all files that the "deploy:deploy-file" goal would normally create (.md5, .sha1, .pom);
     */
    public void installRealFakeArtifactInLocalMavenRepo(File mavenHome, Path artifact) throws ArtifactInstallException {
        final InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);
        request.setMavenHome(mavenHome);
        request.setLocalRepositoryDirectory(Paths.get(this.mavenRepoLocal).toFile());
        request.setShowErrors(true);
        request.addArg("deploy:deploy-file");
        request.addArg("-Durl=file://" + Paths.get(this.mavenRepoLocal).toFile().getAbsolutePath());
        request.addArg("-Dfile=" + artifact.toFile().getAbsolutePath());
        request.addArg("-DgroupId=" + this.groupId);
        request.addArg("-DartifactId=" + this.artifactId);
        request.addArg("-Dversion=" + this.version);
        request.addArg("-Dpackaging=" + this.packaging);
        request.addArg("-Dclassifier=" + this.classifier);

        DefaultInvoker invoker = new DefaultInvoker();
        InvocationResult result = null;

        try {
            result = invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new ArtifactInstallException(e);
        }

        if (result.getExitCode() != 0) {
            if (result.getExecutionException() != null) {
                throw new ArtifactInstallException("Error installing artifact " + artifact.toFile().getAbsolutePath(), result.getExecutionException());
            } else {
                throw new ArtifactInstallException("Error installing artifact " + artifact.toFile().getAbsolutePath());
            }
        }
    }

    /**
     * Get path of artifact in local maven repository
     * @return path or null if artifact was not yet installed
     */
    public Path getArtifactPathInMavenRepo() {
        return artifactPathInMavenRepo;
    }

    /**
     * Get {@code <groupId>/<artifactId>} path in Maven repository. Useful when you want to purge it.
     * @return G:A path in Maven repository
     */
    public Path getGaArtifactPath() {
        return this.artifactPathInMavenRepo.getParent().getParent();
    }

    private String composeJarName(String fileExtension) {
        return this.artifactId + "-" + this.version + "." + fileExtension;
    }

    private Path composePathInMavenRepo(final String artifactName) {
        final String[] groupIdSplit = this.groupId.split("\\.");
        final String groupIdPath = String.join(File.separator, groupIdSplit);
        return Paths.get(this.mavenRepoLocal, groupIdPath, this.artifactId, this.version, artifactName);
    }

    public static final class Builder {

        private String groupId;
        private String artifactId;
        private String version;
        private String mavenRepoLocal;
        private String packaging;
        private String classifier;

        public Builder() {
        }

        /**
         * Set fake group ID
         * @param groupId group ID which is going to be used
         * @return instance of this builder
         */
        public Builder groupId(final String groupId) {
            this.groupId = groupId;
            return this;
        }

        /**
         * Set fake artifact ID
         * @param artifactId artifact ID which is going to be used
         * @return instance of this builder
         */
        public Builder artifactId(final String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        /**
         * Set fake version
         * @param version version which is going to be used
         * @return instance of this builder
         */
        public Builder version(final String version) {
            this.version = version;
            return this;
        }

        /**
         * Set path to local maven repository where artifact is going to be installed
         * @param mavenRepoLocal absolute path to local maven repository
         * @return instance of this builder
         */
        public Builder mavenRepoLocal(final String mavenRepoLocal) {
            this.mavenRepoLocal = mavenRepoLocal;
            return this;
        }

        /**
         * Set packaging
         * @param packaging packaging
         * @return instance of this builder
         */
        public Builder packaging(final String packaging) {
            this.packaging = packaging;
            return this;
        }

        /**
         * Set classifier
         * @param classifier classifier
         * @return instance of this builder
         */
        public Builder classifier(final String classifier) {
            this.classifier = classifier;
            return this;
        }

        private void validate() {
            if (this.artifactId == null) {
                throw new IllegalArgumentException("Artifact ID cannot be null!");
            }
            if (this.groupId == null) {
                throw new IllegalArgumentException("Group ID cannot be null!");
            }
            if (this.version == null) {
                throw new IllegalArgumentException("Version cannot be null!");
            }
            if (this.mavenRepoLocal == null) {
                throw new IllegalArgumentException("Maven repo local path cannot be null!");
            }
        }

        public MavenArtifactFaker build() {
            validate();
            return new MavenArtifactFaker(this);
        }

    }

}

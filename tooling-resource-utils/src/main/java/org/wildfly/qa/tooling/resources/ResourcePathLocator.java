package org.wildfly.qa.tooling.resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourcePathLocator {

    /**
     * Obtain full absolute path of a resource when only relative path in resources directory is known
     * @param resourceRelativePath Relative path in resources directory
     * @return Path of the resource
     */
    public static Path getResourceAbsolutePath(final String resourceRelativePath) {
        final URL url = Thread.currentThread().getContextClassLoader().getResource(resourceRelativePath);
        if (url == null) {
            throw new IllegalStateException("URL of obtained resource from '" + resourceRelativePath + "' is null!");
        }
        final URI pathUri;
        try {
            pathUri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("URL was not properly formatted to be converted to URI!", e);
        }
        return Paths.get(pathUri);
    }

}

import org.wildfly.qa.tooling.version.SemanticVersionAssume;
import org.wildfly.qa.tooling.version.VersionAssume;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionAssumeTest {

    @ParameterizedTest
    @CsvSource({"1.0.0,1.0.1", "1.0.10,1.0.11", "1.0.9,1.0.10", "1.0.5.foobar-00001,1.0.6.foobar-00001"})
    public void testSemanticVersionAssumeNext(String version, String expected) {
        final VersionAssume versionAssume = new SemanticVersionAssume(version);
        assertEquals(expected, versionAssume.nextVersion());
    }

    @ParameterizedTest
    @CsvSource({"1.0.1,1.0.0", "1.0.11,1.0.10", "1.0.10,1.0.9", "1.0.0,0.9.9", "1.0.6.foobar-00001,1.0.5.foobar-00001"})
    public void testSemanticVersionAssumePrevious(String version, String expected) {
        final VersionAssume versionAssume = new SemanticVersionAssume(version);
        assertEquals(expected, versionAssume.previousVersion());
    }

}

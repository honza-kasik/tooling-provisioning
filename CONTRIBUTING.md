# Contributing to tooling-provisioning

## Testing

`mvn verify` runs the unit tests. Run `mvn verify -Pintegration-tests` to also
exercise process execution and SBOM verification against a WildFly distribution
downloaded from Maven Central.

These tests verify the tooling code, using a fixed community WildFly installation
as a fixture. Testing WildFly itself, including early testing and coverage of its
Preview and EE flavors, is outside this project's scope. One community distribution
is sufficient to exercise the tooling's behavior.

The parent POM owns the shared integration-test setup and pins one
`version.wildfly.test` for all modules.
The pinned version must include SBOMs (currently `42.0.0.Beta1`, the existing SBOM
test fixture). To change the fixture for every module in one run, use
`mvn verify -Pintegration-tests -Dversion.wildfly.test=<version>`.

To run just one module, add `-pl tooling-process-execution` or
`-pl tooling-sbom-verification`. Both modules use the community WildFly
distribution, unpacked under each module's `target/wildfly/` directory.

An existing installation can be selected with `-Dwildfly.home=/path/to/wildfly`.
The profile still downloads and unpacks the pinned distribution when this path
is supplied.

## Java style

Use explicit types for local variable declarations in production code and tests.
Do not use `var`.

## Test documentation

Document every new or modified test method with a short Javadoc description of
the scenario being tested and explicit passing criteria. State the expected
observable result, including relevant error cases. Apply this to both unit and
integration tests; a descriptive method name alone is not sufficient.

## Releasing

Artifacts are published to JBoss Nexus at `repository.jboss.org`. Releases are signed with GPG.

### Prerequisites

1. **JBoss Nexus credentials** — add to `~/.m2/settings.xml`:
   ```xml
   <servers>
     <server>
       <id>jbossqe-eap-releases-repository</id>
       <username>your-username</username>
       <password>your-password</password>
     </server>
     <server>
       <id>jboss-snapshots-repository</id>
       <username>your-username</username>
       <password>your-password</password>
     </server>
   </servers>
   ```
2. **GPG key and agent** — install and configure `gpg` with a signing key. The `maven-gpg-plugin`
   will use your default key automatically. If you need a specific key, set `-Dgpg.keyname=<KEY_ID>`.

   Make sure `gpg-agent` is running so you're not prompted for the passphrase on every artifact:
   ```bash
   gpgconf --launch gpg-agent
   echo "test" | gpg --clearsign > /dev/null   # cache the passphrase once
   ```

### Release steps

```bash
# 1. Make sure you're on main with a clean working tree
git checkout main
git pull

# 2. Run integration tests to verify everything works
mvn verify -Pintegration-tests

# 3. Prepare the release (updates versions, creates tag)
mvn release:prepare

# 4. Perform the release (builds, signs, deploys to Nexus)
mvn release:perform

# 5. Push the tag and version commits
git push && git push --tags
```

The `release:prepare` step will prompt for the release version, tag name, and next
development version. Defaults are usually fine.

### Deploying snapshots

To deploy a snapshot without a full release:

```bash
mvn deploy
```

This publishes to the `jboss-snapshots-repository` configured in `distributionManagement`.

## System Property Naming Convention

When adding configurable system properties to any module in this project, follow these rules:

### Prefix

Use `org.wildfly.qa.` — this matches the namespace used by other upstreamed QE tooling
(creaper: `org.wildfly.extras.creaper`, dist-diff: `org.wildfly.qa.distdiff2`).

### Format

```
org.wildfly.qa.{domain}.{detail}
```

Example: `org.wildfly.qa.server.process.timeout.minutes`

### Centralize per module

Each module that reads system properties should have a `*Properties` class
(e.g. `ProcessExecutionProperties` in `tooling-process-execution`) containing:

- All property names as `public static final String` constants
- Typed accessor methods with sensible defaults
- Javadoc explaining what each property controls

Do **not** scatter `System.getProperty()` calls across multiple classes.

### Downstream reference

Testsuites that consume these properties should reference the library's constants
rather than duplicating the property name strings. For example:

```java
// Good — single source of truth
ProcessExecutionProperties.serverProcessTimeoutMinutes()

// Bad — duplicated string, will drift
Long.parseLong(System.getProperty("org.wildfly.qa.server.process.timeout.minutes", "5"))
```

### Defaults

Always provide a sensible default via `System.getProperty(name, default)`.
Properties should be optional — the library must work without any custom properties set.

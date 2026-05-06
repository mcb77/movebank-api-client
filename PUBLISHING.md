# Publishing workflow

This file documents how artifacts in the `de.firetail.compat.movebank.*`
family are released. It covers all four repos:

| Repo                   | Artifact                                                         | Channel                  |
|------------------------|------------------------------------------------------------------|--------------------------|
| `movebank-api-client`  | `de.firetail.compat.movebank:movebank-api-client:<version>`      | **Maven Central**        |
| `movebank-mirror`      | `de.firetail.compat.movebank:movebank-mirror:<version>` (`:lib`) | **Maven Central**        |
| `movebank-mirror`      | `movebank-mirror` CLI distribution archive (`:cli`)              | **GitHub Releases**      |
| `movebank-mirror-api`  | Spring Boot application distribution archive                     | **GitHub Releases**      |

Libraries → Central. Applications → GitHub Releases (no Maven coords; users
download a tarball and run `bin/<name>`).

---

## Part 1. One-time setup

These steps are done once per developer/machine. After they're in place, every
release reuses them.

### 1.1 Sonatype Central account + namespace

1. Create an account at <https://central.sonatype.com>.
2. Register the top-level namespace `de.firetail` under
   *View Account → Namespaces → Add Namespace*. Sonatype proves ownership via
   a DNS TXT record on `firetail.de` (instructions on the Portal).
3. Once verified, the namespace and **all sub-namespaces** are publishable —
   you don't need to register `de.firetail.compat.movebank` separately. Same
   approval covers anything below `de.firetail.*`.

### 1.2 Generate a Central user token

1. <https://central.sonatype.com/account> → *User Tokens* → *Generate Token*.
2. Save the `username:password` pair shown (only displayed once).
3. The publishing plugin wants this **base64-encoded** for HTTP Bearer auth:

   ```bash
   printf '%s' '<token-username>:<token-password>' | base64 -w0
   ```

   The result is a single line; that's the value to set as
   `mavenCentralAuthToken` (see §1.4 for how it gets passed to Gradle).

### 1.3 PGP signing key

Maven Central requires every artifact, POM, and source/javadoc jar to be
signed. The `signing` block in each lib's `build.gradle` uses
`useInMemoryPgpKeys(PGP_KEY, PGP_PASSPHRASE)`, so the secret key is consumed
from environment variables — no file at `~/.gnupg` needed at build time.

```bash
# 1. Generate a 4096-bit RSA key, expiry 2y (renew before expiry).
gpg --full-generate-key

# 2. Find the long key ID.
gpg --list-secret-keys --keyid-format=long
#  sec   rsa4096/AB12CD34EF567890 ...
export KEY_ID=AB12CD34EF567890

# 3. Publish the public key to the keyservers Central polls.
gpg --keyserver hkps://keys.openpgp.org    --send-keys $KEY_ID
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys $KEY_ID
gpg --keyserver hkps://pgp.mit.edu          --send-keys $KEY_ID

# 4. Verify the email on your UID at keys.openpgp.org — it sends a
#    confirmation link. Without verification the UID is stripped and the key
#    is effectively anonymous to Central's verifier.

# 5. Export the secret key (ASCII-armored). Treat like an SSH private key.
gpg --armor --export-secret-keys $KEY_ID > pgp-secret.asc
```

The `*.asc` glob is already in `.gitignore` for every repo in the family.
Don't commit `pgp-secret.asc`.

### 1.4 Environment variables

The build files read three properties via `findProperty(...)`. Gradle resolves
these from `~/.gradle/gradle.properties` first, then `-P` flags, then env vars
prefixed with `ORG_GRADLE_PROJECT_`. The env-var path is preferred for CI;
gradle.properties is fine for local dev.

| Property                  | Value                                                   |
|---------------------------|---------------------------------------------------------|
| `mavenCentralAuthToken`   | base64-encoded `<token-user>:<token-pass>` (§1.2)       |
| `PGP_KEY`                 | ASCII-armored secret key block (§1.3, contents of `pgp-secret.asc`) |
| `PGP_PASSPHRASE`          | passphrase you chose for the PGP key                    |

**Local shell (one-off):**

```bash
export ORG_GRADLE_PROJECT_mavenCentralAuthToken="$(printf '%s' '<user>:<pass>' | base64 -w0)"
export PGP_KEY="$(cat pgp-secret.asc)"
export PGP_PASSPHRASE='your-passphrase'
```

The `$(cat …)` form preserves newlines inside `PGP_KEY`, which the armored
format requires.

**Persistent local (`~/.gradle/gradle.properties`):** safer for everyday work
because nothing leaks via `ps`/shell history:

```
mavenCentralAuthToken=<base64-encoded user:pass>
```

(`PGP_KEY` is multi-line so it's awkward in `gradle.properties`; keep that as
an env var or in a sourced shell script outside the repo.)

**GitHub Actions:** add three repository secrets at *Settings → Secrets and
variables → Actions* — `MAVEN_CENTRAL_AUTH_TOKEN`, `PGP_KEY`, `PGP_PASSPHRASE`
— and surface them via `env:` in the publish workflow (see §3.4).

---

## Part 2. Releasing a library to Maven Central

The two libraries (`movebank-api-client`, `movebank-mirror:lib`) follow the
same recipe. Differences are noted inline.

### 2.1 Update the version

```groovy
// movebank-api-client/build.gradle
version '0.0.3'

// movebank-mirror/build.gradle  (allprojects { ... })
version = '0.0.2'
```

Versions are independent per repo.

### 2.2 Smoke-test signing locally

Before pushing anything to Central, confirm signing produces the expected
`.asc` files:

```bash
./gradlew :lib:publishToMavenLocal      # (or just :publishToMavenLocal in api-client)
ls ~/.m2/repository/de/firetail/compat/movebank/<artifactId>/<version>/
# should include  *.jar  *.jar.asc  *-sources.jar.asc  *-javadoc.jar.asc  *.pom.asc
```

If `.asc` files are missing, the `signing` block isn't picking up `PGP_KEY` —
re-check §1.4.

### 2.3 Publish

The `tech.yanand.maven-central-publish` plugin adds task
`publishToMavenCentralPortal`. **`./gradlew publish` does not invoke this**
on its own — `publish` only runs `maven-publish`'s tasks, which (without a
declared `repositories { ... }` block) do nothing externally.

```bash
# Foundation library — no other internal deps
cd movebank-api-client
./gradlew publishToMavenCentralPortal

# Library that depends on movebank-api-client — wait for that to be PUBLISHED
# (Part 2.5) before running this.
cd movebank-mirror
./gradlew :lib:publishToMavenCentralPortal
```

Successful output ends with:

```
Upload success, response body: <deploymentId>
Checking deployment status, response body: {... "deploymentState":"PUBLISHING" ...}
Upload file success! current status: PUBLISHING.
```

### 2.4 Watch the deployment finish

The state machine on the Portal:

```
VALIDATING → VALIDATED → PUBLISHING → PUBLISHED
                                  ↓
                              (FAILED at any stage with errors[])
```

`build.gradle` sets `publishingType = 'AUTOMATIC'`, so VALIDATED transitions
to PUBLISHING without a manual click. Live view at
<https://central.sonatype.com/publishing/deployments>. Typical end-to-end
time: 15–60 minutes.

If a stage fails, the Portal lists the errors per-file (missing `.asc`,
unverified signing key, malformed POM, etc.). Fix locally and re-run
`publishToMavenCentralPortal`.

### 2.5 Verify resolvability

Once `PUBLISHED`:

```bash
curl -sI https://repo1.maven.org/maven2/de/firetail/compat/movebank/<artifactId>/<version>/<artifactId>-<version>.pom \
     | head -1
# expect:  HTTP/2 200
```

Confirm a downstream resolves it from Central rather than `includeBuild`:

```bash
cd ../movebank-mirror
sed -i.bak 's/^localMovebankApiClient=true/#&/' gradle.properties
./gradlew :lib:dependencies --configuration runtimeClasspath | grep movebank-api-client
mv gradle.properties.bak gradle.properties
```

The dependency line should resolve from Central, not from `project(':')`.

### 2.6 Tag the git release

```bash
git tag -a v0.0.3 -m "Release 0.0.3"
git push origin v0.0.3
```

Tag name matches the artifact version. Use a separate `v<version>` per repo;
they're not in lockstep.

### 2.7 Family release order

`movebank-mirror:lib` declares an `api` dependency on `movebank-api-client`,
so its POM resolution depends on the latter being on Central. Publish in
this order:

1. `movebank-api-client`
2. Wait until step 1 is `PUBLISHED` (verify §2.5)
3. `movebank-mirror:lib`

`BasicImport` doesn't need to be published — it's a private consumer that
uses `includeBuild` for local dev or pulls Central artifacts otherwise.

---

## Part 3. Releasing an application via GitHub Releases

`movebank-mirror-api` and `movebank-mirror:cli` are runnable applications, not
libraries. They're distributed as self-contained archives via GitHub
Releases instead of Maven Central.

### 3.1 Build the distribution archive

```bash
# Spring Boot app
cd movebank-mirror-api
./gradlew bootDistTar bootDistZip
# → build/distributions/movebank-mirror-api-<version>.{tar,zip}

# CLI distribution from the multi-module repo
cd movebank-mirror
./gradlew :cli:distTar :cli:distZip
# → cli/build/distributions/movebank-mirror-cli-<version>.{tar,zip}
```

Each archive contains a `bin/` launcher + `lib/` jars. Users unpack and run
`bin/<name>`; no Java install needed beyond a JRE 21 on `PATH`.

### 3.2 Tag and create the release

```bash
git tag -a v0.0.1 -m "Release 0.0.1"
git push origin v0.0.1

gh release create v0.0.1 \
    build/distributions/*.tar build/distributions/*.zip \
    --title "v0.0.1" \
    --notes "First release. See README.md for usage."
```

For `movebank-mirror`'s CLI, attach the cli-only archives to a release on
that repo (don't conflate with library releases):

```bash
gh release create cli-v0.0.1 \
    cli/build/distributions/*.tar cli/build/distributions/*.zip \
    --title "CLI v0.0.1" \
    --notes "CLI distribution. Library is on Maven Central as movebank-mirror:0.0.1."
```

The `cli-v` prefix keeps lib and CLI release tags from colliding in the same
repo.

### 3.3 Verify the artifact

```bash
gh release download v0.0.1 -p '*.zip' -D /tmp/release-check
unzip -q /tmp/release-check/*.zip -d /tmp/release-check/unpacked
/tmp/release-check/unpacked/*/bin/<name> --help
```

### 3.4 Optional: GitHub Actions publish workflow

A reusable workflow that runs on tag push, suitable for any of the four
repos:

```yaml
# .github/workflows/publish.yml
name: Publish

on:
  push:
    tags: ['v*']
  workflow_dispatch:

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4

      # Library repos: publish to Central
      - name: Publish to Maven Central
        if: ${{ matrix.kind == 'library' }}
        env:
          ORG_GRADLE_PROJECT_mavenCentralAuthToken: ${{ secrets.MAVEN_CENTRAL_AUTH_TOKEN }}
          PGP_KEY:        ${{ secrets.PGP_KEY }}
          PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
        run: ./gradlew publishToMavenCentralPortal

      # Application repos: build distribution and attach to GitHub Release
      - name: Build distribution
        if: ${{ matrix.kind == 'application' }}
        run: ./gradlew bootDistTar bootDistZip
      - name: Create GitHub Release
        if: ${{ matrix.kind == 'application' && startsWith(github.ref, 'refs/tags/v') }}
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release create "${GITHUB_REF_NAME}" \
              build/distributions/*.tar build/distributions/*.zip \
              --title "${GITHUB_REF_NAME}" \
              --notes "Auto-generated release."
```

(Drop the `matrix.kind` conditionals and keep only the relevant block per
repo.)

---

## Part 4. Troubleshooting

### `BUILD SUCCESSFUL` but nothing in the Portal

`./gradlew publish` only invokes `maven-publish`'s tasks, which without a
declared `repositories { ... }` block do nothing externally. Run
`publishToMavenCentralPortal` explicitly. List discoverable tasks:

```bash
./gradlew tasks --all | grep -iE 'central|portal|bundle'
```

### `401 Invalid token`

Token must be **base64-encoded** `username:password`. Plain `<user>:<pass>`
or just the password fails:

```bash
ORG_GRADLE_PROJECT_mavenCentralAuthToken="$(printf '%s' '<user>:<pass>' | base64 -w0)"
```

Sanity-check the round-trip:

```bash
echo -n "$ORG_GRADLE_PROJECT_mavenCentralAuthToken" | base64 -d
# should print:  <user>:<pass>
```

### `403 Namespace not allowed`

Either the namespace is unverified or the artifact's `groupId` doesn't match
your verified namespace. Check at
<https://central.sonatype.com/publishing/namespaces> — you should have
`de.firetail` verified, which automatically permits anything under
`de.firetail.*`.

### Validation fails on missing `.asc` for sources/javadoc

The publishing block must include `withSourcesJar()` + `withJavadocJar()` and
`signing { sign publishing.publications.mavenJava }`. The `signing` plugin
auto-attaches signatures to all artifacts of the publication.

### Validation fails on POM completeness

Central requires the POM to declare `name`, `description`, `url`, `licenses`,
`developers`, and `scm`. All three repos already have these in the
`publishing { ... pom { ... } }` block.

### Key not visible to Central

After `gpg --send-keys`, propagation can take minutes to an hour. Verify with
a fresh client (or different keyserver):

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys $KEY_ID
```

For `keys.openpgp.org` specifically, the server **strips UIDs** until the
email on the UID is verified by clicking a link sent to it. Without that
click, Central sees the key but no identity attached and rejects signatures
as "unverified."

### Token leaked in shell history / chat / logs

Revoke immediately at <https://central.sonatype.com/account> → User Tokens →
Revoke, then generate a fresh one. Cheap to redo, expensive to skip.

### PGP key expired

```bash
gpg --edit-key $KEY_ID
> expire           # extends primary key
> key 1
> expire           # extends signing subkey
> save
gpg --keyserver hkps://keys.openpgp.org --send-keys $KEY_ID
```

Then re-export and update `PGP_KEY` in your env / CI secrets.

---

## Part 5. Per-release checklist

Copy this when cutting a release:

```
[ ] version bump in build.gradle
[ ] CHANGELOG / git log entry
[ ] ./gradlew :<module>:publishToMavenLocal — verify .asc siblings exist
[ ] (libraries) ./gradlew :<module>:publishToMavenCentralPortal
[ ] (apps)      ./gradlew bootDistTar bootDistZip   (or :cli:distTar :cli:distZip)
[ ] watch Portal Deployments tab → PUBLISHED       (libraries)
[ ] curl repo1.maven.org pom — HTTP 200            (libraries)
[ ] git tag -a v<version> -m '...'  &&  git push origin v<version>
[ ] (apps) gh release create v<version> <archives> --title ... --notes ...
[ ] dependent repos: re-enable localXxx flags off, verify they resolve from Central
```

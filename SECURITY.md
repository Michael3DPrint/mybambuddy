# Security

MyBambuddy is a personal project; only the latest `main` is supported.
Report a vulnerability privately; the app deliberately uses plain HTTP, which is a design limit and not a bug to report.
The scan results below are a snapshot against the dependency versions listed, not a guarantee.

## Reporting a vulnerability

Use GitHub's "Report a vulnerability" button on this repository's Security tab, and do not post exploit details in a public issue.
There is no guaranteed response time; reports are read when the maintainer can.

## Design limits

- The app talks to your own Bambuddy server over **plain HTTP**.
  There is no HTTPS.
  Use it on your home network or over a VPN such as Tailscale, and never put the server on the open internet.
  See `docs/design/18-cleartext-and-url-warning.md`.
- Bambuddy's API key is stored in encrypted storage on the phone.
- Spoolman and the Bambuddy slicer sidecar have no authentication of their own.

## Scans

Scanned against Android Gradle plugin 9.5.1 and Kotlin plugin 2.2.10, with Guava 33.7.2 in the app.

| Check | Result |
|---|---|
| gitleaks (git history and files), trufflehog (offline, no live verification) | no secrets found |
| osv-scanner and trivy on the resolved release runtime dependencies | no known vulnerabilities (Guava is pinned to 33.7.2 for GHSA-xxph-c9ww-hj94) |
| semgrep (Kotlin, security-audit, secrets and OWASP packs) | one finding: the launcher activity is exported, which it must be |
| trivy on repository files (secrets, misconfiguration) | no findings |
| Android Lint and the project's own standards checks | part of `./gradlew :app:check` |

Not done: an APK analysis (for example MobSF), a penetration test, and any review by a second person.

## Known advisories in build tooling only

The libraries that the Android Gradle plugin and the Kotlin plugin use to build the app are not shipped in the APK.
They have known advisories in these versions: Bouncy Castle `bcprov-jdk18on` and `bcpkix-jdk18on` 1.80.2, `jose4j` 0.9.5, `jdom2` 2.0.6, `commons-lang3` 3.16.0, Guava 33.4.0 (JVM variant) and the Kotlin Gradle plugin 2.2.10.
They affect only a developer's computer while building, for example when a build tool parses untrusted input.
They are not overridden, because overriding a build tool's own dependencies can break signing and the build.
The fixed versions are taken when new releases of the Android Gradle plugin and Kotlin plugin include them.

## Supply chain

Dependency versions are fixed (no version ranges), and the Gradle wrapper has its published checksum.
`gradle/verification-metadata.xml` records SHA-256 hashes of every resolved artifact, so a changed artifact fails the build.

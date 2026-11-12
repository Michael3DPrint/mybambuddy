# Engineering standards

These rules apply to every change, by every contributor, human or AI assistant.
Checks marked **[build]** are enforced by Gradle and fail `./gradlew :app:check`; **[ci]** also runs on GitHub; the rest are enforced in review.
The app is **MyBambuddy**; "PrintDeck" names only the Printers tab.
Where the code departs from a rule, the departure is named in the rule and tracked in `docs/OPEN_ITEMS.md` (OI-61).

## 0. Process rule: design before code

For any new feature or behavior change, write `docs/design/NN-name.md` first (NN is the highest number plus one) and show it.
It contains:

1. **Wireframe(s)** (ASCII is fine) of every new or changed screen.
2. **Flowchart** of the user flow, including error and empty paths.
3. **Data-flow diagram**: which screen goes through which ViewModel, repository and API call or storage, and which model flows back.
4. **Write-up**: goal, decisions and why, alternatives rejected, risks, open questions, test plan.

Then code.
Tiny bug fixes (a few lines, no new behavior) are exempt.
A change to a reviewed feature updates its design in the same change, so the design always matches the code.
A design carries no status; live status is in `docs/OPEN_ITEMS.md`.

## 1. Style

- Kotlin Coding Conventions and the Android Kotlin Style Guide, formatted with ktlint (`android_studio` code style, 140 columns, see `.editorconfig`). **[build]**
- Kotlin compiler: warnings are errors. **[build]**
  Never `@Suppress` to get past one without a comment saying why.
- Names: types `PascalCase`, functions and variables `camelCase`, constants `SCREAMING_SNAKE`, composables `PascalCase` nouns.
  No abbreviations except well-known ones (HMS, AMS, NFC).
- One primary public type per file, named after the file.
  Closely related small types may share it: the cases of a sealed hierarchy, an enum with the class that uses it, a screen's state classes with its ViewModel, a cache class with its decorator, or the DTOs of one API call.
  Unrelated types get their own files.
- A source file has at most 400 lines; split before that. **[build]**
- Prefer immutable data (`val`, `data class`, `List` rather than `MutableList`).
- No `!!` in app code. **[build]**
- No `lateinit` except for Android lifecycle and test setup.

## 2. Architecture and SOLID, applied here

Flow: `server JSON -> DTO (data/api/dto) -> mapper -> domain model (domain/model) -> repository interface (domain/repo) -> ViewModel -> UiState -> Screen`.

- **Single responsibility.**
  A repository serves one concern (`SpoolRepository`, `CatalogRepository`, `AmsRepository`, `PrinterRepository`, ...).
  A ViewModel serves one screen or one flow.
  A Screen only renders state and forwards events.
  Mapping and formatting live in mappers and the domain layer, never in a Screen.
  A ViewModel has at most 8 public functions. **[build]**
- **Open/closed.**
  Add behavior by adding a type (a command, a repository, a rule), not by widening `when`s in many places.
  New control actions go through `PrinterControlCommand`.
- **Liskov.**
  Every implementation honors its interface's documented contract, including error results.
  Test fakes implement only the small API interface they need.
- **Interface segregation.**
  Depend on the narrowest interface.
  Retrofit interfaces are small and per area (`PrinterApi`, `PrinterControlApi`, `CameraApi`, `HistoryApi`, `InventoryApi`, `AmsApi`, `SystemApi`, `QueueApi`, ...).
  Add new calls to the matching one, or to a new one, never to a catch-all.
  Every endpoint declared in `data/api` is listed in `docs/API_MAP.md`. **[build]**
- **Dependency inversion.**
  ViewModels receive interfaces through constructors and factories.
  `AppContainer` (`di/`) builds the concrete repositories and `MainActivity` wires the ViewModels to the screens.
  Stateless rule objects (`object` with pure functions, such as the policies) are fine.
  Mutable global state is limited to `SnapshotCache` and the log settings (`LogPolicy`, the logger's sink).
- **Layering departures (OI-61).**
  Accepted: UI code imports `ApiResult`, `ApiErrorType` and `toUiErrorState` from `data/api`, and the domain layer imports `ApiResult` and, in the mappers, DTOs.
  Not accepted, and tracked: the Setup and Settings ViewModels build the connection-test client themselves, and the Setup screens create the Wi-Fi reader and the health probe in composable code instead of receiving them from `AppContainer`.
- **Printer commands and Developer LAN mode.**
  Anything that sends a command to the printer is declared in `PrinterTool` with `needsDeveloperMode`, the single table of what to switch off in limited mode (design 10).
  A new control means a new `PrinterTool` entry and a gate (`PrinterToolGate`); a `when` over commands stays exhaustive.
- **One readiness API.**
  What a printer can do now is decided in one place: `ReadinessCheck` reads, `ReadinessEvaluator` and `ToolAvailability` decide.
  Screens render readiness; they do not write a second evaluator.
  Where a screen shows a list, the ViewModel evaluates it once into its UiState (done for the Printers screen).
  The detail and AMS screens call the evaluator for the one printer they show, which is accepted.
- **Two gates, opposite failure modes, on purpose.**
  A slot change fails **closed**: `SlotLoadPolicy.preflight` refuses when the printer's state cannot be read, and re-reads the printer right before the write.
  The reason is that the change reconfigures hardware and may overwrite a Bambu RFID spool.
  Other printer commands go through `PrinterToolGate` and `DeveloperModeGuard` and fail **open** when status cannot be read.
  The reason is that the printer and Bambuddy still refuse what they cannot do, and blocking every control on a flaky read would be worse.
  Confirmation is separate from both: see section 10.
- **Client-side refusals** return `ApiErrorType.UNSUPPORTED` with a message that says why and what to do; `toUiErrorState` shows it as written.
  Test the message through the UI path, not only the repository.
- **Test doubles live in test sources**, never in `main`.
- DTO fields are nullable with defaults; domain models are non-null with safe fallbacks.
  Raw JSON, Moshi errors and stack traces never reach the UI.
- **User-facing text.**
  The app is English only.
  Older screens keep plain texts in string resources; newer screens (the print flow, queue job, Setup help) use string literals.
  Which policy applies is an open decision tracked in OI-22.
  Safety and policy messages stay in Kotlin either way.
  These are the refusals, gates and confirmations produced in `domain/policy` and the repositories, which design 10 pins by test.
  String resources would put them out of reach of the JVM tests.

## 3. Resilience and operations

- **Timeouts everywhere.**
  The shared client uses 10 s connect, read and write timeouts and a 30 s limit on the whole call.
  The camera client reads for 20 s.
  The diagnostics client waits longer (45 s read, 60 s whole call) because the server waits for the printer (design 20).
  A health probe of a network scan uses its own client with sub-second limits.
  No unbounded waits.
- **Retries.**
  Only for idempotent reads, bounded.
  `ReadRetryInterceptor` repeats a dropped GET or HEAD once (a closed keep-alive connection).
  **Never retry a control command** (pause, drying, light, slot assignment, start): writes are single attempts and OkHttp's own retry stays off.
- **Fail fast, degrade gracefully.**
  Validate input on the client before calling (for example drying 45 to 85 C).
  On a failed poll keep the last data and show a banner; never blank the screen.
- **Idempotency.**
  Link-tag and assign-slot calls are safe to repeat; handle 409 conflicts explicitly.
- **Least privilege.**
  The manifest asks only for `INTERNET`, `ACCESS_NETWORK_STATE` (Wi-Fi and VPN checks), `ACCESS_LOCAL_NETWORK` (Android 17 blocks home-network connections without it), `NFC`, `POST_NOTIFICATIONS`, plus what WorkManager adds.
  It queries one package (the Tailscale app) and nothing else.
  No storage permissions, no location.
  A new permission needs a line in the design that adds it.
- **Operational readiness.**
  Log failures with a tag and a reason, never secrets (release builds log no server text and no stack traces).
  Every error shown to the user says what failed and what to do next, and links to help (section 10).
- **Test the contract.**
  Unit tests use redacted fixtures captured from the real server (`app/src/test/resources/fixtures`).

## 4. Security and privacy

- The API key lives only in encrypted storage.
  It is never logged, committed, put in a URL, written to a fixture, or sent to an address that is only being probed (the discovery client is separate and carries no key). **[review]**
- Fixtures, docs and code contain no access codes, serial numbers, tokens, personal data or real server addresses; docs use `<server>`.
  The build fails on a Tailscale-range address or a full `*.ts.net` name outside the files listed in `.pii-guard-allow`. **[build]**
  The rest is **[review]**, backed by secret scanners (below).
- Control commands and any write to the owner's printer, phone, servers or accounts are never sent "as a test".
  Verify with MockWebServer unit tests and a fake or local server, then ask the owner to try.
  This includes repositories and settings on hosting services: creating, publishing or deleting needs the owner's say-so.
- **Dependencies.**
  A new dependency needs a reason in the ledger; prefer none.
  Versions are fixed (no ranges).
  `gradle/verification-metadata.xml` records SHA-256 hashes for every resolved artifact and the Gradle wrapper has its checksum, so a changed artifact fails the build. **[build]**
  Regenerate the metadata after adding or upgrading a dependency (the README has the command; the manual `verification-metadata` workflow does it on Linux).
  Licenses of the shipped libraries are summarized in `THIRD_PARTY_NOTICES.md`.
- **Scans before publishing and after dependency changes:** gitleaks and trufflehog (secrets), osv-scanner and trivy (known vulnerabilities in the resolved dependencies and the build tooling), semgrep (code).
  Findings and known build-tooling advisories are recorded in `SECURITY.md` with the versions scanned; do not claim more than the scans covered.

## 5. Documentation

- Every public class, interface, function and member of a type in `domain/` and `data/`, and every public top-level declaration in `ui/`, has KDoc of at least 12 characters.
  It states *what* and any non-obvious *why* or contract (units, nullability, errors). **[build]**
- Comments explain why, not what, and state facts that can be checked.
  When a fact comes from outside the code, name its source: a Bambuddy route or source file, a wiki page, or a standard.
  Do not write history, dates, people, tool names, to-do markers or finding IDs.
  Cite a design by number only ("design 33"), never by section, and cite only open items that exist in `docs/OPEN_ITEMS.md`.
  Remove stale comments in the code you touch. **[build]**
- Update in the same change: the item in `docs/OPEN_ITEMS.md` (live status), a row in `docs/TASK_LEDGER.md` when an item is finished (history), the relevant design, and this file if a rule changes.
  A finished open item with no ledger row fails the build. **[build]**
- Status lives in `docs/OPEN_ITEMS.md` (live) and `docs/TASK_LEDGER.md` (history) and nowhere else.
  Live documents do not state test counts, commit hashes or `File.kt 123` line numbers, because they go stale.
  They name classes and functions, and a backticked class name must exist in the code.
  Every design and guide is listed in the overview's document map. **[build]**
- Write for a reader with no context: say what, not who or which session did it.
  No chat residue, no first-person narration, no claims about checks that were not run.
  `docs/WRITING_STANDARDS.md` has the full writing rules.
- Markdown follows the format rules in `docs/WRITING_STANDARDS.md`: a title, a summary paragraph, one sentence per line, blank lines around blocks, fenced code with a language. **[build]**
- Data-flow and wire diagrams are traced against the code and name real classes. **[review]**

## 6. Testing

- Every ViewModel has a test class named after it. **[build]**
- New logic ships with unit tests (JUnit 4, MockWebServer, coroutines-test); put decisions in small pure functions that can be tested without a device.
- A bug fix ships with a test that fails without the fix.
- `./gradlew :app:ktlintSourcesFormat :app:check` must pass before every commit; `./gradlew :app:assembleDebug` before handing over an APK. **[build, ci]**
- Tests never call the live servers.
- Instrumented UI tests exist (`app/src/androidTest`) but need a device; they are not part of `check` and are run by the owner (OI-17).
- What the build cannot prove is said out loud: how a screen looks, how it is spoken by TalkBack, behavior on a real printer.

## 7. Version control, CI and publishing

- One logical change per commit, and only green builds.
- Write the commit message the way a person would.
  The subject is in the imperative mood, capitalized, at most 50 characters, with no trailing period ("Add spool locations to the Spools list").
  A blank line follows, then a body wrapped at 72 characters that says what changed and why, not how.
  Leave out process narration, tool or session names, ticket-style prefixes and apologies.
  Add the co-author trailer when an AI assistant wrote the change.
- Commit identity is the GitHub login and its no-reply address, never a machine name.
- Never commit a secret, address or personal name.
  The owner runs a global commit guard (`pii-guard`) that blocks these; never bypass it (`--no-verify`) and never loosen its settings.
- The public repository is `Michael3DPrint/mybambuddy`.
  Never rewrite history that is shared and never force-push `main`.
- CI (`.github/workflows/check.yml`) runs `./gradlew :app:check` on every push to `main` and every pull request, with read-only permissions and actions pinned to full commit hashes.
  A red `main` is fixed before anything else.
- Creating, publishing or deleting a repository, changing repository settings and pushing all need the owner's say-so each time.

## 8. Build pipeline

```text
 edit code
    |
    v
 ./gradlew :app:ktlintSourcesFormat :app:check
        |-- compileKotlin (warnings are errors) ------------- fail: fix
        |-- ktlint (style, 140 columns) --------------------- fail: re-run ktlintSourcesFormat, then fix what remains
        |-- unit tests -------------------------------------- fail: fix the code, not the test
        |-- Android Lint ------------------------------------ fail: fix or document
        |-- StandardsEnforcementTest, DocumentationIdentifiersTest, ApiMapTest
        |        -------------------------------------------- fail: the message names the file
        |-- dependency verification (hashes, wrapper checksum) fail: review, then regenerate
        v
 assembleDebug --> adb install -r --> owner tries it on the device --> ledger updated --> commit --> push --> CI runs check
```

## 9. Definition of done, and how these rules stay enforced

A task is finished only when every line below is true.
Do not say "done" for more than you checked.

1. The design exists first (section 0) and matches the code.
2. `./gradlew :app:ktlintSourcesFormat :app:check` is green.
   `check` runs the unit tests and the standards checks, which fail the build on:
   - `!!` in app code; a file over 400 lines; a ViewModel over 8 public functions or without a test.
   - Public declarations without KDoc; dead production code in `domain/` or `data/`.
   - A real server address in a tracked file; `File.kt 123` line citations and test counts in live documents.
   - A finished open item with no ledger row; a design or guide missing from the document map.
   - An endpoint missing from the API map; a tab row built outside the shared one; an error shown without the Help link; Markdown that breaks the format rules; a comment that cites a missing design or a closed item.
3. New logic has unit tests; a bug fix has a test that fails without the fix; a new ViewModel has its test class in the same change.
4. `docs/OPEN_ITEMS.md` and `docs/TASK_LEDGER.md` are updated in the same change (section 5).
5. A claim in a document or a message is only as wide as what was checked.
   If a scan covered top-level declarations, say "top-level", not "every".
   Prefer making the claim a build check over writing it in prose.
6. No unrequested action on the owner's printer, phone, servers or accounts (section 4).
   Installing a build and reading `adb logcat` are allowed; driving the phone is not.
7. Nothing is committed red.
   One logical change per commit.

When a rule keeps being broken, do not just restate it: add a check to `StandardsEnforcementTest` (or `DocumentationIdentifiersTest`) so the build refuses the next violation.
An exception needs a line with a reason in `docs/standards-allowlist.txt` or `docs/doc-identifier-allowlist.txt`.
An exception that is no longer needed fails the build, so the lists cannot go stale.

## 10. UI patterns and accessibility

The app's UI patterns are written down once in `docs/design/48-ui-patterns-and-setup-help-layout.md`: screen, tabs, expandable section, navigation row, tappable summary card, callout, Help link, confirm then act, accessibility baseline.
New screens use them, or say in their design why they depart.

- Tab rows are built only by the shared `AppTabRow`. **[build]**
- A failed load is shown through the shared `ErrorNotice`, and any screen that reads an error's title or message shows a Help link (`HelpButton`) that opens the troubleshooting page for that kind of error. **[build]**
- Anything that changes the printer or a server asks for confirmation first, with the numbers shown, and is sent once.
  The one known exception is the chamber light switch, which acts at once.
- Accessibility baseline: headings marked; tap targets at least 48 dp; role and state spoken; no meaning carried by color alone.
  Text scales and the layout adapts at 1.3 times font size or more; decorative icons are hidden and informative ones described.
  Contrast of the theme, status and widget colors is a unit test. **[build]**
  Speech, large-font layout and contrast of newer screens are not proven by the build; they are the owner's TalkBack, Accessibility Scanner and large-font passes.

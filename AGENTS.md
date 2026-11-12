# MyBambuddy agent instructions

MyBambuddy is an Android app (Kotlin, Jetpack Compose) that monitors and controls the owner's Bambu Lab printer through their self-hosted Bambuddy server.
Follow the hard rules below, run the check command before you call any task done, and write documents to `docs/WRITING_STANDARDS.md`.
"PrintDeck" is only the name of the Printers tab, not the product.

## Read first

- `README.md`: what the app is.
- `docs/PROJECT_OVERVIEW.md`: architecture, safety model and the document map.
- `docs/ENGINEERING_STANDARDS.md`: the code rules, the definition of done and which rules the build enforces.
- `docs/WRITING_STANDARDS.md`: how to write documents.
- `docs/OPEN_ITEMS.md`: what is still open.
- `docs/TASK_LEDGER.md`: what is finished.
- `docs/design/`: one design per feature.
  Write the design before the code.

## Hard rules

1. Never act on the owner's printer or phone unless asked in the current task.
   Never send control commands (pause, resume, cancel, light, speed, drying, slot assignment, queue start) and never use `adb input`.
   Installing an APK and reading `adb logcat` are fine.
   Reading (GET) the owner's servers is fine; never send a write as a test.
2. Write the design first for any new feature or behavior change: wireframe, flow, data flow and write-up in `docs/design/NN-name.md` (NN is the highest number plus one).
3. Keep the API key only in encrypted storage.
   Never log it, commit it or put it in a URL.
   Never commit a real server address; it lives outside the repository in `~/.config/mybambuddy/`.
   Documents use `<server>`.
4. Claim only what you checked.
   "Every X" needs a scan of every X.
   Prefer a build check to a sentence of prose.
5. Keep status in `docs/OPEN_ITEMS.md` and `docs/TASK_LEDGER.md` only.
   Put no test counts, commit hashes or `File.kt 123` line numbers in documents.
6. Get the owner's approval before you create, publish, push to or delete a repository or change its settings.
   Commit with the GitHub no-reply identity.
   Never bypass the owner's commit guard (`pii-guard`) with `--no-verify`.

## Before you call a task done

Run `./gradlew :app:ktlintSourcesFormat :app:check` from the repository root and make it pass.
`check` runs the unit tests and the standards checks.
It fails on:

- `!!`, a file over 400 lines, a ViewModel over 8 public functions or without a test, public declarations without KDoc, and dead production code.
- A real server address in a tracked file, and line citations or test counts in documents.
- A finished open item with no ledger entry, a design or guide missing from the document map, and an endpoint missing from `docs/API_MAP.md`.
- A tab row built outside the shared one, an error shown without the Help link, Markdown that breaks the format rules, and a comment that cites a missing design or a closed item.

The build also checks dependency hashes in `gradle/verification-metadata.xml`, and CI runs the same check on GitHub.
Put an exception in `docs/standards-allowlist.txt` or `docs/doc-identifier-allowlist.txt` with a reason; an unused exception fails the build.
Update the open item, add the ledger entry and update the design in the same change.
Commit with the co-author line.

## Commit messages

- Write the subject in the imperative mood, capitalized, at most 50 characters, with no period.
- Add a blank line, then a body wrapped at 72 characters that says what changed and why.
- Make one logical change per commit, and commit only after `check` passes.
- Leave out process narration, tool or session names and apologies.
- Add the co-author trailer when you wrote the change.

## Code comments

- State facts that can be checked, and name the source when the fact comes from outside the code (a Bambuddy route, a wiki page, a standard).
- Write no history, dates, people, to-do markers or finding IDs.
- Cite a design by number only, and cite only open items listed in `docs/OPEN_ITEMS.md`.

## Writing documents

- Follow `docs/WRITING_STANDARDS.md`: summary paragraph first, present tense, no history, one sentence per line.
- Verify every name and behavior against the code before you write it.
- Trace each data-flow diagram through the real classes, from screen to API and back, before you keep or change it.
- Delete a document that only records the past.

## Pitfalls

- A `/*` inside KDoc (for example `inventory/*`) opens a nested comment and breaks ktlint.
  Reword it.
- Re-read a file after ktlint reformats it, before a scripted edit.
- Never delete inputs in a split script; run `git status` after each step.
- Validate a fetched value (a checksum, a version) before you write it into a file.
- Check that a change builds from a fresh clone before you call it portable.

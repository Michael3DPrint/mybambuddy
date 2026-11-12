# Writing standards

This is the single source of truth for how documents in this repository are written.
Read it before you create or edit any `.md` file.
Edit this file to change a rule; do not restate rules elsewhere.
The rules are written for AI agents and humans alike: each one is specific, and each one has a check you can run by reading the document.

## Rules for every document

1. **Put the summary first.**
   Start with one paragraph of two to four sentences directly under the title.
   It states what the document covers, the key decision or fact a reader needs, and the current state (built, not built, or superseded).
   A reader who stops after that paragraph must still know what is true.
2. **Describe the system as it is today.**
   Write in the present tense about the code that exists.
   Do not narrate how the work happened.
3. **Remove history.**
   Delete anything that records the past instead of describing the present.
   This covers who asked for a change, what was tried first, mistakes and their fixes, dates of edits, session or agent names, and the names of removed files, classes or documents.
   It also covers words that mark a change: "was", "now", "no longer", "since fixed", "previously", "earlier draft", "as built versus plan".
   Keep a rejected alternative only when a reader might propose it again; state it in one sentence with the reason.
4. **State only what you checked.**
   Verify every class, function, file and behavior you name against the code before you write it.
   Mark what the build and tests cannot show (for example how a screen looks or sounds on a device) in one sentence.
   Never write counts, commit hashes, or `File.kt 123` line numbers; they go stale.
5. **Cut anything a maintainer would not write by hand.**
   Remove tables that restate the code, per-step process logs, exhaustive checklists of the obvious, repeated context, and rules already stated in another document.
   Link to the source of truth instead of copying it.
   If a sentence would not change what a reader does, delete it.
6. **Write plainly.**
   Use American English, active voice and sentence case for headings.
   Use short sentences.
   Avoid marketing words such as "robust", "seamless", "powerful" and filler such as "it is worth noting".
   Define a term once, then use it the same way everywhere.
7. **Give each fact one home.**
   Live status lives only in `OPEN_ITEMS.md`; finished-work history lives only in `TASK_LEDGER.md`; rules live only in `ENGINEERING_STANDARDS.md` and this file.
   Other documents do not carry status.
8. **Trace every diagram against the code.**
   A data-flow or wire diagram shows how data moves from area to area, so each arrow must exist in the code.
   Follow the real chain by reading it: screen, view model, repository interface in `domain/repo`, implementation in `data/repo`, Retrofit interface and endpoint, DTO, mapper, domain model, state, and any store or cache.
   Name the real classes, draw the real direction, and include events that flow back (for example a `changed` event that triggers a refresh).
   Re-trace the diagram whenever the code it shows changes.
   Delete a diagram that only repeats the prose.

## Good and bad examples

Summary paragraph, bad (history and process first):

> This note was written in November after the owner asked for a way to see which spools are loaded.
> We first tried a dropdown and then changed to a list.
> It was designed before the code and later amended.

Summary paragraph, good (what, decision, state):

> The Spools list and the AMS slot sheet show which printer slot each spool is loaded in, for example "AMS 1, slot 3".
> One pure rule, `SpoolLocations`, builds the text for both screens from the AMS units.
> Built; it reads only and sends nothing to the printer.

Sentence, bad: "The scan was previously changed so that it no longer sends the API key."

Sentence, good: "The network scan uses its own client, which never carries the API key."

## Formatting (git-friendly Markdown)

- Use one `#` title per file, then `##` and `###`.
  Use sentence case.
  Do not skip heading levels.
- Put a blank line before and after every heading, list, table and code fence.
- Write one sentence per line in prose, so a diff shows only the sentences that changed.
  Do not hard-wrap inside a sentence.
- Use `-` for bullets and `1.` for numbered lists.
  Indent nested lists by two spaces.
- Fence code, commands and diagrams, and give the fence a language (`kotlin`, `bash`, `text`).
  Use `text` for ASCII wireframes and flows.
- Use backticks for code, file paths, class names and commands.
  Use relative links between documents.
- End each file with one newline.
  Leave no trailing whitespace.
  Use LF line endings.
- Keep tables for reference data only (a short list of options, fields or endpoints), not for prose.

## Document types

Each file is one type.
State the type by structure, not by a label.

| Type | Files | Shape |
|---|---|---|
| Overview and rules | `README.md`, `AGENTS.md`, `docs/PROJECT_OVERVIEW.md`, `docs/ENGINEERING_STANDARDS.md`, this file | Summary, then short sections. Imperative where it gives instructions. |
| Design | `docs/design/NN-name.md` | The template below. |
| Reference | `docs/API_MAP.md`, `THIRD_PARTY_NOTICES.md` | A table or list that a reader looks things up in. |
| Guide | `docs/guides/*.md` | Numbered steps a person follows. |
| Live status | `docs/OPEN_ITEMS.md` | Only open work, each with what is left and when it is done. |
| History | `docs/TASK_LEDGER.md` | One line per finished item: what the app gained. No process, no mistakes. |

## Design document template

A design describes one feature or decision as it exists.
Write it before the code; update it when behavior changes, so it always matches the code.
Use these sections, in this order, and omit any that do not apply:

1. `# NN: Title`, then the summary paragraph (rule 1).
2. `## Decisions`: each decision and its reason, one bullet each.
3. `## Design`: the wireframe (`text` fence), the flow including error and empty paths, and the data flow from screen to storage or API, traced against the code (rule 8).
4. `## Behavior`: rules, limits and edge cases that a maintainer must know.
5. `## Tests`: what unit tests cover and what only a device can show.
6. `## Open work`: a link to the open item in `OPEN_ITEMS.md`, only if the feature is not finished.

Keep a design short enough to read in a few minutes.
Split a design when it covers two features.

## When to delete a document

Delete a document that only records the past (an audit, a review, a plan that was carried out, a superseded design).
First move any fact a maintainer still needs into the design, standard or overview that owns it.
Then remove the file, its row in the document map in `docs/PROJECT_OVERVIEW.md`, and every link to it.

## Keep these rules current

- Change a document in the same change as the code it describes.
- When a review finds a recurring problem in documents, add a specific rule here with an example.
  Do not add vague advice such as "write concisely".
- After you edit this file, start a new conversation so agents pick up the change.
- The build checks part of this: the Markdown format rules in the Formatting section, backticked class names that must exist in the code, no line citations or test counts, and every design and guide in the document map.
  Run `./gradlew :app:check`.
  Diagram accuracy and the summary paragraph are checked in review.

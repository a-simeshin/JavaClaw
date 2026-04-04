---

name: openspec-apply-change
description: Merge change.md into the main specification. Merges change.md sections (business logic, models, integrations, errors, etc.) into corresponding sections of the target specification.
license: MIT
metadata:
author: openspec-distillate
version: "3.0"
--------------

Merge changes from change.md into the main specification (Analyst Merge).

**Input**: Optionally — change name. If not specified, auto-detect or offer a choice.

**Steps**

1. **Select change**

   If name is specified — use it. Otherwise:

   ```bash
   ls openspec/changes/ 2>/dev/null
   ```

   - Auto-select if only one active change (excluding `archive/`)
   - If multiple — use **AskUserQuestion tool** to choose

   Always announce: "Applying change: <name>"

2. **Check for change.md**

   Read `openspec/changes/<name>/change.md`.

   **If file does not exist:** suggest running `/opsx:propose` first
   **If header status is "Implemented":** congratulate, suggest `/opsx:archive`

3. **Read context**

   Read change.md and all related files:
   - `openspec/changes/<name>/change.md`
   - Target specification (specified in change.md header)

4. **Analyst Merge Workflow**

   a. **Read the target specification**

   Path is in the change.md header, "Target specification" field.
   If not specified — ask the user.

   b. **Create backup**

   ```bash
   cp <spec-path> <spec-path>.bak
   ```

   c. **Merge change.md sections → specification**

   **IMPORTANT:** Don't bind to section numbers — use the CURRENT numbering and structure of the target specification. Find the target section BY NAME, not by number.

   For each change.md section that is NOT "No changes", find the corresponding section in the specification:

   |     change.md section     |               Target specification section (by name)                |
   |---------------------------|---------------------------------------------------------------------|
   | 2. Business Logic         | Business Logic                                                      |
   | 3. Data Models (ADDED)    | Data Models — add new ones                                          |
   | 3. Data Models (MODIFIED) | Data Models — update existing                                       |
   | 3. Data Models (REMOVED)  | Data Models — remove                                                |
   | 4. Integrations           | Integrations                                                        |
   | 5. Error Handling         | Error Handling                                                      |
   | 6. Headers                | Headers                                                             |
   | 7. Validation             | Validation                                                          |
   | 8. Security               | Security                                                            |
   | 9. Migration              | If one-time — leave in change.md. If schema change — in Data Models |
   | 10. Logging               | Logging                                                             |
   | 11. Monitoring            | Monitoring                                                          |
   | 12. Configuration         | Configuration                                                       |
   | 13. Acceptance Criteria   | Acceptance Criteria Recommendations                                 |

   **If the target section is not found in the specification** — create it in a logically appropriate place.

   d. **Renumber subsections**

   After inserting new subsections, you MUST renumber ALL subsections
   in affected sections so numbering is sequential.

   Example: if section 2.3 had 2.3.1–2.3.7 and 3 new subsections were added after 2.3.2,
   the final numbering must be 2.3.1–2.3.10, NOT 2.3.1, 2.3.2, 2.3.8, 2.3.9, 2.3.10, 2.3.3, ...

   Rules:
   - New subsections are inserted in the logically correct place (near thematically related ones)
   - All subsequent subsections are renumbered
   - All internal references (e.g., "see section 2.3.2") are updated to new numbers

   e. **Rebuild TOC (table of contents)**

   After merge, you MUST update the `<!-- TOC --> ... <!-- TOC -->` block at the start of the file.

   Rebuild rules:
   - Scan ALL headings `##`, `###`, `####` in the file
   - For each heading generate a markdown link with anchor
   - Anchor format: lowercase, spaces → `-`, special characters removed
   - Nesting levels: `##` no indent, `###` with 2 spaces, `####` with 4 spaces
   - Preserve HTML tags (e.g., `<font>`) in link text, but NOT in anchor

   TOC line format:

   ```
   * [2.3.8. Email bounce classification rules](#238-email-bounce-classification-rules)
   ```

   f. **Increment version** of the specification (e.g., 1.0 → 1.1)

   g. **DO NOT DELETE** existing content not affected by the change

   g2. **Preserve formatting** — during merge DO NOT lose HTML markup: color markers (`<font color="red">**Alt**</font>`, `<font color="blue">**Opt**</font>`, `<font color="green">**Note:**</font>`), and other HTML tags in the specification text

   h. **Update status** of change.md to "Implemented"

   i. **Write** the updated specification

   j. **Verify merge**

   ```bash
   wc -l <spec-path> <spec-path>.bak
   grep "^## \|^### \|^#### " <spec-path>
   ```

   Ensure:
   - If change contains no REMOVED sections — line count >= original. If it does — decrease is acceptable.
   - Subsection numbering is sequential (no gaps or duplicates)
   - TOC matches actual headings

**Output**

```
## Apply Complete (Analyst Merge)

**Change:** <change-name>
**Target:** <spec-path>
**Version:** X.Y → X.Y+1

### Merged Sections
- ✓ Business Logic → 2.3
- ✓ Data Models (3 ADDED) → 2.6
- ✓ Integrations (2 ADDED) → 2.5
- ○ Security — No changes
...

Backup: <spec-path>.bak
Ready for archival: /opsx:archive
```

**Guardrails**
- ALWAYS read contextFiles before starting
- NEVER delete content not mentioned in the change
- ALWAYS create backup before writing
- Verify line count after merge (must be >= original)
- Show what was done in the summary

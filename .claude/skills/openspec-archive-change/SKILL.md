---

name: openspec-archive-change
description: Archive a completed change after merging into the main specification. Moves the change directory to archive/ with a date prefix.
license: MIT
metadata:
author: openspec-distillate
version: "3.0"
--------------

Archive a completed change.

**Input**: Optionally — change name. If not specified, offer a choice from active changes.

**Steps**

1. **If name not specified — offer a choice**

   Find active changes:

   ```bash
   ls openspec/changes/ 2>/dev/null
   ```

   Exclude `archive/` from the list. Use **AskUserQuestion tool** to choose.

   Show only active (non-archived) changes.

   **IMPORTANT**: DO NOT guess or auto-select. Let the user choose.

2. **Check for change.md**

   Verify that `openspec/changes/<name>/change.md` exists.

   **If change.md does not exist:**
   - Show warning: change.md not created
   - Ask for confirmation via **AskUserQuestion tool**
   - Proceed on confirmation

3. **Check change.md status**

   Read `openspec/changes/<name>/change.md` and check the "Status" field in the header:
   - If "Implemented" — all ok, ready for archival
   - If other status — warn and ask for confirmation

4. **Perform archival**

   ```bash
   mkdir -p openspec/changes/archive
   ```

   Archive name: `YYYY-MM-DD-<change-name>`

   **Check that the target directory does not exist:**
   - If it exists — error, suggest renaming
   - If not — move:

   ```bash
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```
5. **Show result**

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** analyst-driven
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/

All artifacts finalized. Change archived.
```

**Output With Warnings**

```
## Archive Complete (with warnings)

**Change:** <change-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/

**Warnings:**
- change.md not in "Implemented" status
- N incomplete tasks

Review the archive if this was not intentional.
```

**Guardrails**
- Always offer change selection if name not specified
- Check status by reading change.md content, not via CLI
- DO NOT block archival on warnings — inform and confirm
- The entire change directory is moved as a whole
- Show a clear summary

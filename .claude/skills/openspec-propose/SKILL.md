---

name: openspec-propose
description: Create a specification change proposal (change.md). A unified document combining motivation (WHY) and system requirements (WHAT CHANGES) — 13 sections from business logic to acceptance criteria.
license: MIT
metadata:
author: openspec-distillate
version: "3.0"
--------------

Create a specification change proposal.

Will create a unified `change.md` document — proposal + system requirements in one file.
After approval, run `/opsx:apply` to merge into the main specification.

---

**Input**: Change name (kebab-case) OR a description of what needs to change.

**Steps**

1. **Phase 1: Task Interview — understand WHAT and WHY**

   **DO NOT create change.md after a single question.** First understand the task itself, without tying it to code and specifications.

   Start with an open question:

   > "What change needs to be made? Describe what you want to change and why."

   Then ask clarifying questions about the task:
   - **Motivation**: "What problem does this solve? Who encountered it?"
   - **Scope**: "Does this affect one service or multiple? Which ones?"
   - **Business logic**: "What rules should apply? Are there exceptions?"
   - **Expected outcome**: "How should it work after the change? How will the user see it?"
   - **Constraints**: "Are there deadlines, dependencies, technical constraints?"

   **Phase 1 rules:**
   - Ask questions in batches of 2–3, don't overwhelm with a wall of text
   - Adapt to answers — if the user described in detail, don't re-ask the obvious
   - If an answer is incomplete or contradictory — dig deeper
   - **DO NOT proceed to Phase 2 until the task is fully clear**
   - When the task is understood — summarize and get confirmation

   Derive a kebab-case name from the description — words separated by hyphens, lowercase Latin letters (e.g., "add batch webhook sending" → `add-webhook-batch`, "fix email validation" → `fix-email-validation`).

2. **Study context**

   Read:
   - Target specification (the one we'll be changing)
   - Related specifications (if the change affects multiple services)
   - Service code — to understand the current implementation

   **Reference materials** (shipped with the plugin in `references/`, next to `skills/`):
   - `references/common-requirements.md` — logging standards, error codes, RFC 7807 format, HTTP status mapping
   - `references/analytics-rules.md` — documentation formatting rules
   - `references/terms-and-abbreviations.md` — terminology
   - `references/example-change.md` — full real example of a completed change.md (636 lines, 18 acceptance criteria)

   **Code analysis — use the best available tools:**

   Check which code analysis tools are available and use the most effective one (in priority order):
   1. **Serena** (MCP) — `find_symbol`, `get_symbols_overview`, `find_referencing_symbols` — semantic analysis, symbol navigation, dependency search
   2. **LSP tools** — go-to-definition, find-references, workspace symbols — if a language server is available
   3. **Code index / embeddings** — semantic codebase search
   4. **Any other MCP servers or tools** for code analysis, navigation, indexing — if available, use them
   5. **Grep/Glob** — text search as a last resort

   Don't limit yourself to reading individual files — build a complete picture: dependencies, calling code, related entities.

3. **Phase 2: Implementation Detail Interview — HOW exactly we're changing**

   Now, knowing the task AND having studied the current code and specifications, ask questions about implementation details:
   - **Data models**: "Currently entity X has fields A, B, C. Are new ones needed? Anything changing?"
   - **Integrations**: "The service currently calls Y and Z. New calls appearing? Existing ones changing?"
   - **Errors**: "What new errors are possible? How to handle them?"
   - **Migration**: "Table X currently has N records. Need data migration? Backward compatibility?"
   - **Security**: "Are access, roles, authorization changing?"
   - **Configuration**: "Need new configuration parameters? Feature flags?"
   - **Monitoring**: "Need new metrics or alerts?"

   **Phase 2 rules:**
   - Tie questions to specifics from code and specifications — "Currently it works like this: ... How should it change?"
   - If you found contradictions between the task and current implementation — draw attention
   - **DO NOT proceed to file creation while inaccuracies, ambiguities, or gaps remain**
   - When all details are covered — summarize ALL collected requirements and explicitly ask: "Is everything accounted for? Is there anything I missed?"
   - Only after user confirmation — proceed to creating change.md
   - If the user adds new details during confirmation — process them and summarize again

4. **Create change directory**

   ```bash
   mkdir -p openspec/changes/<name>
   ```

   If the directory already exists and contains `change.md` — ask the user: continue working on the existing change or create a new one with a different name.

5. **Read the change.md template**

   The template ships with the plugin: `templates/change.md` (in the plugin's root directory, next to `skills/`).
   Determine the template path from this SKILL.md's path — go up two levels and find `templates/change.md`.

   If the template also exists in the project (`openspec/schemas/analyst-driven/templates/change.md`) — use the project one, it takes priority.

6. **Create `change.md`**

   Fill the structure with real data based on studied context:

   - If using the template — **remove ALL HTML comments** (`<!-- -->`) and `<!-- EXAMPLE: ... -->` blocks
   - Fill metadata in the header (status: Draft, date, target specification)
   - For each of the 13 sections:
     - If the change affects this section — fill with real data
     - If it doesn't — write exactly one line: `No changes.`
   - Business rules — in IF/THEN/ELSE format (examples are in the template)
   - Acceptance criteria recommendations — in WHEN/THEN format (examples are in the template)
   - For alternative and error scenarios use color markers:
     - `<font color="red">**Alt**</font>` — alternative/error flow
     - `<font color="blue">**Opt**</font>` — optional element
     - `<font color="blue">**Loop**</font>` — loop element
     - `<font color="green">**Note:**</font>` — important notes

   **Requirement obligation levels:**
   - MUST / SHALL — mandatory, system does not work without it
   - SHOULD — recommended, but deviation is allowed with justification
   - MAY — optional

   Write to: `openspec/changes/<name>/change.md`

   **WARNING: large files.** Change.md may be large (200+ lines). If the file is large — write it in parts (section by section) to avoid losing content due to context window limitations. Warn the user: "Change is large, writing in parts."

7. **Review in subagent**

   After creating the file, run a review. If the Agent tool is available — launch a **separate subagent**. If not — perform the review yourself using the checklist below.

   Subagent prompt:

   > Review file `openspec/changes/<name>/change.md`.
   > Read change.md, target specification, and service code.
   > Check:
   > 1. **Completeness** — are all aspects of the task covered? Are there missing sections that should be filled?
   > 2. **Accuracy** — do the described changes match the actual code? Are there contradictions with current implementation?
   > 3. **Consistency** — are there conflicts between sections within change.md?
   > 4. **Specificity** — are there vague formulations, placeholders, TODOs?
   > 5. **Feasibility** — is it possible to implement what's described? Are there missing dependencies?
   > Return a list of found issues or "Review passed, no issues found".

   **If the subagent found issues** — fix change.md and inform the user what was corrected.

**Output**

After creation and review:
- Change name and file path
- Brief description: which sections are filled, which are "No changes"
- Target specification
- Review results (issues and corrections, if any)
- Hint: "Ready for approval. To merge into specification — `/opsx:apply`"

**Guardrails**

**CRITICAL — document role:**
- change.md is a SYSTEMS ANALYST document, not a developer document
- Describe WHAT should change and WHY, but NEVER write HOW to implement IN CODE
- **PROHIBITED in change.md:**
- Code in any language (Java, Python, etc.)
- Method, class, or function implementations
- Unit tests, integration tests, test code
- Specific algorithmic solutions (that's the developer's job)
- Implementation plan with technical implementation steps
- Class diagrams or code reviews
- **CORRECT:**
- Business rules in IF/THEN/ELSE format (without code)
- Data models as field tables (name, type, required, example value, description)
- API as endpoint descriptions (method, path, request/response schema), not as implementation
- Acceptance criteria recommendations in WHEN/THEN format (without test code)
- Configuration as parameter tables, not as yaml/properties files
- SQL is allowed ONLY as DDL/DML recommendations (CREATE TABLE, ALTER TABLE) for describing data structure
- JSON-schema, Protobuf definitions, Avro schemas are allowed for describing data models
- Application code (Java, Python, Go, etc.) is COMPLETELY PROHIBITED
- If the user asks to add code — explain that change.md describes requirements, and implementation is the developer's job based on these requirements

**Process:**
- NEVER create change.md while ambiguities remain — conduct a full interview until complete clarity
- Before creating the file, summarize ALL requirements and get explicit user confirmation
- ALWAYS study the target specification BEFORE creating change.md
- DO NOT copy examples from the template into the final file
- Every filled section must contain real data, not placeholders
- Use MUST/SHALL for mandatory requirements
- If a change with this name already exists — ask whether to continue it or create a new one
- Verify the file is created and contains all 13 sections

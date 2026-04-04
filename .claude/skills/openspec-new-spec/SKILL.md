---

name: openspec-new-spec
description: Create a full service specification from template. Use when you need to document a new or existing service using the service-spec.md template.
license: MIT
metadata:
author: openspec-distillate
version: "3.0"
--------------

Create a full service specification — for a new or existing service.

Read the specification template and common requirements, then generate a complete specification.

---

**Input**: Service name and description, or just a name if the user wants to describe it interactively.

**Steps**

1. **Phase 1: Task Interview — understand WHAT the service is**

   **DO NOT create a specification after a single question.** First understand the service itself, without tying it to code.

   Start with an open question:

   > "What service needs to be documented? Describe its purpose and key functions."

   Then ask clarifying questions about the service's essence:
   - **Purpose**: "Who are the consumers of this service? What business problem does it solve?"
   - **Functions**: "What key operations does it perform? What's most important?"
   - **Context**: "Where does this service fit in the overall architecture? Part of a platform, standalone?"
   - **Reliability**: "What SLAs apply? What happens when dependencies are unavailable?"

   **Phase 1 rules:**
   - Ask questions in batches of 2–3, don't overwhelm with a wall of text
   - Adapt to answers — if the user described in detail, don't re-ask the obvious
   - If an answer is incomplete — dig deeper
   - **DO NOT proceed to Phase 2 until the service's purpose and context are clear**
   - When the essence is understood — summarize and get confirmation

   Derive a kebab-case name from the description.

2. **Study code and existing specifications**

   Read the specification template — shipped with the plugin: `templates/service-spec.md` (in the plugin's root directory, next to `skills/`).
   Determine the template path from this SKILL.md's path — go up two levels and find `templates/service-spec.md`.
   If the template also exists in the project (`openspec/schemas/analyst-driven/templates/service-spec.md`) — use the project one, it takes priority.

   Also study:
   - Service code — controllers, services, entities, configs
   - 1–2 existing specifications from `openspec/specs/` as a style reference (if any)

   **Reference materials** (shipped with the plugin in `references/`, next to `skills/`):
   - `references/common-requirements.md` — logging standards (ERROR/WARN/INFO/DEBUG/TRACE levels), error codes with reserved ranges, RFC 7807 format, HTTP status mapping
   - `references/analytics-rules.md` — documentation formatting rules
   - `references/terms-and-abbreviations.md` — terminology

   **Code analysis — use the best available tools:**

   Check which code analysis tools are available and use the most effective one (in priority order):
   1. **Serena** (MCP) — `find_symbol`, `get_symbols_overview`, `find_referencing_symbols` — semantic analysis, symbol navigation, dependency search
   2. **LSP tools** — go-to-definition, find-references, workspace symbols — if a language server is available
   3. **Code index / embeddings** — semantic codebase search
   4. **Any other MCP servers or tools** for code analysis, navigation, indexing — if available, use them
   5. **Grep/Glob** — text search as a last resort

   Don't limit yourself to reading individual files — build a complete picture: all endpoints, all entities, all integrations, all config parameters.

3. **Phase 2: Detail Interview — HOW the service works**

   Now, knowing the service's purpose AND having studied its code, ask questions about specific details:
   - **API**: "I see endpoints X, Y, Z in the code. Are all current? Any undocumented ones?"
   - **Business logic**: "In the code I found rule X. Is this correct? Are there edge cases, exceptions?"
   - **Integrations**: "The service calls A and B. Is that the full list? How are call errors handled?"
   - **Data**: "I see entities X, Y. What are the relationships? Is there caching?"
   - **Authorization**: "Which roles have access? Are there data-level restrictions?"
   - **Configuration**: "I see parameters X, Y in config. Document all? Are there feature flags?"

   **Phase 2 rules:**
   - Tie questions to specifics from the code — "I see X in the code. Is this current?"
   - If you found inconsistencies or non-obvious logic — clarify
   - **DO NOT proceed to file creation while inaccuracies, ambiguities, or gaps remain**
   - When all details are covered — summarize ALL collected data and explicitly ask: "Is everything accounted for? Is there anything I missed?"
   - Only after user confirmation — proceed to creating the specification
   - If the user adds new details during confirmation — process them and summarize again

4. **Create directory and specification**

   ```bash
   mkdir -p openspec/specs/<service-name>
   ```

   Fill the structure with real data based on user description:
   - If using the template — remove ALL HTML comments (`<!-- -->`)
   - Fill ALL sections with real content, no placeholders. If a section is not applicable (e.g., monitoring for a prototype) — write: "Not defined at current stage. Will be added during production preparation."
   - Business rules in IF/THEN/ELSE format (examples are in the template)
   - Acceptance criteria recommendations in WHEN/THEN format (examples are in the template)

   **Requirement obligation levels:**
   - MUST / SHALL — mandatory, system does not work without it
   - SHOULD — recommended, but deviation is allowed with justification
   - MAY — optional

   Write to: `openspec/specs/<service-name>/<service-name>.md`

   **WARNING: large files.** The specification may be very large (300+ lines). If the file is large — write it in parts (section by section) to avoid losing content due to context window limitations. Warn the user: "Specification is large, writing in parts."

5. **Review in subagent**

   After creating the file, run a review. If the Agent tool is available — launch a **separate subagent**. If not — perform the review yourself using the checklist below.

   Subagent prompt:

   > Review file `openspec/specs/<service-name>/<service-name>.md`.
   > Read the specification and service code (controllers, services, entities, configs).
   > Check:
   > 1. **Completeness** — are all service functions from the code reflected in the specification? Are there missing endpoints, entities, integrations?
   > 2. **Accuracy** — do descriptions match the actual code? Are there discrepancies in field names, types, business logic?
   > 3. **Consistency** — are there conflicts between sections within the specification?
   > 4. **Specificity** — are there vague formulations, placeholders, TODOs?
   > 5. **Structure** — are all required sections present and filled?
   > Return a list of found issues or "Review passed, no issues found".

   **If the subagent found issues** — fix the specification and inform the user what was corrected.

**Output**

After creation and review, show:
- Service name and file path
- List of generated sections
- Key documented integrations
- Review results (issues and corrections, if any)
- Hint: "Specification is ready. To make changes, use `/opsx:propose`"

**Guardrails**

**CRITICAL — document role:**
- The specification is a SYSTEMS ANALYST document, not a developer document
- Describe WHAT the service does, HOW it behaves, WHAT data it accepts/returns — but NEVER write HOW to implement IN CODE
- **PROHIBITED in specifications:**
- Code in any language (Java, Python, etc.)
- Method, class, or function implementations
- Unit tests, integration tests, test code
- Specific algorithmic solutions (that's the developer's job)
- **CORRECT:**
- Business rules in IF/THEN/ELSE format (without code)
- Data models as field tables (name, type, required, example value, description)
- API as endpoint descriptions (method, path, request/response JSON schema)
- Integrations as interaction descriptions (protocol, format, timeout, retry policy)
- Acceptance criteria recommendations in WHEN/THEN format (without test code)
- Configuration as parameter tables (name, type, default, description)
- SQL is allowed ONLY as DDL/DML recommendations (CREATE TABLE, ALTER TABLE) for describing data structure
- JSON-schema, Protobuf definitions, Avro schemas are allowed for describing data models
- Application code (Java, Python, Go, etc.) is COMPLETELY PROHIBITED

**Process:**
- NEVER create a specification while ambiguities remain — conduct a full interview until complete clarity
- Before creating the file, summarize ALL data about the service and get explicit user confirmation
- ALWAYS read the template from the plugin directory — it ships with the skills
- Remove ALL HTML comments from the result
- Every section must contain real content — no leftover `<!-- placeholder -->`
- Verify the file exists after writing

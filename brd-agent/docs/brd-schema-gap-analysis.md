# Gap Analysis: brd-schema.md vs BRD_FleetManagement.md

## Structural Difference

The two documents use fundamentally different organizational principles:

| Aspect | brd-schema.md (JSON) | BRD_FleetManagement.md (Existing) |
|--------|---------------------|-----------------------------------|
| **Organization** | By requirement type (FRs, entities, screens...) | By menu structure (File > Fleet, File > Fuel...) |
| **Granularity** | One BRD per module/unit | One BRD for the entire application |
| **Source tracing** | Per-requirement `source_trace` array | Inline references to VCX files, PRG programs |
| **Abstraction level** | Business-language requirements | Implementation-level trace (class names, SQL, VCX paths) |

---

## Section-by-Section Comparison

### Present in BOTH documents

| Schema Section | Existing BRD Equivalent | Match Quality |
|----------------|------------------------|---------------|
| `purpose` | Section headings "Purpose:" under each menu item | Good - both describe business purpose |
| `functional_requirements` | Business Rules + Form Actions sections | Partial - existing BRD mixes FRs with UI flow |
| `data_entities` | Section 9: Database Schema (Tables, Views, ER) | Good - existing has table+field+FK detail |
| `ui_screens` | Sections 1.1-1.5 (each form = a screen) | Good - existing has more UI detail (controls, grids) |
| `business_rules` | "Business Rules:" subsections under each form | Good - both capture rules |
| `reports` | Section 5: Reports (inventory table) | Good - existing has full report inventory |

### Present in existing BRD but MISSING from brd-schema.md

| Existing BRD Section | What It Captures | Suggested Schema Addition |
|---------------------|------------------|--------------------------|
| **Menu Structure** (File, Edit, Tools, Help) | Navigation hierarchy, hot keys, menu conditions | Add `navigation_structure` or enrich `ui_screens.navigation_to` |
| **Business Objects** (bo_vehicle, bo_fuel...) | Middle-tier components, VCX class files | Add to `source_trace` or a new `components` section |
| **Data Sources** (ds_vehicle, ds_fuel...) | Data binding layer between BO and DB | Could fold into `data_entities.source_trace` |
| **SQL Queries** | Actual SQL used per form | Add `queries` field to `data_entities` or `functional_requirements` |
| **Lookup Data Management** (Section 7) | Centralized reference data: codes, descriptions | Could be a `data_entity` with a `lookup_categories` field |
| **Utility Programs** (Section 8) | PRG programs, their callers, and purpose | Could fold into `integrations` or add a `utility_programs` section |
| **Security Model** (Section 10) | Auth, authz, access levels, gating conditions | Add `security` top-level section or fold into `business_rules` |
| **Startup Sequence** (Section 11) | Application initialization order | Add `initialization_sequence` or treat as an integration |
| **Execution Flow Pattern** (Section 12) | Menu > Form > BO > DS > DB trace pattern | Architectural documentation, not per-module |
| **VCX Library references** | Which VCX file defines each class | Already in `source_trace.file`, but format differs |
| **Form Type** (modal, two-page, data entry) | UI pattern classification | Add `form_type` to `ui_screens` |
| **Related Sub-Forms** | Popup/child forms launched from a parent | Add `child_screens` to `ui_screens` |
| **Keyboard shortcuts / Hot Keys** | Ctrl+Z, Alt+X, F1 etc. | Add `shortcut` to UI actions or a `keyboard_shortcuts` section |

### Present in brd-schema.md but MISSING from existing BRD

| Schema Section | What It Would Capture | Status in Existing BRD |
|---------------|----------------------|----------------------|
| `actors` | Named user roles with descriptions | Not explicitly listed (implicit: Fleet Manager, Admin, general user) |
| `functional_requirements.inputs` | Formal input parameters per FR | Not formalized - inputs are implied in field lists |
| `functional_requirements.outputs` | Formal output descriptions per FR | Not formalized - outputs are implied in display descriptions |
| `functional_requirements.exception_paths` | Failure scenarios per FR | Partially present in business rules (e.g., security access denied) |
| `integrations` | External system connections | Not present - VFP app is self-contained (except MSINFO32.EXE) |
| `open_questions` | Unresolved items needing SME input | Not present in existing BRD (it was human-authored, not extracted) |
| `data_entities.fields.business_meaning` | Human description per field | Not present - only technical field names listed |

---

## Recommended Schema Changes to Match Existing BRD

### Priority 1: High-value additions (capture what the existing BRD emphasizes)

1. **Add `sql_queries` to `data_entities`**
   ```json
   "sql_queries": [
     { "name": "string", "sql": "string", "purpose": "string" }
   ]
   ```
   The existing BRD lists SQL for every form. This is valuable for migration.

2. **Add `lookup_categories` to a dedicated `reference_data` section or as a special `data_entity`**
   ```json
   "reference_data": {
     "table": "Lookups",
     "categories": [
       { "code": "mke", "description": "Vehicle manufacturer", "used_by": ["Vehicle form"] }
     ]
   }
   ```
   Section 7 of the existing BRD is entirely about lookup data.

3. **Enrich `ui_screens` with implementation details**
   ```json
   "form_type": "string (two-page, data entry, modal dialog, grid)",
   "form_class": "string",
   "library_file": "string (VCX path)",
   "child_screens": ["string - sub-form screen IDs"],
   "controls": [
     { "name": "string", "type": "string", "action": "string" }
   ]
   ```
   The existing BRD has detailed control-level documentation per form.

4. **Add `security` top-level section**
   ```json
   "security": {
     "authentication": "string",
     "authorization_model": "string",
     "access_checks": [
       { "resource": "string", "check": "string", "minimum_level": "number" }
     ]
   }
   ```

### Priority 2: Nice-to-have

5. **Add `menu_structure`** - captures the File/Edit/Tools/Help hierarchy
6. **Add `startup_sequence`** - ordered initialization steps
7. **Add `keyboard_shortcuts`** - hot key mappings

### Priority 3: Can skip

8. Utility programs (DVStuff/) - developer-only, not business requirements
9. Edit menu standard operations - VFP framework boilerplate
10. Execution flow pattern - architectural doc, not per-module

---

## Recommended Markdown Template Adjustment

To make the agent's markdown output look like the existing BRD, the template should be reorganized from **"by requirement type"** to **"by functional area / screen"**, grouping all related FRs, entities, rules, and SQL under each screen.

### Current template structure (from schema):
```
1. Actors
2. Functional Requirements (all FRs together)
3. Data Entities (all entities together)
4. UI Screens (all screens together)
5. Business Rules (all rules together)
6. Reports
7. Integrations
8. Open Questions
```

### Suggested template structure (matching existing BRD):
```
1. Purpose & Actors
2. Functional Areas (one section per screen/form):
   2.1 Fleet Management
       - Form attributes (class, library, type, modal)
       - Business objects / data sources
       - Fields (entity fields relevant to this screen)
       - Business rules (for this screen)
       - SQL queries
       - Related sub-forms
       - Source trace
   2.2 Fuel Management
       - (same structure)
   ...
3. Database Schema
   - Tables & fields (consolidated from data_entities)
   - Views
   - Entity relationships
4. Reports
5. Lookup Data Management
6. Security Model
7. Open Questions
```

This restructuring would require a **post-processing step** after the LLM produces per-file partials: the BrdMerger (Phase 4) would reorganize the flat JSON into this screen-centric markdown layout.

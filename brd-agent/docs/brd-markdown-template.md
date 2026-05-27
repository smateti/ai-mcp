# {Module Name} - Business Requirements Document (BRD)

## Metadata

| Attribute | Value |
|-----------|-------|
| **Module** | {module_name} |
| **Extraction Date** | {YYYY-MM-DD} |
| **Source Files** | {source_files[], one per line} |

**Purpose:** {purpose - 1-3 sentence summary of what this module does for the business}

---

## 1. Actors

| Actor | Description |
|-------|-------------|
| {name} | {description} |

---

## 2. Menu Structure

### {Menu Name} (e.g. File, Edit, Tools, Help)

| Label | Command | Hot Key | Condition | Screen |
|-------|---------|---------|-----------|--------|
| {label} | {command} | {hot_key or --} | {condition or --} | {screen_ref or --} |

**Source Trace:**

| File | Location |
|------|----------|
| {file} | {location} |

---

## 3. Functional Requirements

### FR-001: {title}

| Attribute | Value |
|-----------|-------|
| **Trigger** | {trigger - what initiates this} |

**Description:** {description - what the system must do, business-language}

**Inputs:**

| Name | Type | Required | Validation |
|------|------|----------|------------|
| {name} | {type} | {true/false} | {validation rule or null} |

**Outputs:**

| Name | Type | Description |
|------|------|-------------|
| {name} | {type} | {description} |

**Business Rules:**
- {rule 1}
- {rule 2}

**Exception Paths:**
- {what happens when it fails 1}
- {what happens when it fails 2}

**Source Trace:**

| File | Location | Snippet |
|------|----------|---------|
| {file} | {procedure or line range} | `{code snippet}` |

---

## 4. UI Screens

### UI-001: {Screen Name}

| Attribute | Value |
|-----------|-------|
| **Purpose** | {purpose} |
| **Form Type** | {data entry / two-page / grid navigation / modal dialog / lookup picker} |
| **Form Class** | {class name or --} |
| **Library File** | {VCX/SCX path or --} |
| **Modal** | {true / false} |
| **Navigates To** | {screen IDs, comma-separated} |
| **Child Screens** | {sub-form screen IDs, comma-separated, or --} |

**Fields Displayed:**
- {field 1}
- {field 2}

**User Actions:**
- {action 1}
- {action 2}

**Controls:**

| Name | Type | Action | Program Called |
|------|------|--------|---------------|
| {name} | {button/grid/textbox/checkbox/combobox/label} | {action} | {program or --} |

**Components (Business Objects / Data Sources):**

| Name | Type | Class File | Target Table |
|------|------|------------|--------------|
| {e.g. bo_vehicle} | {business_object / data_source} | {VCX file} | {table or view} |

**Source Trace:**

| File | Location |
|------|----------|
| {file} | {location} |

---

## 5. Data Entities

### {Entity Name}

**Description:** {business meaning, not table structure}

**Fields:**

| Field | Type | Required | Validation | Business Meaning |
|-------|------|----------|------------|------------------|
| {name} | {type} | {true/false} | {validation or null} | {business_meaning} |

**Relationships:**
- {relationship to other entity 1}
- {relationship to other entity 2}

**SQL Queries:**

| SQL | Purpose |
|-----|---------|
| `{sql statement}` | {purpose} |

**Source Trace:**

| File | Location |
|------|----------|
| {file} | {location} |

---

## 6. Business Rules

| ID | Rule | Rationale |
|----|------|-----------|
| BR-001 | {declarative, business language} | {rationale or null} |

**Source Traces:**

| Rule | File | Location | Snippet |
|------|------|----------|---------|
| BR-001 | {file} | {location} | `{snippet}` |

---

## 7. Reference Data

### {Table Name} (e.g. Lookups)

**Description:** {purpose of this reference data store}

| Code | Description | Used By |
|------|-------------|---------|
| {e.g. mke} | {e.g. Vehicle manufacturer} | {forms/modules using this category} |

**Source Trace:**

| File | Location |
|------|----------|
| {file} | {location} |

---

## 8. Reports

| ID | Name | Purpose | Data Source | Parameters | Output Options |
|----|------|---------|-------------|------------|----------------|
| RPT-001 | {name} | {purpose} | {data_source} | {params, comma-separated} | {preview / printer / file} |

**Source Traces:**

| Report | File | Location |
|--------|------|----------|
| RPT-001 | {file} | {location} |

---

## 9. Security

| Attribute | Value |
|-----------|-------|
| **Authentication** | {how users log in} |
| **Authorization Model** | {role-based / level-based / etc.} |

**Access Checks:**

| Resource | Check Expression | Description |
|----------|-----------------|-------------|
| {menu item, form, or feature} | `{code that gates access}` | {business meaning} |

**Source Trace:**

| File | Location |
|------|----------|
| {file} | {location} |

---

## 10. Integrations

| Name | Type | Description |
|------|------|-------------|
| {name} | {type} | {description} |

**Source Traces:**

| Integration | File | Location |
|-------------|------|----------|
| {name} | {file} | {location} |

---

## 11. Open Questions

- {question 1 - things the extractor could not determine and needs SME input}
- {question 2}

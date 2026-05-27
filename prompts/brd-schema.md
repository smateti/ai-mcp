A BRD document is a JSON object with this exact structure. Every field
is required. Use null for unknowns, never omit a field.

{
  "metadata": {
    "source_files": ["FleetManagement/forms/CUSTEDIT.scx", ...],
    "module_name": "string",
    "extraction_date": "YYYY-MM-DD"
  },
  "purpose": "1-3 sentence summary of what this module does for the business",
  "actors": [
    { "name": "string", "description": "string" }
  ],
  "menu_structure": [
    {
      "menu": "string (top-level menu: File, Edit, Tools, Help)",
      "items": [
        {
          "label": "string (display text)",
          "command": "string (code executed on click)",
          "hot_key": "string or null",
          "condition": "string or null (visibility/enable condition)",
          "screen_ref": "string or null (UI screen ID this item opens)",
          "source_trace": { "file": "string", "location": "string" }
        }
      ]
    }
  ],
  "functional_requirements": [
    {
      "id": "FR-001",
      "title": "string",
      "description": "string - what the system must do, business-language",
      "trigger": "string - what initiates this (user action, schedule, event)",
      "inputs": [
        { "name": "string", "type": "string", "required": true, "validation": "string" }
      ],
      "outputs": [
        { "name": "string", "type": "string", "description": "string" }
      ],
      "business_rules": ["string - one rule per array entry"],
      "exception_paths": ["string - what happens when it fails"],
      "source_trace": [
        { "file": "string", "location": "procedure or line range", "snippet": "string" }
      ]
    }
  ],
  "ui_screens": [
    {
      "id": "UI-001",
      "name": "string",
      "purpose": "string",
      "form_type": "string (data entry | two-page | grid navigation | modal dialog | lookup picker)",
      "form_class": "string (class name e.g. FleetSearchForm)",
      "library_file": "string (VCX/SCX file path)",
      "modal": true,
      "fields_displayed": ["string"],
      "user_actions": ["string"],
      "controls": [
        {
          "name": "string",
          "type": "string (button | grid | textbox | checkbox | combobox | label)",
          "action": "string (what happens on click/change)",
          "program_called": "string or null"
        }
      ],
      "child_screens": ["string - sub-form/dialog screen IDs"],
      "navigation_to": ["string - other screen IDs reachable"],
      "components": [
        {
          "name": "string (e.g. bo_vehicle, ds_vehicle)",
          "type": "string (business_object | data_source)",
          "class_file": "string (VCX file)",
          "target_table": "string (table or view name)"
        }
      ],
      "source_trace": [{ "file": "string", "location": "string" }]
    }
  ],
  "data_entities": [
    {
      "name": "string",
      "description": "string - business meaning, not table structure",
      "fields": [
        { "name": "string", "type": "string", "required": true,
          "validation": "string", "business_meaning": "string" }
      ],
      "relationships": ["string - to other entities"],
      "sql_queries": [
        { "name": "string or null", "sql": "string", "purpose": "string" }
      ],
      "source_trace": [{ "file": "string", "location": "string" }]
    }
  ],
  "business_rules": [
    {
      "id": "BR-001",
      "rule": "string - declarative, business language",
      "rationale": "string or null",
      "source_trace": [{ "file": "string", "location": "string", "snippet": "string" }]
    }
  ],
  "reference_data": [
    {
      "table": "string (e.g. Lookups)",
      "description": "string - purpose of this reference data store",
      "categories": [
        {
          "code": "string (e.g. mke)",
          "description": "string (e.g. Vehicle manufacturer)",
          "used_by": ["string (forms/modules that use this category)"]
        }
      ],
      "source_trace": [{ "file": "string", "location": "string" }]
    }
  ],
  "reports": [
    {
      "id": "RPT-001",
      "name": "string",
      "purpose": "string",
      "data_source": "string",
      "parameters": ["string"],
      "output_options": ["string (preview | printer | file)"],
      "source_trace": [{ "file": "string", "location": "string" }]
    }
  ],
  "security": {
    "authentication": "string - how users log in",
    "authorization_model": "string - how access is controlled (e.g. role-based, level-based)",
    "access_checks": [
      {
        "resource": "string (menu item, form, or feature)",
        "check_expression": "string (code that gates access)",
        "description": "string (business meaning)"
      }
    ],
    "source_trace": [{ "file": "string", "location": "string" }]
  },
  "integrations": [
    { "name": "string", "type": "string", "description": "string",
      "source_trace": [{ "file": "string", "location": "string" }] }
  ],
  "open_questions": [
    "string - things the extractor could not determine and needs SME input"
  ]
}

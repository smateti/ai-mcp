package com.example.brdagent.agent;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

// PROVENANCE: PartialBrd — per-file extraction output matching brd-llama.md schema

/**
 * Java model for the partial BRD that the LLM produces for a single source file.
 * Field names match the snake_case JSON schema defined in brd-llama.md.
 */
public class PartialBrd {

    @JsonbProperty("source_file")
    private String sourceFile;

    @JsonbProperty("functional_requirements")
    private List<FunctionalRequirement> functionalRequirements;

    @JsonbProperty("data_entities")
    private List<DataEntity> dataEntities;

    @JsonbProperty("business_rules")
    private List<BusinessRule> businessRules;

    @JsonbProperty("ui_elements")
    private List<UiElement> uiElements;

    @JsonbProperty("menu_items")
    private List<MenuItem> menuItems;

    @JsonbProperty("reference_data_usage")
    private List<ReferenceDataUsage> referenceDataUsage;

    @JsonbProperty("security_checks")
    private List<SecurityCheck> securityChecks;

    @JsonbProperty("open_questions")
    private List<String> openQuestions;

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public List<FunctionalRequirement> getFunctionalRequirements() { return functionalRequirements; }
    public void setFunctionalRequirements(List<FunctionalRequirement> v) { this.functionalRequirements = v; }
    public List<DataEntity> getDataEntities() { return dataEntities; }
    public void setDataEntities(List<DataEntity> v) { this.dataEntities = v; }
    public List<BusinessRule> getBusinessRules() { return businessRules; }
    public void setBusinessRules(List<BusinessRule> v) { this.businessRules = v; }
    public List<UiElement> getUiElements() { return uiElements; }
    public void setUiElements(List<UiElement> v) { this.uiElements = v; }
    public List<MenuItem> getMenuItems() { return menuItems; }
    public void setMenuItems(List<MenuItem> v) { this.menuItems = v; }
    public List<ReferenceDataUsage> getReferenceDataUsage() { return referenceDataUsage; }
    public void setReferenceDataUsage(List<ReferenceDataUsage> v) { this.referenceDataUsage = v; }
    public List<SecurityCheck> getSecurityChecks() { return securityChecks; }
    public void setSecurityChecks(List<SecurityCheck> v) { this.securityChecks = v; }
    public List<String> getOpenQuestions() { return openQuestions; }
    public void setOpenQuestions(List<String> v) { this.openQuestions = v; }

    // --- Nested models ---

    public static class FunctionalRequirement {
        private String id;
        private String title;
        private String description;
        private String trigger;
        @JsonbProperty("business_rules")
        private List<String> businessRules;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }
        public List<String> getBusinessRules() { return businessRules; }
        public void setBusinessRules(List<String> businessRules) { this.businessRules = businessRules; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }

    public static class DataEntity {
        private String name;
        private List<Field> fields;
        @JsonbProperty("sql_queries")
        private List<SqlQuery> sqlQueries;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<Field> getFields() { return fields; }
        public void setFields(List<Field> fields) { this.fields = fields; }
        public List<SqlQuery> getSqlQueries() { return sqlQueries; }
        public void setSqlQueries(List<SqlQuery> sqlQueries) { this.sqlQueries = sqlQueries; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }

        public static class Field {
            private String name;
            private String type;
            private boolean required;
            private String validation;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            public boolean isRequired() { return required; }
            public void setRequired(boolean required) { this.required = required; }
            public String getValidation() { return validation; }
            public void setValidation(String validation) { this.validation = validation; }
        }
    }

    public static class SqlQuery {
        private String sql;
        private String purpose;

        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
    }

    public static class BusinessRule {
        private String id;
        private String rule;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRule() { return rule; }
        public void setRule(String rule) { this.rule = rule; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }

    public static class UiElement {
        private String id;
        private String name;
        private String purpose;
        @JsonbProperty("form_type")
        private String formType;
        @JsonbProperty("form_class")
        private String formClass;
        @JsonbProperty("library_file")
        private String libraryFile;
        private Boolean modal;
        @JsonbProperty("fields_displayed")
        private List<String> fieldsDisplayed;
        @JsonbProperty("user_actions")
        private List<String> userActions;
        private List<Control> controls;
        private List<Component> components;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getFormType() { return formType; }
        public void setFormType(String formType) { this.formType = formType; }
        public String getFormClass() { return formClass; }
        public void setFormClass(String formClass) { this.formClass = formClass; }
        public String getLibraryFile() { return libraryFile; }
        public void setLibraryFile(String libraryFile) { this.libraryFile = libraryFile; }
        public Boolean getModal() { return modal; }
        public void setModal(Boolean modal) { this.modal = modal; }
        public List<String> getFieldsDisplayed() { return fieldsDisplayed; }
        public void setFieldsDisplayed(List<String> v) { this.fieldsDisplayed = v; }
        public List<String> getUserActions() { return userActions; }
        public void setUserActions(List<String> v) { this.userActions = v; }
        public List<Control> getControls() { return controls; }
        public void setControls(List<Control> v) { this.controls = v; }
        public List<Component> getComponents() { return components; }
        public void setComponents(List<Component> v) { this.components = v; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }

    public static class Control {
        private String name;
        private String type;
        private String action;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    public static class Component {
        private String name;
        private String type;
        @JsonbProperty("class_file")
        private String classFile;
        @JsonbProperty("target_table")
        private String targetTable;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getClassFile() { return classFile; }
        public void setClassFile(String classFile) { this.classFile = classFile; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    }

    public static class MenuItem {
        private String menu;
        private String label;
        private String command;
        @JsonbProperty("hot_key")
        private String hotKey;
        private String condition;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getMenu() { return menu; }
        public void setMenu(String menu) { this.menu = menu; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String getHotKey() { return hotKey; }
        public void setHotKey(String hotKey) { this.hotKey = hotKey; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }

    public static class ReferenceDataUsage {
        private String code;
        private String context;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }

    public static class SecurityCheck {
        private String resource;
        @JsonbProperty("check_expression")
        private String checkExpression;
        private String description;
        @JsonbProperty("source_snippet")
        private String sourceSnippet;

        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getCheckExpression() { return checkExpression; }
        public void setCheckExpression(String checkExpression) { this.checkExpression = checkExpression; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSourceSnippet() { return sourceSnippet; }
        public void setSourceSnippet(String sourceSnippet) { this.sourceSnippet = sourceSnippet; }
    }
}

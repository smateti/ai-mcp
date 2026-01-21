package com.naag.toolregistry.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ParameterDefinition entity.
 */
class ParameterDefinitionTest {

    @Nested
    @DisplayName("getEffectiveDescription Tests")
    class GetEffectiveDescriptionTests {

        @Test
        @DisplayName("Should return humanReadableDescription when set")
        void shouldReturnHumanReadableDescriptionWhenSet() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Original description");
            param.setHumanReadableDescription("Human readable description");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Human readable description");
        }

        @Test
        @DisplayName("Should return original description when humanReadableDescription is null")
        void shouldReturnOriginalDescriptionWhenHumanReadableIsNull() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Original description");
            param.setHumanReadableDescription(null);

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Original description");
        }

        @Test
        @DisplayName("Should return original description when humanReadableDescription is blank")
        void shouldReturnOriginalDescriptionWhenHumanReadableIsBlank() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Original description");
            param.setHumanReadableDescription("   ");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Original description");
        }

        @Test
        @DisplayName("Should append enum values to description")
        void shouldAppendEnumValuesToDescription() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Status field");
            param.setEnumValues("ACTIVE,INACTIVE,PENDING");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Status field. Allowed values: ACTIVE, INACTIVE, PENDING");
        }

        @Test
        @DisplayName("Should append enum values to humanReadableDescription when set")
        void shouldAppendEnumValuesToHumanReadableDescription() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Original status");
            param.setHumanReadableDescription("The current status of the item");
            param.setEnumValues("ACTIVE,INACTIVE");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("The current status of the item. Allowed values: ACTIVE, INACTIVE");
        }

        @Test
        @DisplayName("Should return only enum values when descriptions are empty")
        void shouldReturnOnlyEnumValuesWhenDescriptionsAreEmpty() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription(null);
            param.setHumanReadableDescription(null);
            param.setEnumValues("YES,NO");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Allowed values: YES, NO");
        }

        @Test
        @DisplayName("Should not append enum values when null")
        void shouldNotAppendEnumValuesWhenNull() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Field description");
            param.setEnumValues(null);

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Field description");
        }

        @Test
        @DisplayName("Should not append enum values when blank")
        void shouldNotAppendEnumValuesWhenBlank() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Field description");
            param.setEnumValues("   ");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Field description");
        }

        @Test
        @DisplayName("Should handle single enum value")
        void shouldHandleSingleEnumValue() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Type field");
            param.setEnumValues("ONLY_VALUE");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Type field. Allowed values: ONLY_VALUE");
        }

        @Test
        @DisplayName("Should return empty string when all values are null")
        void shouldReturnEmptyStringWhenAllValuesAreNull() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription(null);
            param.setHumanReadableDescription(null);
            param.setEnumValues(null);

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("");
        }

        @Test
        @DisplayName("Should format enum values with proper spacing")
        void shouldFormatEnumValuesWithProperSpacing() {
            ParameterDefinition param = new ParameterDefinition();
            param.setDescription("Priority");
            param.setEnumValues("LOW,MEDIUM,HIGH,CRITICAL");

            assertThat(param.getEffectiveDescription())
                    .isEqualTo("Priority. Allowed values: LOW, MEDIUM, HIGH, CRITICAL");
        }
    }
}

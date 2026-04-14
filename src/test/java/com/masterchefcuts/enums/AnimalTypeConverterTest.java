package com.masterchefcuts.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AnimalTypeConverterTest {

    private final AnimalTypeConverter converter = new AnimalTypeConverter();

    // ── convertToDatabaseColumn ───────────────────────────────────────────────

    @Test
    void convertToDatabaseColumn_beef_returnsBeefString() {
        assertThat(converter.convertToDatabaseColumn(AnimalType.BEEF)).isEqualTo("BEEF");
    }

    @Test
    void convertToDatabaseColumn_pork_returnsPorkString() {
        assertThat(converter.convertToDatabaseColumn(AnimalType.PORK)).isEqualTo("PORK");
    }

    @Test
    void convertToDatabaseColumn_lamb_returnsLambString() {
        assertThat(converter.convertToDatabaseColumn(AnimalType.LAMB)).isEqualTo("LAMB");
    }

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    // ── convertToEntityAttribute ──────────────────────────────────────────────

    @Test
    void convertToEntityAttribute_beefString_returnsBeefEnum() {
        assertThat(converter.convertToEntityAttribute("BEEF")).isEqualTo(AnimalType.BEEF);
    }

    @Test
    void convertToEntityAttribute_porkString_returnsPorkEnum() {
        assertThat(converter.convertToEntityAttribute("PORK")).isEqualTo(AnimalType.PORK);
    }

    @Test
    void convertToEntityAttribute_lambString_returnsLambEnum() {
        assertThat(converter.convertToEntityAttribute("LAMB")).isEqualTo(AnimalType.LAMB);
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    // ── AnimalType.fromString ─────────────────────────────────────────────────

    @Test
    void fromString_lowercaseInput_returnsCorrectEnum() {
        assertThat(AnimalType.fromString("beef")).isEqualTo(AnimalType.BEEF);
        assertThat(AnimalType.fromString("pork")).isEqualTo(AnimalType.PORK);
        assertThat(AnimalType.fromString("lamb")).isEqualTo(AnimalType.LAMB);
    }

    @Test
    void fromString_withWhitespace_returnsCorrectEnum() {
        assertThat(AnimalType.fromString("  BEEF  ")).isEqualTo(AnimalType.BEEF);
    }

    @Test
    void fromString_null_returnsNull() {
        assertThat(AnimalType.fromString(null)).isNull();
    }

    @Test
    void fromString_unknownValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> AnimalType.fromString("CHICKEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHICKEN");
    }
}

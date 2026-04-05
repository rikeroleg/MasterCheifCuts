package com.masterchefcuts.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AnimalTypeConverter implements AttributeConverter<AnimalType, String> {

    @Override
    public String convertToDatabaseColumn(AnimalType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AnimalType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return AnimalType.fromString(dbData);
    }
}

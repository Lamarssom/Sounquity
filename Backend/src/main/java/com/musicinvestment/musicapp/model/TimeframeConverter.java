package com.musicinvestment.musicapp.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TimeframeConverter implements AttributeConverter<Timeframe, String> {

    @Override
    public String convertToDatabaseColumn(Timeframe timeframe) {
        return timeframe == null ? null : timeframe.getValue();
    }

    @Override
    public Timeframe convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Timeframe.fromValue(dbData);
    }
}
package com.runtime.pivot.protocol;

import opamp.proto.v1.AnyValue;
import opamp.proto.v1.KeyValue;

public final class AttributeValues {
    private AttributeValues() {
    }

    public static KeyValue stringAttribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value == null ? "" : value).build())
                .build();
    }

    public static String stringValue(KeyValue attribute) {
        if (attribute == null || !attribute.hasValue()) {
            return null;
        }
        if (attribute.getValue().hasStringValue()) {
            return attribute.getValue().getStringValue();
        }
        return null;
    }

    public static Integer intStringValue(KeyValue attribute) {
        String value = stringValue(attribute);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

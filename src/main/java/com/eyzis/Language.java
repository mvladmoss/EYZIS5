package com.eyzis;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Language {

    ENGLISH("/english"),
    FRENCH("/french");

    private String value;

    Language(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values())
                .map(Language::getValue)
                .collect(Collectors.toList());
    }

    public static Language getFromValue(String value) {
        return Arrays.stream(values())
                .filter(language -> language.getValue().equals(value))
                .findFirst().get();
    }
}

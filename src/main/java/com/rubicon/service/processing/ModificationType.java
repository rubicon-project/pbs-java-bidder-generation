package com.rubicon.service.processing;

public enum ModificationType {
    IMP("imp.", "imp"),
    REQUEST("bidRequest.", "request");

    private final String path;
    private final String name;

    ModificationType(String path, String name) {
        this.path = path;
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }
}

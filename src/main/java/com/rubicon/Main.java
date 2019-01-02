package com.rubicon;

import com.rubicon.service.TemplateProcessing;

public class Main {

    private static final String INPUT_FILE = "src/test_input.json";
    private static final String TEMPLATES_DIRECTORY = "src/main/resources/templates";

    public static void main(String[] args) {

        TemplateProcessing templateProcessing = new TemplateProcessing(INPUT_FILE, TEMPLATES_DIRECTORY);

        templateProcessing.createBidderFiles("meta_info.ftl", "usersyncer.ftl",
                "properties.ftl", "configuration.ftl");
    }
}

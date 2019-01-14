package com.rubicon;

import com.rubicon.service.TemplateProcessing;

public class Main {

    //private static final String PBS_DIRECTORY = "C:/Users/rostyslav.goncharuk/IdeaProjects/prebid-server-java";
    //private static final String INPUT_FILE = "src/test_input.json";
    private static final String TEMPLATES_DIRECTORY = "src/main/resources/templates";

    public static void main(String[] args) {

        if (args.length != 2) {
            throw new IllegalArgumentException();
        }
        final String inputFile = args[0];
        final String pbsDirectory = args[1];

        TemplateProcessing templateProcessing = new TemplateProcessing(inputFile, TEMPLATES_DIRECTORY, pbsDirectory);

        templateProcessing.createBidderFiles("meta_info.ftl", "usersyncer.ftl",
                "properties.ftl", "configuration.ftl", "schema.ftl",
                "usersyncer_test.ftl", "bidder_test.ftl");
    }
}

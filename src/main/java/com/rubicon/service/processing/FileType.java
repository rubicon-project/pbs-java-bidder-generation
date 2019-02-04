package com.rubicon.service.processing;

public enum FileType {
    BIDDER("/src/main/java/", ""),
    USERSYNCER("/src/main/java/org/prebid/server/bidder/", "Usersyncer.java"),
    EXT("/src/main/java/", ""),
    CONFIG("/src/main/java/org/prebid/server/spring/config/bidder/", "Configuration.java"),
    PROPERTIES("/src/main/resources/bidder-config/", ".yaml"),
    SCHEMA("/src/main/resources/static/bidder-params/", ".json"),
    TEST_USERSYNCER("/src/addBuilderField/java/org/prebid/server/bidder/", "UsersyncerTest.java"),
    TEST_BIDDER("/src/addBuilderField/java/org/prebid/server/bidder/", "BidderTest.java");

    private final String filePrefix;
    private final String fileSuffix;

    FileType(String filePrefix, String fileSuffix) {
        this.filePrefix = filePrefix;
        this.fileSuffix = fileSuffix;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }
}

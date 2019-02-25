package com.rubicon.service.processing;

public enum FileType {
    BIDDER("/src/main/java/", ""),
    EXT("/src/main/java/", ""),
    BIDDER_TEST("/src/test/java/", ""),
    CONFIG("/src/main/java/org/prebid/server/spring/config/bidder/", "Configuration.java"),
    PROPERTIES("/src/main/resources/bidder-config/", ".yaml"),
    SCHEMA("/src/main/resources/static/bidder-params/", ".json"),
    TEST_SIMPLE_BIDDER("/src/test/java/org/prebid/server/bidder/", "BidderTest.java");

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

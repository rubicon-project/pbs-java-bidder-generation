package com.rubicon.service.processing;

import com.rubicon.model.BidderData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
class FileCreator {

    String makeBidderFile(BidderData bidderData, FileType fileType) throws IOException {
        final String filePath = bidderData.getPbsDirectory() + getPbsFilePath(bidderData, fileType);
        final Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        if (!(fileType.equals(FileType.BIDDER) || fileType.equals(FileType.EXT))) {
            Files.createFile(path);
        }
        System.out.println(filePath);

        return filePath;
    }

    private static String getPbsFilePath(BidderData bidderData, FileType fileType) {
        return fileType.filePrefix + resolveFilePackageAndName(bidderData, fileType) + fileType.fileSufix;
    }

    private static String resolveFilePackageAndName(BidderData bidderData, FileType fileType) {
        final String bidderName = bidderData.getBidderName();
        final String capitalizedName = StringUtils.capitalize(bidderName);
        switch (fileType) {
            case USERSYNCER:
                return bidderName.toLowerCase() + "/" + capitalizedName;
            case PROPERTIES:
                return bidderName.toLowerCase();
            case SCHEMA:
                return bidderName;
            case CONFIG:
                return capitalizedName;
            case TEST_USERSYNCER:
                return bidderName.toLowerCase() + "/" + capitalizedName;
            case TEST_BIDDER:
                return bidderName.toLowerCase() + "/" + capitalizedName;
            default:
                return "";
        }
    }

    public enum FileType {
        BIDDER("/src/main/java/", ""),
        USERSYNCER("/src/main/java/org/prebid/server/bidder/", "Usersyncer.java"),
        EXT("/src/main/java/", ""),
        CONFIG("/src/main/java/org/prebid/server/spring/config/bidder/", "Configuration.java"),
        PROPERTIES("/src/main/resources/bidder-config/", ".yaml"),
        SCHEMA("/src/main/resources/static/bidder-params/", ".json"),
        TEST_USERSYNCER("/src/test/java/org/prebid/server/bidder/", "UsersyncerTest.java"),
        TEST_BIDDER("/src/test/java/org/prebid/server/bidder/", "BidderTest.java");

        private final String filePrefix;
        private final String fileSufix;

        FileType(String filePrefix, String fileSufix) {
            this.filePrefix = filePrefix;
            this.fileSufix = fileSufix;
        }
    }
}

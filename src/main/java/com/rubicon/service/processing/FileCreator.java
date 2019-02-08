package com.rubicon.service.processing;

import com.rubicon.model.BidderData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileCreator {

    String makeBidderFile(BidderData bidderData, FileType fileType) throws IOException {
        final String filePath = bidderData.getPbsDirectory() + getPbsFilePath(bidderData, fileType);
        final Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        if (!(fileType.equals(FileType.BIDDER) || fileType.equals(FileType.EXT)
                || fileType.equals(FileType.BIDDER_TEST))) {
            Files.createFile(path);
        }
        System.out.println(filePath);

        return filePath;
    }

    private static String getPbsFilePath(BidderData bidderData, FileType fileType) {
        return fileType.getFilePrefix() + resolveFilePackageAndName(bidderData, fileType) + fileType.getFileSuffix();
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
            case TEST_SIMPLE_BIDDER:
                return bidderName.toLowerCase() + "/" + capitalizedName;
            default:
                return "";
        }
    }
}

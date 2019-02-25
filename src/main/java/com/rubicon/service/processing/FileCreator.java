package com.rubicon.service.processing;

import com.rubicon.model.BidderData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
public class FileCreator {

    String makeBidderFile(BidderData bidderData, FileType fileType) throws IOException {
        final String filePath = getAbsolutePbsDirectoryPath() + getPbsFilePath(bidderData, fileType);
        final Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());

        final List<FileType> nonTemplateFiles = Arrays.asList(FileType.BIDDER, FileType.EXT, FileType.BIDDER_TEST);
        if (!nonTemplateFiles.contains(fileType)) {
            try {
                Files.createFile(path);
            } catch (FileAlreadyExistsException e) {
                Files.delete(path);
                Files.createFile(path);
            }
        }
        return filePath;
    }

    private static String getAbsolutePbsDirectoryPath() {
        final Path currentRelativePath = Paths.get("");
        final String bgtPath = currentRelativePath.toAbsolutePath().toString();
        return StringUtils.replace(bgtPath, "pbs-java-bidder-generation", "prebid-server-java");
    }

    private static String getPbsFilePath(BidderData bidderData, FileType fileType) {
        return fileType.getFilePrefix() + resolveFilePackageAndName(bidderData, fileType) + fileType.getFileSuffix();
    }

    private static String resolveFilePackageAndName(BidderData bidderData, FileType fileType) {
        final String bidderName = bidderData.getBidderName();
        final String capitalizedName = StringUtils.capitalize(bidderName);
        switch (fileType) {
            case PROPERTIES:
                return bidderName.toLowerCase();
            case SCHEMA:
                return bidderName;
            case CONFIG:
                return capitalizedName;
            case TEST_SIMPLE_BIDDER:
                return bidderName.toLowerCase() + "/" + capitalizedName;
            default:
                return "";
        }
    }
}

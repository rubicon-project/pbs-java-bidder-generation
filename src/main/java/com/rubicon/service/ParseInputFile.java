package com.rubicon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubicon.model.BidderData;
import com.rubicon.model.MetaInfoData;
import com.rubicon.model.PropertiesData;
import com.rubicon.model.UsersyncerData;

import java.io.FileReader;
import java.io.IOException;

public class ParseInputFile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static BidderData parseInputFile(String inputFile) {
        try (FileReader reader = new FileReader(inputFile)) {
            return OBJECT_MAPPER.convertValue(OBJECT_MAPPER.readTree(reader), BidderData.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PropertiesData parsePropertiesData(BidderData bidderData) {
        final PropertiesData propertiesData = bidderData.getProperties();
        propertiesData.setBidderName(bidderData.getBidderName());
        return propertiesData;
    }

    public static MetaInfoData parseMetaInfoData(BidderData bidderData) {
        final MetaInfoData metaInfoData = bidderData.getMetaInfo();
        metaInfoData.setBidderName(bidderData.getBidderName());
        return metaInfoData;
    }

    public static UsersyncerData parseUsersyncerData(BidderData bidderData) {
        final UsersyncerData usersyncerData = bidderData.getUsersyncer();
        usersyncerData.setBidderName(bidderData.getBidderName());
        return usersyncerData;
    }
}

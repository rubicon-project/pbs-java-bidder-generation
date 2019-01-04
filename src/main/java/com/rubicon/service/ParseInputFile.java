package com.rubicon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubicon.model.BidderData;
import com.rubicon.model.MetaInfoData;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    public static Map<String, String> parsePropertiesData(BidderData bidderData) {
        final Map<String, String> propertiesData = new HashMap<>();
        propertiesData.put("bidderName", bidderData.getBidderName());
        propertiesData.put("endpointUrl", bidderData.getProperties().getEndpointUrl());
        propertiesData.put("usersyncerUrl", bidderData.getProperties().getUsersyncerUrl());
        return propertiesData;
    }

    public static MetaInfoData parseMetaInfoData(BidderData bidderData) {
        final MetaInfoData metaInfoData = bidderData.getMetaInfo();
        metaInfoData.setBidderName(bidderData.getBidderName());
        return metaInfoData;
    }

    public static Map<String, String> getUsersyncerData(BidderData bidderData) {
        final Map<String, String> usersyncerData = new HashMap<>();
        usersyncerData.put("bidderName", bidderData.getBidderName());
        usersyncerData.put("cookieFamilyName", bidderData.getCookieFamilyName());
        return usersyncerData;
    }
}

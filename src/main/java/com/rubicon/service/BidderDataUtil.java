package com.rubicon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubicon.model.BidderData;
import com.rubicon.model.MetaInfoData;

import java.util.HashMap;
import java.util.Map;

public class BidderDataUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

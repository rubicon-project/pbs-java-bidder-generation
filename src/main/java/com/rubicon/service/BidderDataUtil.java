package com.rubicon.service;

import com.rubicon.model.BidderData;
import com.rubicon.model.PropertiesData;
import com.rubicon.model.UsersyncerData;

public class BidderDataUtil {

    public static PropertiesData preparePropertiesData(BidderData bidderData) {
        final PropertiesData propertiesData = bidderData.getProperties();
        propertiesData.setBidderName(bidderData.getBidderName());
        return propertiesData;
    }

    public static UsersyncerData getUsersyncerData(BidderData bidderData) {
        final UsersyncerData usersyncerData = bidderData.getUsersyncer();
        usersyncerData.setBidderName(bidderData.getBidderName());
        return usersyncerData;
    }
}

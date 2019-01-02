package com.rubicon.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BidderData {

    String bidderName;

    PropertiesData properties;

    MetaInfoData metaInfo;

    UsersyncerData usersyncer;

    ExtData ext;

    BidderImplData bidder;
}

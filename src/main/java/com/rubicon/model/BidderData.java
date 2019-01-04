package com.rubicon.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class BidderData {

    String bidderName;

    String cookieFamilyName;

    PropertiesData properties;

    MetaInfoData metaInfo;

    List<BidderParam> bidderParams;

    List<Transformation> transformations;
}

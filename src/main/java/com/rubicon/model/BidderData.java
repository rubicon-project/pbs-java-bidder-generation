package com.rubicon.model;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Value
public class BidderData {

    @NotNull
    String bidderName;

    @NotNull
    String cookieFamilyName;

    PropertiesData properties;

    @NotNull
    MetaInfoData metaInfo;

    List<BidderParam> bidderParams;

    List<Transformation> transformations;
}

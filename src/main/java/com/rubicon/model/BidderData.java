package com.rubicon.model;

import lombok.Builder;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Value
public class BidderData {

    @NotBlank
    String strategy;

    @NotBlank
    String bidderName;

    String urlParams;

    @Valid
    @NotNull
    PropertiesData properties;

    List<BidderParam> bidderParams;

    List<Transformation> transformations;
}

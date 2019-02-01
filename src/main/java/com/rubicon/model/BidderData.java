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
    String pbsDirectory;

    @NotBlank
    String strategy;

    @NotBlank
    String bidderName;

    @Valid
    @NotNull
    UsersyncerData usersyncer;

    @Valid
    @NotNull
    PropertiesData properties;

    List<BidderParam> bidderParams;

    List<Transformation> transformations;
}

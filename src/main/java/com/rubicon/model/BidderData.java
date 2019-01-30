package com.rubicon.model;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Value
public class BidderData {

    @NotBlank
    String pbsDirectory;

    @NotBlank
    String bidderName;

    @NotNull
    UsersyncerData usersyncer;

    @NotNull
    PropertiesData properties;

    List<BidderParam> bidderParams;

    List<Transformation> transformations;
}

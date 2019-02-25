package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@AllArgsConstructor
@Data
public class PropertiesData {

    String bidderName;

    @NotBlank
    String endpointUrl;

    @NotBlank
    String usersyncerUrl;

    String uidPlaceholder;

    @NotBlank
    String maintainerEmail;

    List<String> appMediaTypes;

    List<String> siteMediaTypes;

    @NotNull
    Integer vendorId;
}

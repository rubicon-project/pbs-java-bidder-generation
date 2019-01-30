package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class PropertiesData {

    String bidderName;

    String endpointUrl;

    String usersyncerUrl;

    String maintainerEmail;

    List<String> appMediaTypes;

    List<String> siteMediaTypes;

    List<String> supportedVendors;

    Integer vendorId;
}

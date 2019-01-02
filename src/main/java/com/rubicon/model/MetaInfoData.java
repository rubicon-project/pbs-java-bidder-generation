package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class MetaInfoData {

    String bidderName;

    String maintainerEmail;

    List<String> appMediaTypes;

    List<String> siteMediaTypes;

    Integer vendorId;
}

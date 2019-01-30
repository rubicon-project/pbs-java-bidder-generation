package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@AllArgsConstructor
@Data
public class UsersyncerData {

    String bidderName;

    @NotBlank
    String cookieFamilyName;

    @NotBlank
    String urlPrefix;
}

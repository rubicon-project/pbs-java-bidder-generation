package com.rubicon.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Transformation {

    String target;

    JsonNode staticValue;

    String from;
}

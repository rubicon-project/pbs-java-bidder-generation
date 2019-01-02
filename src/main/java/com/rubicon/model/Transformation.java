package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Transformation {

    String target;

    String staticValue;

    String from;
}

package com.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class ExtData {

    List<ClassField> fields;
}

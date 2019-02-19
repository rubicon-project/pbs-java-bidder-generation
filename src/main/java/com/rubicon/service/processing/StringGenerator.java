package com.rubicon.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rubicon.model.BidderData;
import com.rubicon.model.BidderParam;
import com.rubicon.model.Transformation;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class StringGenerator {

    public String addTransformationString(Transformation transformation, ModificationType type) {
        final String target = transformation.getTarget();
        final JsonNode staticValue = transformation.getStaticValue();
        final String value = staticValue != null ? resolveStaticValue(staticValue) : transformation.getFrom();
        final String[] targetPath = StringUtils.split(target, ".");
        if (type.equals(ModificationType.IMP) && targetPath.length > 2) {
            throw new IllegalArgumentException("not supported transformation");
        }
        return staticValue != null
                ? createStaticTransformationString(value, targetPath)
                : createDynamicTransformationString(value, targetPath, type);
    }

    public void addTopFieldBuilder(MethodSpec.Builder method, String sourceString, ClassName className,
                                   String fieldName, String capName) {
        method.addStatement("final $T $L = $Lget$L()", className, fieldName, sourceString, capName);
        resolveFieldBuilderString(method, className, fieldName, capName);
    }

    public void addNestedFieldBuilder(MethodSpec.Builder method, ClassName className,
                                      String topFieldName, String fieldName, String capName) {
        method.addStatement("final $T $L = $L != null ? $L.get$L() : null", className, fieldName, topFieldName,
                topFieldName, capName);
        resolveFieldBuilderString(method, className, fieldName, capName);
    }

    private static void resolveFieldBuilderString(MethodSpec.Builder method, ClassName className, String fieldName,
                                                  String capName) {
        method.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                className, capName, fieldName, fieldName, fieldName, className);
    }

    public void resolveMethodBody(MethodSpec.Builder method, Map<String, List<Transformation>> fieldToTransformations,
                                  ModificationType modificationType, String topFieldName) {
        final boolean isImpModification = modificationType.equals(ModificationType.IMP);
        for (Map.Entry<String, List<Transformation>> field : fieldToTransformations.entrySet()) {
            final List<Transformation> transformations = field.getValue();
            final String fieldName = field.getKey();
            final String capField = StringUtils.capitalize(fieldName);
            final ClassName fieldClass = ClassName.get("com.iab.openrtb.request", capField);

            if (transformations.size() > 1) {
                addMethodTransformationsGroup(method, transformations, topFieldName,
                        fieldClass, fieldName, capField, modificationType);
            } else {
                final Transformation singleTransformation = transformations.get(0);
                final String target = singleTransformation.getTarget();
                final String[] targetPath = StringUtils.split(target, ".");
                final String modFieldName = modificationType.getName();
                if (targetPath.length == 1) {
                    addBuilderField(method, singleTransformation, modificationType, modFieldName, null);
                } else if (targetPath.length == 2) {
                    if (isImpModification) {
                        addTopFieldBuilder(method, modificationType.getPath(), fieldClass, fieldName, capField);
                        addBuilderField(method, singleTransformation, modificationType, modFieldName, fieldName);
                    } else {
                        addBuilderField(method, singleTransformation, modificationType, topFieldName, null);
                    }
                } else {
                    addNestedFieldBuilder(method, fieldClass, topFieldName, fieldName, capField);
                    addBuilderField(method, singleTransformation, modificationType, topFieldName, fieldName);
                }
            }
        }
        if (isImpModification) {
            method.addStatement("return impBuilder.build()");
        } else {
            method.addStatement("requestBuilder.$L($LBuilder.build())", topFieldName, topFieldName);
        }
    }

    private void addMethodTransformationsGroup(MethodSpec.Builder method, List<Transformation> transformations,
                                               String topFieldName, ClassName className,
                                               String fieldName, String capName, ModificationType modificationType) {
        if (modificationType.equals(ModificationType.IMP)) {
            final String source = modificationType.getPath();
            addTopFieldBuilder(method, source, className, fieldName, capName);
            addStatementForEachTransformation(method, transformations, fieldName, modificationType);
            method.addStatement("impBuilder.$L($LBuilder.build())", fieldName, fieldName);
        } else {
            addNestedFieldBuilder(method, className, topFieldName, fieldName, capName);
            addStatementForEachTransformation(method, transformations, fieldName, modificationType);
            method.addStatement("$LBuilder.$L($LBuilder.build())", topFieldName, fieldName, fieldName);
        }
    }

    private void addStatementForEachTransformation(MethodSpec.Builder method, List<Transformation> transformations,
                                                   String fieldName, ModificationType modificationType) {
        for (Transformation transformation : transformations) {
            method.addStatement("$LBuilder$L", fieldName, addTransformationString(transformation, modificationType));
        }
    }

    public void addBuilderField(MethodSpec.Builder method, Transformation transformation,
                                ModificationType modificationType, String topFieldName, String fieldName) {
        final StringBuilder builder = new StringBuilder(topFieldName)
                .append("Builder");
        if (fieldName != null) {
            builder.append(".").append(fieldName).append("(").append(fieldName).append("Builder$L.build())");
        } else {
            builder.append("$L");
        }
        method.addStatement(builder.toString(), addTransformationString(transformation, modificationType));
    }

    public String resolveExt(BidderData bidderData) {
        final List<BidderParam> bidderParams = bidderData.getBidderParams();
        if (CollectionUtils.isEmpty(bidderParams)) {
            return ".ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))\n";
        }
        return bidderParams.size() > 4
                ? addExtBuilder(bidderParams)
                : addExtConstructor(bidderParams);
    }

    private static String addExtConstructor(List<BidderParam> params) {
        int numValue = 1;
        final StringBuilder builder = new StringBuilder(".ext(mapper.valueToTree(ExtPrebid.of(null,\n")
                .append("$T")
                .append(".of(");
        final StringJoiner joiner = new StringJoiner(",");
        for (BidderParam param : params) {
            joiner.add(resolveParamValue(param, numValue++));
        }
        return builder.append(joiner.toString()).append("))))\n").toString();
    }

    private static String addExtBuilder(List<BidderParam> params) {
        Integer numValue = 1;
        final StringBuilder builder = new StringBuilder(".ext(mapper.valueToTree(ExtPrebid.of(null, $T")
                .append(".builder()\n");
        for (BidderParam param : params) {
            builder.append(".").append(param.getName()).append("(").append(resolveParamValue(param, numValue)).append(")\n");
            final String type = param.getType();
            if (!type.equals("String") && !type.equals("Boolean")) {
                numValue++;
            }
        }
        return builder.append(".build())))\n").toString();
    }

    private static String resolveParamValue(BidderParam param, Integer startNumValue) {
        final String type = param.getType();
        switch (type) {
            case "String":
                return "\"" + param.getName() + "String" + "\"";
            case "Double":
                return String.valueOf(Double.valueOf(startNumValue));
            case "Float":
                return String.valueOf(Float.valueOf(startNumValue)) + "F";
            case "Long":
                return String.valueOf(Long.valueOf(startNumValue)) + "L";
            case "Boolean":
                return String.valueOf(true);
            default:
                return String.valueOf(startNumValue);
        }
    }

    private static String resolveStaticValue(JsonNode jsonNode) {
        switch (jsonNode.getNodeType()) {
            case NULL:
                return null;
            case NUMBER:
                return jsonNode.asText();
            case STRING:
                return "\"" + jsonNode.asText() + "\"";
            default:
                throw new IllegalArgumentException();
        }
    }

    private static String createStaticTransformationString(String staticValue, String[] targetPath) {
        return String.format(".%s(%s)", targetPath[targetPath.length - 1], staticValue);
    }

    private String createDynamicTransformationString(String from, String[] targetPath, ModificationType type) {
        if (type.equals(ModificationType.IMP)) {
            return from.contains("impExt.")
                    ? createFromExtField(StringUtils.replace(from, "impExt.", ""), targetPath)
                    : createFromImpField(StringUtils.replace(from, "imp.", ""), targetPath);
        }
        return createFromRequestField(from, targetPath);
    }

    private static String createFromExtField(String extField, String[] targetPath) {
        return resolveFieldString(extField, "impExt", targetPath);
    }

    private static String createFromImpField(String impField, String[] targetPath) {
        return resolveFieldString(impField, "imp", targetPath);
    }

    private static String createFromRequestField(String requestField, String[] targetPath) {
        return resolveFieldString(requestField, "bidRequest", targetPath);
    }

    private static String resolveFieldString(String field, String filedPrefix, String[] targetPath) {
        final String[] filedPath = StringUtils.split(field, ".");
        final StringBuilder builder = new StringBuilder(".")
                .append(targetPath[targetPath.length - 1])
                .append("(")
                .append(filedPrefix);
        for (String fieldPart : filedPath) {
            builder.append(".get")
                    .append(StringUtils.capitalize(fieldPart))
                    .append("()");
        }
        return builder.append(")").toString();
    }

    public String resolveGivenBidRequestString(BidderData bidderData) {
        final StringBuilder builder = new StringBuilder("final BidRequest bidRequest = givenBidRequest(\n");

        final List<Transformation> fromImpOrRequestFields = bidderData.getTransformations().stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getFrom()))
                .filter(transformation -> !transformation.getFrom().contains("impExt."))
                .collect(Collectors.toList());

        int numValue = 1;
        final Set<Transformation> fromImpField = fromImpOrRequestFields.stream()
                .filter(transformation -> transformation.getFrom().contains("imp."))
                .map(transformation -> new Transformation(transformation.getTarget(), null,
                        StringUtils.replace(transformation.getFrom(), "imp.", "")))
                .collect(Collectors.toSet());

        if (!fromImpField.isEmpty()) {
            builder.append("impBuilder -> impBuilder\n");

            final Map<String, List<Transformation>> fromToTransformations = fromImpField.stream()
                    .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getFrom(), ".")[0]));

            for (Map.Entry<String, List<Transformation>> field : fromToTransformations.entrySet()) {
                final List<Transformation> transformations = field.getValue();
                final String topFieldName = field.getKey();
                final String capTopField = StringUtils.capitalize(topFieldName);
                final ClassName topFieldClass = ClassName.get("com.iab.openrtb.request", capTopField);

                if (transformations.size() > 1) {
                    builder.append(".").append(topFieldName).append("(").append(topFieldClass)
                            .append(".builder()");
                    for (Transformation transformation : transformations) {
                        final String[] fromSplit = StringUtils.split(transformation.getFrom(), ".");
                        final String targetField = fromSplit[fromSplit.length - 1];
                        final boolean isIntField = integerFields.contains(targetField);
                        final String value = resolveParamValue(new BidderParam(targetField, isIntField ? "Integer" : "String"),
                                numValue++);
                        builder.append(".").append(fromSplit[1]).append("(").append(value).append(")");
                    }
                    builder.append(".build())\n");
                } else {
                    final Transformation singleTransformation = transformations.get(0);
                    final String[] splitFrom = StringUtils.split(singleTransformation.getFrom(), ".");
                    final String targetField = splitFrom[splitFrom.length - 1];
                    final boolean isIntField = integerFields.contains(targetField);
                    final String value = resolveParamValue(new BidderParam(targetField, isIntField ? "Integer" : "String"),
                            numValue++);
                    if (splitFrom.length == 1) {
                        builder.append(".").append(splitFrom[0]).append("(").append(value).append(")\n");
                    } else {
                        builder.append(".").append(splitFrom[0]).append("(").append(topFieldClass)
                                .append(".builder().").append(targetField).append("(").append(value).append(").build())\n");
                    }
                }
            }
            builder.append(",\n");
        } else {
            builder.append("identity(),\n");
        }

        final Set<Transformation> fromRequestField = fromImpOrRequestFields.stream()
                .filter(transformation -> !transformation.getFrom().contains("imp."))
                .collect(Collectors.toSet());

        if (!fromRequestField.isEmpty()) {
            builder.append("requestBuilder -> requestBuilder\n");

            final Map<String, List<Transformation>> fromToTransformations = fromRequestField.stream()
                    .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getFrom(), ".")[0]));

            for (Map.Entry<String, List<Transformation>> field : fromToTransformations.entrySet()) {
                final List<Transformation> transformations = field.getValue();
                final String topFieldName = field.getKey();
                final String capTopField = StringUtils.capitalize(topFieldName);
                final ClassName topFieldClass = ClassName.get("com.iab.openrtb.request", capTopField);

                if (transformations.size() > 1) {
                    builder.append(".").append(topFieldName).append("(").append(topFieldClass)
                            .append(".builder()");

                    final Map<String, List<Transformation>> midFromToTransformations = transformations.stream()
                            .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getFrom(),
                                    ".")[1]));

                    for (Map.Entry<String, List<Transformation>> midField : midFromToTransformations.entrySet()) {
                        final List<Transformation> midTransformations = midField.getValue();
                        final String midFieldName = midField.getKey();
                        final String capMidField = StringUtils.capitalize(midFieldName);
                        final ClassName midFieldClass = ClassName.get("com.iab.openrtb.request", capMidField);

                        if (midTransformations.size() > 1) {
                            builder.append(".").append(midFieldName).append("(").append(midFieldClass)
                                    .append(".builder()\n");
                            for (Transformation transformation : midTransformations) {
                                final String[] fromSplit = StringUtils.split(transformation.getFrom(), ".");
                                final String targetField = fromSplit[fromSplit.length - 1];
                                final boolean isIntField = integerFields.contains(targetField);
                                final String value = resolveParamValue(new BidderParam(
                                        targetField, isIntField ? "Integer" : "String"), numValue++);
                                builder.append(".").append(fromSplit[2]).append("(").append(value).append(")\n");
                            }
                            builder.append(".build())\n");
                        } else {
                            final Transformation singleTransformation = midTransformations.get(0);
                            final String[] fromSplit = StringUtils.split(singleTransformation.getFrom(), ".");
                            final String targetField = fromSplit[fromSplit.length - 1];
                            final boolean isIntField = integerFields.contains(targetField);
                            final String value = resolveParamValue(new BidderParam(targetField, isIntField ? "Integer" : "String"),
                                    numValue++);
                            builder.append(".");
                            if (fromSplit.length == 2) {
                                builder.append(fromSplit[1]).append("(").append(value).append(")\n");
                            } else {
                                builder.append(fromSplit[1]).append("(").append(midFieldClass)
                                        .append(".builder().").append(fromSplit[2]).append("(")
                                        .append(value).append(").build())\n");
                            }
                        }
                    }
                    builder.append(".build())\n");
                } else {
                    final Transformation singleTransformation = transformations.get(0);
                    final String[] splitFrom = StringUtils.split(singleTransformation.getFrom(), ".");
                    final String targetField = splitFrom[splitFrom.length - 1];
                    final boolean isIntField = integerFields.contains(targetField);
                    final String value = resolveParamValue(new BidderParam(targetField, isIntField ? "Integer" : "String"),
                            numValue++);
                    if (splitFrom.length == 1) {
                        builder.append(".").append(splitFrom[0]).append("(").append(value).append(")\n");
                    } else if (splitFrom.length == 2){
                        builder.append(".").append(splitFrom[0]).append("(").append(topFieldClass)
                                .append(".builder().").append(targetField).append("(").append(value).append(").build())\n");
                    } else if (splitFrom.length == 3){
                        final ClassName midFieldClass = ClassName.get("com.iab.openrtb.request",
                                StringUtils.capitalize(splitFrom[1]));
                        builder.append(".").append(splitFrom[0]).append("(").append(topFieldClass)
                                .append(".builder().").append(splitFrom[1]).append("(")
                                .append(midFieldClass).append(".builder().")
                                .append(targetField).append("(").append(value).append(").build()).build())\n");
                    } else {
                        final ClassName secondFieldClass = ClassName.get("com.iab.openrtb.request",
                                StringUtils.capitalize(splitFrom[1]));
                        final ClassName thirdFieldClass = ClassName.get("com.iab.openrtb.request",
                                StringUtils.capitalize(splitFrom[2]));
                        builder.append(".").append(splitFrom[0]).append("(").append(topFieldClass)
                                .append(".builder().").append(splitFrom[1]).append("(")
                                .append(secondFieldClass).append(".builder().")
                                .append(splitFrom[2]).append("(")
                                .append(thirdFieldClass).append(".builder().")
                                .append(targetField).append("(").append(value).append(").build()).build()).build())\n");
                    }
                }
            }
        } else {
            builder.append("identity()");
        }
        return builder.append(");").toString();
    }

    private static List integerFields = Arrays.asList("w", "h", "at", "pos", "topframe", "minduration", "maxduration",
            "startdelay", "placement", "sequence", "minbitrate", "maxbitrate", "secure");

    public String resolveExpectedBidRequestString(BidderData bidderData) {
        final StringBuilder builder = new StringBuilder("final BidRequest expectedRequest = bidRequest.toBuilder()\n");

        final Map<String, List<Transformation>> fieldToTransformations = bidderData.getTransformations().stream()
                .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getTarget(), ".")[0]));

        for (Map.Entry<String, List<Transformation>> field : fieldToTransformations.entrySet()) {
            final List<Transformation> transformations = field.getValue();
            final String topFieldName = field.getKey();
            final String capTopField = StringUtils.capitalize(topFieldName);
            final ClassName topFieldClass = ClassName.get("com.iab.openrtb.request", capTopField);
            final boolean isImp = topFieldName.equals("imp");

            if (transformations.size() > 1) {
                builder.append(".");
                if (isImp) {
                    builder.append("imp(singletonList(bidRequest.getImp().get(0).toBuilder()\n");
                } else {
                    builder.append(topFieldName).append("(").append("(bidRequest.get").append(capTopField)
                            .append("() == null ? ").append(topFieldClass)
                            .append(".builder() : bidRequest.get").append(capTopField).append("().toBuilder())\n");
                }

                final Map<String, List<Transformation>> midFieldToTransformations = transformations.stream()
                        .collect(Collectors.groupingBy(tr -> StringUtils.split(tr.getTarget(), ".")[1]));

                for (Map.Entry<String, List<Transformation>> midField : midFieldToTransformations.entrySet()) {
                    final List<Transformation> midTransformations = midField.getValue();
                    final String midFieldName = midField.getKey();
                    final String capMidField = StringUtils.capitalize(midFieldName);
                    final ClassName midFieldClass = ClassName.get("com.iab.openrtb.request", capMidField);

                    if (midTransformations.size() > 1) {
                        builder.append(".");
                        if (isImp) {
                            builder.append(midFieldName).append("(bidRequest.getImp().get(0).get").append(capMidField)
                                    .append("().toBuilder()\n");
                        } else {
                            builder.append(midFieldName).append("(").append(midFieldClass)
                                    .append(".builder()\n");
                        }

                        for (Transformation transformation : midTransformations) {
                            final String[] targetSplit = StringUtils.split(transformation.getTarget(), ".");
                            builder.append(".").append(targetSplit[2]).append("(")
                                    .append(resolveValue(transformation, bidderData))
                                    .append(")\n");
                        }

                        builder.append(".build())\n");
                    } else {
                        final Transformation singleTransformation = midTransformations.get(0);
                        final String target = singleTransformation.getTarget();
                        final String[] targetPath = StringUtils.split(target, ".");
                        builder.append(".");
                        if (targetPath.length == 2) {
                            builder.append(targetPath[1]).append("(")
                                    .append(resolveValue(singleTransformation, bidderData)).append(")\n");
                        } else {
                            builder.append(targetPath[1]).append("(").append(midFieldClass)
                                    .append(".builder().").append(targetPath[2]).append("(")
                                    .append(resolveValue(singleTransformation, bidderData)).append(").build())\n");
                        }
                    }
                }
                if (isImp) {
                    builder.append(".build()))\n");
                } else {
                    builder.append(".build())\n");
                }

            } else {
                final Transformation singleTransformation = transformations.get(0);
                final String target = singleTransformation.getTarget();
                final String[] targetPath = StringUtils.split(target, ".");
                builder.append(".");
                if (targetPath.length == 1) {
                    builder.append(targetPath[0]).append("(").append(resolveValue(singleTransformation, bidderData))
                            .append(")\n");
                } else if (targetPath.length == 2) {
                    if (isImp) {
                        builder.append("imp(singletonList(bidRequest.getImp().get(0).toBuilder()\n.").append(targetPath[1])
                                .append("(").append(resolveValue(singleTransformation, bidderData))
                                .append(").build()))\n");
                    } else {
                        builder.append(targetPath[0]).append("(").append(topFieldClass)
                                .append(".builder().").append(targetPath[1]).append("(")
                                .append(resolveValue(singleTransformation, bidderData)).append(").build())\n");
                    }
                } else {
                    final ClassName midFieldClass = ClassName.get("com.iab.openrtb.request",
                            StringUtils.capitalize(targetPath[1]));
                    if (isImp) {
                        builder.append("imp(singletonList(bidRequest.getImp().get(0).toBuilder()\n.").append(targetPath[1])
                                .append("(").append(midFieldClass).append(".builder().")
                                .append(targetPath[2]).append("(")
                                .append(resolveValue(singleTransformation, bidderData))
                                .append(").build()).build()))\n");
                    } else {
                        builder.append(targetPath[0]).append("((").append("bidRequest.get")
                                .append(capTopField).append("() == null ? ").append(topFieldClass)
                                .append(".builder() : ").append("bidRequest.get").append(capTopField)
                                .append("().toBuilder())\n.").append(targetPath[1]).append("(")
                                .append(midFieldClass).append(".builder().")
                                .append(targetPath[2]).append("(")
                                .append(resolveValue(singleTransformation, bidderData))
                                .append(").build()).build())\n");
                    }
                }
            }
        }
        return builder.append(".build();").toString();
    }

    private String resolveValue(Transformation transformation, BidderData bidderData) {
        final JsonNode staticValue = transformation.getStaticValue();
        if (staticValue != null) {
            return resolveStaticValue(staticValue);
        }

        final String from = transformation.getFrom();
        if (from.contains("impExt")) {
            final String extField = from.replace("impExt.", "");
            final String fieldType = bidderData.getBidderParams().stream()
                    .filter(param -> param.getName().equals(extField))
                    .map(BidderParam::getType)
                    .map(String::toLowerCase)
                    .findFirst().orElse(null);

            final String finalType = fieldType.equals("string") ? "text" : fieldType;
            return "bidRequest.getImp().get(0).getExt().get(\"bidder\").get(\""
                    + extField + "\")." + finalType + "Value()";
        }
        final String[] fromSplit = StringUtils.split(from, ".");
        final StringBuilder builder = new StringBuilder("bidRequest");
        for (String part : fromSplit) {
            builder.append(".").append("get").append(StringUtils.capitalize(part)).append("()");
            if (part.equals("imp")) {
                builder.append(".get(0)");
            }
        }
        return builder.toString();
    }
}

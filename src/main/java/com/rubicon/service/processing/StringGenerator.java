package com.rubicon.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rubicon.model.Transformation;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        resolveFieldBuilderString(method, className, topFieldName, capName);
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
                // possible only for imp
                if (targetPath.length == 1) {
                    addBuilderField(method, singleTransformation, modificationType, modificationType.getName(), null);
                    // both imp and request
                } else if (targetPath.length == 2) {
                    if (isImpModification) {
                        addTopFieldBuilder(method, modificationType.getPath(), fieldClass, fieldName, capField);
                        addBuilderField(method, singleTransformation, modificationType, modificationType.getName(), fieldName);
                    } else {
                        addBuilderField(method, singleTransformation, modificationType, topFieldName, null);
                    }
                    // length == 3 - possible only for request
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

    private static void resolveFieldBuilderString(MethodSpec.Builder method, ClassName className, String fieldName,
                                                  String capName) {
        method.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                className, capName, fieldName, fieldName, fieldName, className);
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
}

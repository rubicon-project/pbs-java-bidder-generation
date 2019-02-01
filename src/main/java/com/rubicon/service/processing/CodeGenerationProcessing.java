package com.rubicon.service.processing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.rubicon.model.BidderData;
import com.rubicon.model.BidderParam;
import com.rubicon.model.Transformation;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CodeGenerationProcessing {

    @Autowired
    private FileCreator fileCreator;

    public void generateBidderJavaFiles(BidderData bidderData) throws IOException {
        final JavaFile extJavaFile = createExtJavaFile(bidderData);
        if (extJavaFile != null) {
            writeExtFile(extJavaFile, bidderData);
        }

        final JavaFile bidderJavaFile = createBidderJavaFile(bidderData);
        writeBidderFile(bidderJavaFile, bidderData);
    }

    private static JavaFile createExtJavaFile(BidderData bidderData) {
        final List<BidderParam> properties = bidderData.getBidderParams();
        if (CollectionUtils.isEmpty(properties)) {
            return null;
        }

        final String bidderName = bidderData.getBidderName();
        final TypeSpec.Builder extensionClassBuilder =
                TypeSpec.classBuilder("ExtImp" + StringUtils.capitalize(bidderName))
                        .addJavadoc("Defines the contract for bidrequest.imp[i].ext." + bidderName + "\n")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(properties.size() > 4 ? Builder.class : AllArgsConstructor.class)
                        .addAnnotation(Value.class);

        for (BidderParam field : properties) {
            final String fieldName = field.getName();
            try {
                final Class<?> forName = Class.forName(qualifiedClassName(field.getType()));
                final FieldSpec fieldSpec = FieldSpec.builder(forName, fieldName)
                        .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                                .addMember("value", "$S", fieldName)
                                .build())
                        .build();
                extensionClassBuilder.addField(fieldSpec);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return JavaFile.builder("org.prebid.server.proto.openrtb.ext.request."
                + bidderName.toLowerCase(), extensionClassBuilder.build())
                .skipJavaLangImports(true)
                .build();
    }

    private static String qualifiedClassName(String inputName) {
        return "java.lang." + inputName;
    }

    private void writeExtFile(JavaFile extFile, BidderData bidderData) throws IOException {
        final String extFilePath = fileCreator.makeBidderFile(bidderData, FileType.EXT);
        extFile.writeTo(Paths.get(extFilePath));
    }

    private static JavaFile createBidderJavaFile(BidderData bidderData) {
        final String bidderName = bidderData.getBidderName();
        final List<BidderParam> bidderParams = bidderData.getBidderParams();
        final String strategy = bidderData.getStrategy();
        final ClassName extClass = CollectionUtils.isNotEmpty(bidderParams)
                ? ClassName.get("org.prebid.server.proto.openrtb.ext.request." + bidderName.toLowerCase(),
                "ExtImp" + StringUtils.capitalize(bidderName))
                : ClassName.get(Void.class);

        MethodSpec bidderConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "endpointUrl")
                .addStatement("super(endpointUrl, RequestCreationStrategy.$L, $T.class)", strategy, extClass)
                .build();

        final ClassName openrtbBidder = ClassName.get("org.prebid.server.bidder", "OpenrtbBidder");
        final TypeSpec.Builder bidderClassBuilder =
                TypeSpec.classBuilder(StringUtils.capitalize(bidderName) + "Bidder")
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(ParameterizedTypeName.get(openrtbBidder, extClass))
                        .addMethod(bidderConstructor);

        final List<Transformation> transformations = bidderData.getTransformations();
        if (CollectionUtils.isNotEmpty(transformations)) {
            modifyImps(bidderClassBuilder, bidderData, extClass);
            modifyRequest(bidderClassBuilder, bidderData, extClass);
        }

        return JavaFile.builder("org.prebid.server.bidder."
                + bidderName.toLowerCase(), bidderClassBuilder.build())
                .skipJavaLangImports(true)
                .build();
    }

    private static void modifyImps(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {

        // filter impressions modifications only, make .imp a root element by removing if from target path string
        final List<Transformation> impTransformations = bidderData.getTransformations().stream()
                .filter(transformation -> transformation.getTarget().contains("imp."))
                .map(transformation -> new Transformation(
                        StringUtils.replace(transformation.getTarget(), "imp.", ""),
                        transformation.getStaticValue(),
                        transformation.getFrom()))
                .collect(Collectors.toList());

        // if there are changes to be done to the impression - create a overridden "modifyImp" method
        if (!impTransformations.isEmpty()) {

            final ClassName impClass = ClassName.get("com.iab.openrtb.request", "Imp");
            final MethodSpec.Builder modifyImps = MethodSpec.methodBuilder("modifyImp")
                    .addModifiers(Modifier.PROTECTED)
                    .returns(impClass)
                    .addAnnotation(Override.class)
                    .addParameter(impClass, "imp")
                    .addParameter(extClass, "impExt")
                    .addStatement("final Imp.ImpBuilder impBuilder = imp.toBuilder()");

            final Map<String, List<Transformation>> fieldToTransformations = impTransformations.stream()
                    .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getTarget(), ".")[0]));

            for (Map.Entry<String, List<Transformation>> field : fieldToTransformations.entrySet()) {
                final List<Transformation> transformations = field.getValue();
                final String fieldName = field.getKey();
                final String capKey = StringUtils.capitalize(fieldName);
                final ClassName keyClass = ClassName.get("com.iab.openrtb.request", capKey);

                if (transformations.size() > 1) {
                    modifyImps.addStatement("final $T $L = imp.get$L()", keyClass, fieldName, capKey);
                    modifyImps.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                            keyClass, capKey, fieldName, fieldName, fieldName, keyClass);
                    modifyImps.addCode("$LBuilder", fieldName);

                    for (Transformation transformation : transformations) {
                        modifyImps.addCode(addTransformation(transformation, Type.IMP));
                    }
                    modifyImps.addCode(";\n");
                    modifyImps.addStatement("impBuilder.$L($LBuilder.build())", fieldName, fieldName);
                } else {
                    final Transformation singleTransformation = transformations.get(0);
                    final String target = singleTransformation.getTarget();
                    final String[] targetPath = StringUtils.split(target, ".");
                    if (targetPath.length == 1) {
                        modifyImps.addStatement("impBuilder$L", addTransformation(singleTransformation, Type.IMP));
                    } else {
                        modifyImps.addStatement("final $T $L = imp.get$L()", keyClass, fieldName, capKey);
                        modifyImps.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                                keyClass, capKey, fieldName, fieldName, fieldName, keyClass);
                        modifyImps.addStatement("impBuilder.$L($LBuilder$L.build())",
                                fieldName, fieldName, addTransformation(singleTransformation, Type.IMP));
                    }
                }
            }
            modifyImps.addStatement("return impBuilder.build()");
            classBuilder.addMethod(modifyImps.build());
        }
    }

    private static String addTransformation(Transformation transformation, Type type) {
        final String target = transformation.getTarget();
        final JsonNode staticValue = transformation.getStaticValue();
        final String value = staticValue != null ? resolveStaticValue(staticValue) : transformation.getFrom();
        final String[] targetPath = StringUtils.split(target, ".");
        if (type.equals(Type.IMP) && targetPath.length > 2) {
            throw new IllegalArgumentException("not supported transformation");
        }
        return staticValue != null
                ? createStaticTransformationString(value, targetPath)
                : createDynamicTransformationString(value, targetPath, type);
    }

    private static String createStaticTransformationString(String value, String[] targetPath) {
        if (targetPath.length == 3) {
            return String.format(".%s(%s)", targetPath[2], value);
        }
        if (targetPath.length == 2) {
            return String.format(".%s(%s)", targetPath[1], value);
        }
        return String.format(".%s(%s)", targetPath[0], value);
    }

    private static String createDynamicTransformationString(String from, String[] targetPath, Type type) {
        if (type.equals(Type.IMP)) {
            return from.contains("impExt")
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

    private static String resolveFieldString(String field, String fieldPrefix, String[] targetPath) {
        final String[] fieldPath = StringUtils.split(field, ".");
        if (targetPath.length == 3) {
            if (fieldPath.length == 3) {
                return String.format(".%s(%s.get%s().get%s().get%s())", targetPath[2], fieldPrefix,
                        StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]),
                        StringUtils.capitalize(fieldPath[2]));
            }
            if (fieldPath.length == 2) {
                return String.format(".%s(%s.get%s().get%s())", targetPath[2], fieldPrefix,
                        StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]));
            }
            return String.format(".%s(%s.get%s())", targetPath[2], fieldPrefix,
                    StringUtils.capitalize(fieldPath[0]));
        }
        if (targetPath.length == 2) {
            if (fieldPath.length == 3) {
                return String.format(".%s(%s.get%s().get%s().get%s())", targetPath[1], fieldPrefix,
                        StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]),
                        StringUtils.capitalize(fieldPath[2]));
            }
            if (fieldPath.length == 2) {
                return String.format(".%s(%s.get%s().get%s())", targetPath[1], fieldPrefix,
                        StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]));
            }
            return String.format(".%s(%s.get%s())", targetPath[1], fieldPrefix, StringUtils.capitalize(field));
        }
        if (fieldPath.length == 3) {
            return String.format(".%s(%s.get%s().get%s().get%s())", targetPath[0], fieldPrefix,
                    StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]),
                    StringUtils.capitalize(fieldPath[2]));
        }
        if (fieldPath.length == 2) {
            return String.format(".%s(%s.get%s().get%s())", targetPath[0], fieldPrefix,
                    StringUtils.capitalize(fieldPath[0]), StringUtils.capitalize(fieldPath[1]));
        }
        return String.format(".%s(%s.get%s())", targetPath[0], fieldPrefix, StringUtils.capitalize(field));
    }

    // bidrequest.site/app.content - no .toBuilder() method - not available for modifications!
    private static void modifyRequest(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {

        // filter out impression transformations, leave request level only
        final List<Transformation> requestTransformations = bidderData.getTransformations().stream()
                .filter(transformation -> !transformation.getTarget().contains("imp."))
                .collect(Collectors.toList());

        if (!requestTransformations.isEmpty()) {
            final ClassName bidRequest = ClassName.get("com.iab.openrtb.request", "BidRequest");
            final ClassName impWithExt = ClassName.get("org.prebid.server.bidder.model", "ImpWithExt");
            final MethodSpec.Builder modifyRequest = MethodSpec.methodBuilder("modifyRequest")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .addParameter(bidRequest, "bidRequest")
                    .addParameter(bidRequest.nestedClass("BidRequestBuilder"), "requestBuilder")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(List.class),
                            ParameterizedTypeName.get(impWithExt, extClass)), "impsWithExts");

            final Map<String, List<Transformation>> fieldToTransformations = requestTransformations.stream()
                    .collect(Collectors.groupingBy(transformation -> StringUtils.split(transformation.getTarget(), ".")[0]));

            fieldToTransformations.entrySet().forEach(System.out::println);

            for (Map.Entry<String, List<Transformation>> field : fieldToTransformations.entrySet()) {
                final List<Transformation> transformations = field.getValue();
                final String topFieldName = field.getKey();
                final String capKey = StringUtils.capitalize(topFieldName);
                final ClassName keyClass = ClassName.get("com.iab.openrtb.request", capKey);

                if (transformations.size() > 1) {
                    modifyRequest.addStatement("final $T $L = bidRequest.get$L()", keyClass, topFieldName, capKey);
                    modifyRequest.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                            keyClass, capKey, topFieldName, topFieldName, topFieldName, keyClass);

                    final Map<String, List<Transformation>> midFieldToTrans = transformations.stream()
                            .collect(Collectors.groupingBy(tr -> StringUtils.split(tr.getTarget(), ".")[1]));

                    for (Map.Entry<String, List<Transformation>> midField : midFieldToTrans.entrySet()) {
                        final List<Transformation> midTransformations = midField.getValue();
                        final String midFieldName = midField.getKey();
                        final String capMid = StringUtils.capitalize(midFieldName);
                        final ClassName midFieldClass = ClassName.get("com.iab.openrtb.request", capMid);

                        if (midTransformations.size() > 1) {
                            modifyRequest.addStatement("final $T $L = $L != null ? $L.get$L() : null",
                                    midFieldClass, midFieldName, topFieldName, topFieldName, capMid);
                            modifyRequest.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                                    midFieldClass, capMid, midFieldName, midFieldName, midFieldName, midFieldClass);

                            for (Transformation tr : midTransformations) {
                                modifyRequest.addStatement("$LBuilder$L", midFieldName,
                                        addTransformation(tr, Type.REQUEST));
                            }
                            modifyRequest.addStatement("$LBuilder.$L($LBuilder.build())",
                                    topFieldName, midFieldName, midFieldName);
                        } else {
                            final Transformation singleTransformation = midTransformations.get(0);
                            final String target = singleTransformation.getTarget();
                            final String[] targetPath = StringUtils.split(target, ".");
                            if (targetPath.length == 2) {
                                modifyRequest.addStatement("$LBuilder$L", topFieldName,
                                        addTransformation(singleTransformation, Type.REQUEST));
                            } else {
                                modifyRequest.addStatement("final $T $L = $L != null ? $L.get$L() : null",
                                        midFieldClass, midFieldName, topFieldName, topFieldName, capMid);
                                modifyRequest.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                                        midFieldClass, capMid, midFieldName, midFieldName, midFieldName, midFieldClass);
                                modifyRequest.addStatement("$LBuilder.$L($LBuilder$L.build())",
                                        topFieldName, midFieldName, midFieldName,
                                        addTransformation(singleTransformation, Type.REQUEST));
                            }
                        }
                    }
                    modifyRequest.addStatement("requestBuilder.$L($LBuilder.build())", topFieldName, topFieldName);
                } else {
                    final Transformation singleTransformation = transformations.get(0);
                    final String target = singleTransformation.getTarget();
                    final String[] targetPath = StringUtils.split(target, ".");
                    if (targetPath.length == 1) {
                        modifyRequest.addStatement("requestBuilder$L", addTransformation(singleTransformation, Type.REQUEST));
                    } else {
                        modifyRequest.addStatement("final $T $L = bidRequest.get$L()", keyClass, topFieldName, capKey);
                        modifyRequest.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                                keyClass, capKey, topFieldName, topFieldName, topFieldName, keyClass);
                        if (targetPath.length == 2) {
                            modifyRequest.addStatement("requestBuilder.$L($LBuilder$L.build())",
                                    topFieldName, topFieldName, addTransformation(singleTransformation, Type.REQUEST));
                        } else {
                            final String midField = targetPath[1];
                            final String capMiddleName = StringUtils.capitalize(midField);
                            final ClassName midClass = ClassName.get("com.iab.openrtb.request", capMiddleName);
                            modifyRequest.addStatement("final $T $L = $L != null ? $L.get$L() : null",
                                    midClass, midField, topFieldName, topFieldName, capMiddleName);
                            modifyRequest.addStatement("final $T.$LBuilder $LBuilder = $L != null ? $L.toBuilder() : $T.builder()",
                                    midClass, capMiddleName, midField, midField, midField, midClass);
                            modifyRequest.addStatement("requestBuilder.$L($LBuilder.$L($LBuilder$L.build()).build())",
                                    topFieldName, topFieldName, midField, midField,
                                    addTransformation(singleTransformation, Type.REQUEST));
                        }
                    }
                }
            }
            classBuilder.addMethod(modifyRequest.build());
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

    private void writeBidderFile(JavaFile bidderFile, BidderData bidderData) throws IOException {
        final String bidderFilePath = fileCreator.makeBidderFile(bidderData, FileType.BIDDER);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }

    private enum Type {
        IMP,
        REQUEST
    }
}

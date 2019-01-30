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
        final String extFilePath = fileCreator.makeBidderFile(bidderData, FileCreator.FileType.EXT);
        extFile.writeTo(Paths.get(extFilePath));
    }

    private static JavaFile createBidderJavaFile(BidderData bidderData) {
        final String bidderName = bidderData.getBidderName();
        final List<BidderParam> bidderParams = bidderData.getBidderParams();
        final ClassName extClass = CollectionUtils.isNotEmpty(bidderParams)
                ? ClassName.get("org.prebid.server.proto.openrtb.ext.request." + bidderName.toLowerCase(),
                "ExtImp" + StringUtils.capitalize(bidderName))
                : ClassName.get(Void.class);

        MethodSpec bidderConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "endpointUrl")
                .addStatement("super($N, $N, $T.class)", "endpointUrl",
                        "RequestCreationStrategy.SINGLE_REQUEST", extClass)
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
        final List<Transformation> impModifications = bidderData.getTransformations().stream()
                .filter(transformation -> transformation.getTarget().contains("imp"))
                .map(transformation -> new Transformation(
                        StringUtils.replace(transformation.getTarget(), "imp.", ""),
                        transformation.getStaticValue(), transformation.getFrom()))
                .collect(Collectors.toList());

        final List<Transformation> staticChanges = impModifications.stream()
                .filter(transformation -> transformation.getStaticValue() != null)
                .collect(Collectors.toList());

        final List<Transformation> extChanges = impModifications.stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getFrom()))
                .peek(transformation -> transformation.setFrom(StringUtils.replace(transformation.getFrom(), "impExt.", "")))
                .collect(Collectors.toList());

        if (!staticChanges.isEmpty() || !extChanges.isEmpty()) {
            final ClassName impClass = ClassName.get("com.iab.openrtb.request", "Imp");
            final MethodSpec.Builder modifyImps = MethodSpec.methodBuilder("modifyImp")
                    .addModifiers(Modifier.PROTECTED)
                    .returns(impClass)
                    .addAnnotation(Override.class)
                    .addParameter(impClass, "imp")
                    .addParameter(extClass, "impExt")
                    .addCode("return imp.toBuilder()\n");

            if (!staticChanges.isEmpty()) {
                for (Transformation trans : staticChanges) {
                    final String target = trans.getTarget();
                    final String valueString = resolveStaticValue(trans.getStaticValue());
                    final String[] split = StringUtils.split(target, ".");
                    if (split.length == 2) {
                        modifyImps.addCode("." + split[0] + "(imp.get" + StringUtils.capitalize(split[0])
                                + "().toBuilder()." + split[1] + "(" + valueString + ").build())\n");
                    } else {
                        modifyImps.addCode("." + target + "(" + valueString + ")\n");
                    }
                }
            }
            if (!extChanges.isEmpty()) {
                for (Transformation trans : extChanges) {
                    final String target = trans.getTarget();
                    final String value = trans.getFrom();
                    final String[] split = StringUtils.split(target, ".");
                    if (split.length == 2) {
                        modifyImps.addCode("." + split[0] + "(imp.get" + StringUtils.capitalize(split[0])
                                + "().toBuilder()." + split[1] + "(impExt.get" + StringUtils.capitalize(value)
                                + "()).build())\n");
                    } else {
                        modifyImps.addCode("." + target + "(impExt.get" + StringUtils.capitalize(value) + "())\n");
                    }
                }
            }
            modifyImps.addStatement(".build()");
            classBuilder.addMethod(modifyImps.build());
        }
    }

    private static void modifyRequest(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {
        final List<Transformation> requestModifications = bidderData.getTransformations().stream()
                .filter(transformation -> !transformation.getTarget().contains("imp"))
                .collect(Collectors.toList());

        final List<Transformation> staticChanges = requestModifications.stream()
                .filter(transformation -> transformation.getStaticValue() != null)
                .collect(Collectors.toList());

        if (!staticChanges.isEmpty()) {
            final ClassName bidRequest = ClassName.get("com.iab.openrtb.request", "BidRequest");
            final ClassName impWithExt = ClassName.get("org.prebid.server.bidder.model", "ImpWithExt");
            final MethodSpec.Builder modifyRequest = MethodSpec.methodBuilder("modifyRequest")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .addParameter(bidRequest, "bidRequest")
                    .addParameter(bidRequest.nestedClass("BidRequestBuilder"), "requestBuilder")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(List.class),
                            ParameterizedTypeName.get(impWithExt, extClass)), "impsWithExts")
                    .addCode("requestBuilder");

            for (Transformation trans : staticChanges) {
                final String target = trans.getTarget();
                final String valueString = resolveStaticValue(trans.getStaticValue());
                final String[] split = StringUtils.split(target, ".");
                if (split.length == 2) {
                    modifyRequest.addCode("\n." + split[0] + "(bidRequest.get" + StringUtils.capitalize(split[0])
                            + "().toBuilder()." + split[1] + "(" + valueString + ").build())\n");
                } else {
                    modifyRequest.addCode("\n." + target + "(" + valueString + ")");
                }
            }

            modifyRequest.addCode(";\n");
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
        final String bidderFilePath = fileCreator.makeBidderFile(bidderData, FileCreator.FileType.BIDDER);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }
}

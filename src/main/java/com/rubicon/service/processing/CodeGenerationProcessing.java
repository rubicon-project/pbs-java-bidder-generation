package com.rubicon.service.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CodeGenerationProcessing {

    private static final String IMP_SOURCE = "imp.";
    private static final String REQUEST_SOURCE = "bidRequest.";

    private static String bidderName;
    private static String bidderImpExtName;
    private static String bidderPackage;
    private static String bidderFile;

    private final FileCreator fileCreator;
    private final StringGenerator stringGenerator;

    public CodeGenerationProcessing(FileCreator fileCreator, StringGenerator stringGenerator) {
        this.fileCreator = fileCreator;
        this.stringGenerator = stringGenerator;
    }

    public void generateBidderJavaFiles(BidderData bidderData) throws IOException {
        setStaticFields(bidderData);

        final JavaFile extJavaFile = createExtJavaFile(bidderData);
        if (extJavaFile != null) {
            writeGeneratedFile(extJavaFile, bidderData, FileType.EXT);
        }

        final JavaFile bidderJavaFile = createBidderJavaFile(bidderData);
        writeGeneratedFile(bidderJavaFile, bidderData, FileType.BIDDER);

        if (CollectionUtils.isNotEmpty(bidderData.getTransformations())) {
            final JavaFile bidderTestJavaFile = createBidderTestJavaFile(bidderData);
            writeGeneratedFile(bidderTestJavaFile, bidderData, FileType.BIDDER_TEST);
        }
    }

    private static void setStaticFields(BidderData bidderData) {
        bidderName = bidderData.getBidderName();
        bidderPackage = bidderName.toLowerCase();
        final String capitalizedName = StringUtils.capitalize(bidderName);
        bidderImpExtName = "ExtImp" + capitalizedName;
        bidderFile = capitalizedName + "Bidder";
    }

    private static JavaFile createExtJavaFile(BidderData bidderData) {
        final List<BidderParam> properties = bidderData.getBidderParams();
        if (CollectionUtils.isEmpty(properties)) {
            return null;
        }

        final TypeSpec.Builder extensionClassBuilder =
                TypeSpec.classBuilder(bidderImpExtName)
                        .addJavadoc("Defines the contract for $Limp[i].ext.$L\n", REQUEST_SOURCE, bidderName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(properties.size() > 4
                                ? AnnotationSpec.builder(Builder.class).build()
                                : AnnotationSpec.builder(AllArgsConstructor.class).addMember(
                                "staticName", "$S", "of").build())
                        .addAnnotation(Value.class);

        for (BidderParam field : properties) {
            final String fieldName = field.getName();
            try {
                final Class<?> forName = Class.forName(qualifiedClassName(field.getType()));
                final FieldSpec fieldSpec = FieldSpec.builder(forName, fieldName).build();
                extensionClassBuilder.addField(fieldSpec);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return JavaFile.builder("org.prebid.server.proto.openrtb.ext.request."
                + bidderPackage, extensionClassBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private static String qualifiedClassName(String inputName) {
        return "java.lang." + inputName;
    }

    private JavaFile createBidderJavaFile(BidderData bidderData) {
        final List<BidderParam> bidderParams = bidderData.getBidderParams();
        final ClassName extClass = CollectionUtils.isNotEmpty(bidderParams)
                ? ClassName.get("org.prebid.server.proto.openrtb.ext.request."
                + bidderPackage, bidderImpExtName)
                : ClassName.get(Void.class);

        final String strategy = bidderData.getStrategy();
        final MethodSpec bidderConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "endpointUrl")
                .addStatement("super(endpointUrl, RequestCreationStrategy.$L, $T.class)", strategy, extClass)
                .build();

        final ClassName openrtbBidder = ClassName.get("org.prebid.server.bidder", "OpenrtbBidder");
        final TypeSpec.Builder bidderClassBuilder =
                TypeSpec.classBuilder(bidderFile)
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(ParameterizedTypeName.get(openrtbBidder, extClass))
                        .addMethod(bidderConstructor);

        final List<Transformation> transformations = bidderData.getTransformations();
        if (CollectionUtils.isNotEmpty(transformations)) {
            modifyImps(bidderClassBuilder, bidderData, extClass);
            modifyRequest(bidderClassBuilder, bidderData, extClass);
        }

        return JavaFile.builder("org.prebid.server.bidder." + bidderPackage, bidderClassBuilder.build())
                .skipJavaLangImports(true)
                .indent("    ")
                .build();
    }

    private void modifyImps(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {

        // filter impressions modifications only, make .imp a root element by removing if from target path string
        final List<Transformation> impTransformations = bidderData.getTransformations().stream()
                .filter(transformation -> transformation.getTarget().contains(IMP_SOURCE))
                .map(transformation -> new Transformation(
                        StringUtils.replace(transformation.getTarget(), IMP_SOURCE, ""),
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

            stringGenerator.resolveMethodBody(modifyImps, fieldToTransformations, ModificationType.IMP, null);

            classBuilder.addMethod(modifyImps.build());
        }
    }

    // bidrequest.site/app.content - no .toBuilder() method - not available for modifications!
    private void modifyRequest(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {

        // filter out impression transformations, leave request level only
        final List<Transformation> requestTransformations = bidderData.getTransformations().stream()
                .filter(transformation -> !transformation.getTarget().contains(IMP_SOURCE))
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

            for (Map.Entry<String, List<Transformation>> field : fieldToTransformations.entrySet()) {
                final List<Transformation> transformations = field.getValue();
                final String topFieldName = field.getKey();
                final String capKey = StringUtils.capitalize(topFieldName);
                final ClassName keyClass = ClassName.get("com.iab.openrtb.request", capKey);

                if (transformations.size() > 1) {
                    stringGenerator.addTopFieldBuilder(modifyRequest, REQUEST_SOURCE, keyClass, topFieldName,
                            capKey);

                    final Map<String, List<Transformation>> midFieldToTrans = transformations.stream()
                            .collect(Collectors.groupingBy(tr -> StringUtils.split(tr.getTarget(), ".")[1]));

                    stringGenerator.resolveMethodBody(modifyRequest, midFieldToTrans, ModificationType.REQUEST,
                            topFieldName);
                } else {
                    final Transformation singleTransformation = transformations.get(0);
                    final String target = singleTransformation.getTarget();
                    final String[] targetPath = StringUtils.split(target, ".");
                    if (targetPath.length == 1) {
                        stringGenerator.addBuilderField(modifyRequest, singleTransformation,
                                ModificationType.REQUEST, ModificationType.REQUEST.getName(), null);
                    } else {
                        stringGenerator.addTopFieldBuilder(modifyRequest, REQUEST_SOURCE, keyClass, topFieldName,
                                capKey);
                        final String midField = targetPath[1];
                        final String capMiddleName = StringUtils.capitalize(midField);
                        final ClassName midClass = ClassName.get("com.iab.openrtb.request", capMiddleName);
                        if (targetPath.length == 2) {
                            stringGenerator.addBuilderField(modifyRequest, singleTransformation,
                                    ModificationType.REQUEST, topFieldName, null);
                            modifyRequest.addStatement("requestBuilder.$L($LBuilder.build())",
                                    topFieldName, topFieldName);
                        } else {
                            stringGenerator.addNestedFieldBuilder(modifyRequest, midClass, topFieldName,
                                    midField, capMiddleName);
                            modifyRequest.addStatement("requestBuilder.$L($LBuilder.$L($LBuilder$L.build()).build())",
                                    topFieldName, topFieldName, midField, midField,
                                    stringGenerator.addTransformationString(singleTransformation, ModificationType.REQUEST));
                        }
                    }
                }
            }
            classBuilder.addMethod(modifyRequest.build());
        }
    }

    private JavaFile createBidderTestJavaFile(BidderData bidderData) {
        final FieldSpec endpointField = FieldSpec.builder(String.class, "ENDPOINT_URL")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", "https://test.endpoint.com")
                .build();

        final ClassName bidderClass = ClassName.get("org.prebid.server.bidder." + bidderPackage, bidderFile);
        final FieldSpec bidderInstance = FieldSpec.builder(bidderClass, bidderName + "Bidder", Modifier.PRIVATE)
                .build();

        final MethodSpec setUpMethod = MethodSpec.methodBuilder("setUp")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Before.class)
                .addStatement("$LBidder = new $L($N)", bidderName, bidderFile, endpointField)
                .build();

        final MethodSpec endpointValidationTest = createTestMethod("creationShouldFailOnInvalidEndpointUrl",
                method -> method
                        .addStatement(" assertThatIllegalArgumentException().isThrownBy(() -> new $L(\"invalid_url\"))",
                                bidderFile));

        final ClassName extPrebid = ClassName.get("org.prebid.server.proto.openrtb.ext", "ExtPrebid");
        final MethodSpec extCannotBeParsedTest = createTestMethod("makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed",
                method -> method
                        .addCode("// given\nfinal BidRequest bidRequest = BidRequest.builder()\n")
                        .addCode(".imp(singletonList(Imp.builder()\n")
                        .addCode(".ext(mapper.valueToTree($T.of(null, mapper.createArrayNode())))\n", extPrebid)
                        .addCode(".build()))\n.build();\n\n")
                        .addCode("// when\nfinal Result<List<HttpRequest<BidRequest>>> result = $N.makeHttpRequests(bidRequest);\n\n",
                                bidderInstance)
                        .addCode("// then\nassertThat(result.getErrors()).hasSize(1);\n")
                        .addCode("assertThat(result.getErrors().get(0).getMessage()).startsWith(\"Cannot deserialize instance\");\n")
                        .addStatement("assertThat(result.getValue()).isEmpty()"));

        // insert methods here

        final ClassName result = ClassName.get("org.prebid.server.bidder.model", "Result");
        final ClassName bidderBid = ClassName.get("org.prebid.server.bidder.model", "BidderBid");
        final ClassName bidderError = ClassName.get("org.prebid.server.bidder.model", "BidderError");
        final MethodSpec responseBodyTest = createTestMethod("makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed",
                method -> method
                        .addCode("// given\nfinal HttpCall<BidRequest> httpCall = givenHttpCall(null, \"invalid\");\n\n")
                        .addCode("// when\nfinal $T<$T<$T>> result = $N.makeBids(httpCall, null);\n\n",
                                result, List.class, bidderBid, bidderInstance)
                        .addCode("// then\nassertThat(result.getErrors()).hasSize(1);\n")
                        .addCode("assertThat(result.getErrors().get(0).getMessage()).startsWith(\"Failed to decode: Unrecognized token\");\n")
                        .addCode("assertThat(result.getErrors().get(0).getType()).isEqualTo($T.Type.bad_server_response);\n",
                                bidderError)
                        .addStatement("assertThat(result.getValue()).isEmpty()"));

        final MethodSpec bidResponseNullTest = createTestMethod("makeBidsShouldReturnEmptyListIfBidResponseIsNull",
                method -> method.addException(JsonProcessingException.class)
                        .addCode("// given\nfinal HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));\n\n")
                        .addCode("// when\nfinal $T<List<BidderBid>> result = $N.makeBids(httpCall, null);\n\n",
                                result, bidderInstance)
                        .addCode("// then\nassertThat(result.getErrors()).isEmpty();\n")
                        .addStatement("assertThat(result.getValue()).isEmpty()"));

        final MethodSpec seatBidNullTest = createTestMethod("makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull",
                method -> method.addException(JsonProcessingException.class)
                        .addCode("// given\nfinal HttpCall<BidRequest> httpCall = givenHttpCall(null,\n")
                        .addCode("mapper.writeValueAsString(BidResponse.builder().build()));\n\n")
                        .addCode("// when\nfinal $T<List<BidderBid>> result = $N.makeBids(httpCall, null);\n\n",
                                result, bidderInstance)
                        .addCode("// then\nassertThat(result.getErrors()).isEmpty();\n")
                        .addStatement("assertThat(result.getValue()).isEmpty()"));


        final MethodSpec bannerBidTest = createTestMethod("makeBidsShouldReturnBannerBid",
                method -> method.addException(JsonProcessingException.class)
                        .addCode("// given\nfinal HttpCall<BidRequest> httpCall = givenHttpCall(\n")
                        .addCode("BidRequest.builder().imp(singletonList(Imp.builder().id(\"123\").build())).build(),\n")
                        .addCode("mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid(\"123\"))));\n\n")
                        .addCode("// when\nfinal $T<List<BidderBid>> result = $N.makeBids(httpCall, null);\n\n",
                                result, bidderInstance)
                        .addCode("// then\nassertThat(result.getErrors()).isEmpty();\n")
                        .addStatement("assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()"
                                + ".impid(\"123\").build(), banner, \"USD\"))"));

        final MethodSpec targetingTest = createTestMethod("extractTargetingShouldReturnEmptyMap",
                method -> method.addStatement("assertThat($N.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap())",
                        bidderInstance));

        final ClassName vertxTest = ClassName.get("org.prebid.server", "VertxTest");
        final TypeSpec.Builder testClassBuilder =
                TypeSpec.classBuilder(bidderFile + "Test")
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(vertxTest)
                        .addField(endpointField)
                        .addField(bidderInstance)
                        .addMethod(setUpMethod)
                        .addMethod(endpointValidationTest)
                        .addMethod(extCannotBeParsedTest);

        resolveAndAddBidderTransformationsTest(testClassBuilder, bidderData, bidderInstance);

        testClassBuilder
                .addMethod(responseBodyTest)
                .addMethod(bidResponseNullTest)
                .addMethod(seatBidNullTest)
                .addMethod(bannerBidTest)
                .addMethod(targetingTest);

        addUtilityMethods(testClassBuilder, bidderData);

        return JavaFile.builder("org.prebid.server.bidder."
                + bidderPackage, testClassBuilder.build())
                .skipJavaLangImports(true)
                .addStaticImport(Collections.class, "emptyMap", "singletonList")
                .addStaticImport(Assertions.class, "assertThat", "assertThatIllegalArgumentException")
                .addStaticImport(ClassName.get("org.prebid.server.proto.openrtb.ext.response", "BidType"), "banner")
                .addStaticImport(Function.class, "identity")
                .indent("    ")
                .build();
    }

    private void resolveAndAddBidderTransformationsTest(TypeSpec.Builder builder, BidderData bidderData,
                                                        FieldSpec bidderInstance) {
        final MethodSpec transformationsTest = createTestMethod("makeHttpRequestsShouldReturnExpectedRequest",
                method -> method
                        .addCode("// given\n")
                        .addCode(stringGenerator.resolveGivenBidRequestString(bidderData))
                        .addCode("\n\n")
                        .addCode("// when\nfinal Result<List<HttpRequest<BidRequest>>> result = "
                                + "$NBidder.makeHttpRequests(bidRequest);\n\n", bidderInstance)
                        .addCode("// then\n")
                        .addCode(stringGenerator.resolveExpectedBidRequestString(bidderData))
                        .addCode("\n")
                        .addCode("assertThat(result.getErrors()).isEmpty();\n")
                        .addCode("assertThat(result.getValue()).hasSize(1)\n")
                        .addCode(".extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))\n")
                        .addStatement(".containsOnly(expectedRequest)"));

        builder.addMethod(transformationsTest);
    }

    private void addUtilityMethods(TypeSpec.Builder builder, BidderData bidderData) {
        final ClassName bidRequest = ClassName.get("com.iab.openrtb.request", "BidRequest");
        final ClassName bidRequestBuilder = bidRequest.nestedClass("BidRequestBuilder");
        final ClassName imp = ClassName.get("com.iab.openrtb.request", "Imp");
        final ClassName impBuilder = imp.nestedClass("ImpBuilder");
        final MethodSpec givenBidRequest = MethodSpec.methodBuilder("givenBidRequest")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(bidRequest)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class),
                        impBuilder, impBuilder), "impCustomizer")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class),
                        bidRequestBuilder, bidRequestBuilder), "requestCustomizer")
                .addCode("return requestCustomizer.apply(BidRequest.builder()\n")
                .addCode(".imp(singletonList(givenImp(impCustomizer))))\n.build();")
                .build();

        final MethodSpec givenBidRequestImp = MethodSpec.methodBuilder("givenBidRequest")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(bidRequest)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class),
                        impBuilder, impBuilder), "impCustomizer")
                .addStatement("return givenBidRequest(impCustomizer, identity())")
                .build();

        final ClassName imExtClass = ClassName.get("org.prebid.server.proto.openrtb.ext.request."
                + bidderPackage, bidderImpExtName);
        final ClassName banner = ClassName.get("com.iab.openrtb.request", "Banner");
        final ClassName video = ClassName.get("com.iab.openrtb.request", "Video");
        final MethodSpec givenImp = MethodSpec.methodBuilder("givenImp")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(imp)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class),
                        impBuilder, impBuilder), "impCustomizer")
                .addCode("return impCustomizer.apply(Imp.builder()\n")
                .addCode(".id(\"123\"))\n")
                .addCode(".banner($T.builder().build())\n", banner)
                .addCode(".video($T.builder().build())\n", video)
                .addCode(stringGenerator.resolveExt(bidderData), imExtClass)
                .addStatement(".build()")
                .build();

        final ClassName bidBuilder = ClassName.get("com.iab.openrtb.response", "Bid", "BidBuilder");
        final ClassName seatBid = ClassName.get("com.iab.openrtb.response", "SeatBid");
        final MethodSpec givenBidResponse = MethodSpec.methodBuilder("givenBidResponse")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class), bidBuilder, bidBuilder), "bidCustomizer")
                .returns(ClassName.get("com.iab.openrtb.response", "BidResponse"))
                .addCode("return BidResponse.builder()\n")
                .addCode(".seatbid(singletonList($T.builder()\n", seatBid)
                .addCode(".bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))\n")
                .addCode(".build()))\n")
                .addStatement(".build()")
                .build();


        final ClassName httpRequest = ClassName.get("org.prebid.server.bidder.model", "HttpRequest");
        final ClassName httpResponse = ClassName.get("org.prebid.server.bidder.model", "HttpResponse");
        final MethodSpec givenHttpCall = MethodSpec.methodBuilder("givenHttpCall")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(bidRequest, "bidRequest")
                .addParameter(String.class, "body")
                .returns(ParameterizedTypeName.get(
                        ClassName.get("org.prebid.server.bidder.model", "HttpCall"), bidRequest))
                .addCode("return HttpCall.success($T.<BidRequest>builder().payload(bidRequest).build(),\n", httpRequest)
                .addStatement("$T.of(200, null, body), null)", httpResponse)
                .build();

        builder.addMethod(givenBidRequest)
                .addMethod(givenBidRequestImp)
                .addMethod(givenImp)
                .addMethod(givenBidResponse)
                .addMethod(givenHttpCall);
    }

    private void writeGeneratedFile(JavaFile bidderFile, BidderData bidderData, FileType fileType) throws IOException {
        final String bidderFilePath = fileCreator.makeBidderFile(bidderData, fileType);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }

    private static MethodSpec createTestMethod(String methodName,
                                               Function<MethodSpec.Builder, MethodSpec.Builder> customizer) {
        return customizer.apply(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class))
                .build();
    }
}

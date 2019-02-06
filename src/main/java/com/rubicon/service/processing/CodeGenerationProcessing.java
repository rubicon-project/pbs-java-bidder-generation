package com.rubicon.service.processing;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CodeGenerationProcessing {

    private static final String IMP_SOURCE = "imp.";
    private static final String REQUEST_SOURCE = "bidRequest.";

    private final FileCreator fileCreator;
    private final StringGenerator stringGenerator;

    public CodeGenerationProcessing(FileCreator fileCreator, StringGenerator stringGenerator) {
        this.fileCreator = fileCreator;
        this.stringGenerator = stringGenerator;
    }

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

    private JavaFile createBidderJavaFile(BidderData bidderData) {
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
                        if (targetPath.length == 2) {
                            stringGenerator.addBuilderField(modifyRequest, singleTransformation,
                                    ModificationType.REQUEST, topFieldName, null);
                        } else {
                            final String midField = targetPath[1];
                            final String capMiddleName = StringUtils.capitalize(midField);
                            final ClassName midClass = ClassName.get("com.iab.openrtb.request", capMiddleName);
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

    private void writeBidderFile(JavaFile bidderFile, BidderData bidderData) throws IOException {
        final String bidderFilePath = fileCreator.makeBidderFile(bidderData, FileType.BIDDER);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }
}

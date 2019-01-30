package com.rubicon.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.rubicon.model.BidderData;
import com.rubicon.model.BidderParam;
import com.rubicon.model.PropertiesData;
import com.rubicon.model.Transformation;
import com.rubicon.model.UsersyncerData;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TemplateProcessing {

    private static final String TEMPLATES_DIRECTORY = "src/main/resources/templates";
    private static final String USERSYNCER_TEMPLATE = "usersyncer.ftl";
    private static final String PROPERTIES_TEMPLATE = "properties.ftl";
    private static final String BIDDER_CONFIG_TEMPLATE = "configuration.ftl";
    private static final String SCHEMA_TEMPLATE = "schema.ftl";
    private static final String USERSYNC_TEST_TEMPLATE = "usersyncer_test.ftl";
    private static final String SIMPLE_BIDDER_TEST_TEMPLATE = "bidder_test.ftl";

    public void createBidderFiles(BidderData bidderData) throws IOException, TemplateException {

        createPropertiesYamlFile(bidderData);
        createUsersyncerJavaFile(bidderData);
        createUsersyncerTestFile(bidderData);
        createBidderSchemaJsonFile(bidderData);
        createBidderConfigurationJavaFile(bidderData);

        final JavaFile extJavaFile = createExtJavaFile(bidderData);
        if (extJavaFile != null) {
            writeExtFile(extJavaFile, bidderData);
        }

        final JavaFile bidderJavaFile = createBidderJavaFile(bidderData);
        writeBidderFile(bidderJavaFile, bidderData);

        if (extJavaFile == null && CollectionUtils.isEmpty(bidderData.getTransformations())) {
            createSimpleBidderTestFile(bidderData);
        }
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

    private static void writeExtFile(JavaFile extFile, BidderData bidderData) throws IOException {
        final String extFilePath = makeBidderFile(bidderData, FileType.EXT);
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

    private static void writeBidderFile(JavaFile bidderFile, BidderData bidderData) throws IOException {
        final String bidderFilePath = makeBidderFile(bidderData, FileType.BIDDER);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }

    private static void createPropertiesYamlFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final PropertiesData propertiesData = BidderDataUtil.preparePropertiesData(bidderData);
        final Template propertiesTemplate = cfg.getTemplate(PROPERTIES_TEMPLATE);
        final String propertiesFile = makeBidderFile(bidderData, FileType.PROPERTIES);
        FileWriter writer = new FileWriter(propertiesFile);
        propertiesTemplate.process(propertiesData, writer);
        writer.close();
    }

    private static void createUsersyncerJavaFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final UsersyncerData usersyncerData = BidderDataUtil.getUsersyncerData(bidderData);
        final Template usersyncerTemplate = cfg.getTemplate(USERSYNCER_TEMPLATE);
        final String usersyncerFile = makeBidderFile(bidderData, FileType.USERSYNCER);
        FileWriter writer = new FileWriter(usersyncerFile);
        usersyncerTemplate.process(usersyncerData, writer);
        writer.close();
    }

    private static void createBidderConfigurationJavaFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final String bidderName = bidderData.getBidderName();
        final Template bidderConfigTemplate = cfg.getTemplate(BIDDER_CONFIG_TEMPLATE);
        final String bidderConfigFile = makeBidderFile(bidderData, FileType.CONFIG);
        FileWriter writer = new FileWriter(bidderConfigFile);
        bidderConfigTemplate.process(Collections.singletonMap("bidderName", bidderName), writer);
        writer.close();
    }

    private static void createBidderSchemaJsonFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final Map<String, Object> schemaData = new HashMap<>();
        schemaData.put("bidderParams", bidderData.getBidderParams());
        schemaData.put("bidderName", bidderData.getBidderName());

        final Template schemaTemplate = cfg.getTemplate(SCHEMA_TEMPLATE);
        final String schemaFile = makeBidderFile(bidderData, FileType.SCHEMA);
        FileWriter writer = new FileWriter(schemaFile);
        schemaTemplate.process(schemaData, writer);
        writer.close();
    }

    private static void createUsersyncerTestFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final UsersyncerData usersyncerData = BidderDataUtil.getUsersyncerData(bidderData);
        final Template usersyncerTestTemplate = cfg.getTemplate(USERSYNC_TEST_TEMPLATE);
        final String usersyncerTestFile = makeBidderFile(bidderData, FileType.TEST_USERSYNCER);
        FileWriter writer = new FileWriter(usersyncerTestFile);
        usersyncerTestTemplate.process(usersyncerData, writer);
        writer.close();
    }

    private static void createSimpleBidderTestFile(BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final Template bidderTestTemplate = cfg.getTemplate(SIMPLE_BIDDER_TEST_TEMPLATE);
        final String bidderTestFile = makeBidderFile(bidderData, FileType.TEST_BIDDER);
        FileWriter writer = new FileWriter(bidderTestFile);
        bidderTestTemplate.process(bidderData, writer);
        writer.close();
    }

    private static String makeBidderFile(BidderData bidderData, FileType fileType) throws IOException {
        final String bidderName = bidderData.getBidderName();
        final String capitalizedName = StringUtils.capitalize(bidderName);
        final String javaFilesPackages = "java/org/prebid/server/";
        final String srcMain = "/src/main/";
        StringBuilder stringBuilder = new StringBuilder(bidderData.getPbsDirectory());
        switch (fileType) {
            case EXT:
                stringBuilder.append(srcMain).append("java/");
                break;
            case BIDDER:
                stringBuilder.append(srcMain).append("java/");
                break;
            case USERSYNCER:
                stringBuilder.append(srcMain).append(javaFilesPackages).append("bidder/")
                        .append(bidderName.toLowerCase()).append("/").append(capitalizedName).append("Usersyncer.java");
                break;
            case PROPERTIES:
                stringBuilder.append(srcMain).append("resources/bidder-config/")
                        .append(bidderName.toLowerCase()).append(".yaml");
                break;
            case SCHEMA:
                stringBuilder.append(srcMain).append("resources/static/bidder-params/")
                        .append(bidderName).append(".json");
                break;
            case CONFIG:
                stringBuilder.append(srcMain).append(javaFilesPackages).append("spring/config/bidder/")
                        .append(capitalizedName).append("Configuration.java");
                break;
            case TEST_USERSYNCER:
                stringBuilder.append("/src/test/java/org/prebid/server/bidder/").append(bidderName.toLowerCase())
                        .append("/").append(capitalizedName).append("UsersyncerTest.java");
                break;
            case TEST_BIDDER:
                stringBuilder.append("/src/test/java/org/prebid/server/bidder/").append(bidderName.toLowerCase())
                        .append("/").append(capitalizedName).append("BidderTest.java");
                break;
            default:
                throw new IllegalArgumentException();
        }
        final String filePath = stringBuilder.toString();
        final Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        if (!(fileType.equals(FileType.BIDDER) || fileType.equals(FileType.EXT))) {
            Files.createFile(path);
        }
        System.out.println(filePath);

        return filePath;
    }

    private static Configuration defaultConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_28);
        try {
            cfg.setDirectoryForTemplateLoading(new File(TEMPLATES_DIRECTORY));
        } catch (IOException e) {
            e.printStackTrace();
        }

        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        return cfg;
    }

    public enum FileType {
        BIDDER,
        USERSYNCER,
        PROPERTIES,
        CONFIG,
        EXT,
        SCHEMA,
        TEST_USERSYNCER,
        TEST_BIDDER
    }
}

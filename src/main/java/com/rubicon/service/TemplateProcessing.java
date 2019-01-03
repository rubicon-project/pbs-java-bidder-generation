package com.rubicon.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubicon.model.BidderData;
import com.rubicon.model.BidderImplData;
import com.rubicon.model.ClassField;
import com.rubicon.model.ExtData;
import com.rubicon.model.MetaInfoData;
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

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TemplateProcessing {

    private final String templatesDirectory;
    private final BidderData bidderData;

    public TemplateProcessing(String inputFile, String templatesDirectory) {
        this.templatesDirectory = templatesDirectory;
        this.bidderData = ParseInputFile.parseInputFile(inputFile);
    }

    public void createBidderFiles(String metaInfoTemplate, String usersyncerTemplate, String propertiesTemplate,
                                  String bidderConfigTemplate) {
        try {
            createBidderConfigurationJavaFile(bidderConfigTemplate, templatesDirectory, bidderData);
            createMataInfoJavaFile(metaInfoTemplate, templatesDirectory, bidderData);
            createUsersyncerJavaFile(usersyncerTemplate, templatesDirectory, bidderData);
            createPropertiesYamlFile(propertiesTemplate, templatesDirectory, bidderData);

            final JavaFile extJavaFile = createExtJavaFile(bidderData);
            if (extJavaFile != null) {
                writeExtFile(extJavaFile, bidderData);
            }

            final JavaFile bidderJavaFile = createBidderJavaFile(bidderData);
            writeBidderFile(bidderJavaFile, bidderData);

        } catch (IOException | TemplateException e) {
            e.printStackTrace();
        }
    }

    private static JavaFile createExtJavaFile(BidderData bidderData) {
        final ExtData ext = bidderData.getExt();
        if (ext == null || CollectionUtils.isEmpty(ext.getFields())) {
            return null;
        }

        final String bidderName = bidderData.getBidderName();
        final List<ClassField> fields = ext.getFields();
        final TypeSpec.Builder extensionClassBuilder =
                TypeSpec.classBuilder("ExtImp" + StringUtils.capitalize(bidderName))
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(fields.size() > 4 ? Builder.class : AllArgsConstructor.class)
                        .addAnnotation(Value.class);

        for (ClassField field : fields) {
            final String fieldName = field.getFieldName();
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
        final String extFilePath = makeBidderFile(bidderData.getBidderName(), FileType.EXT);
        extFile.writeTo(Paths.get(extFilePath));
    }

    private static JavaFile createBidderJavaFile(BidderData bidderData) {
        final String bidderName = bidderData.getBidderName();
        final ExtData ext = bidderData.getExt();
        final boolean hasExtension = ext != null && CollectionUtils.isNotEmpty(ext.getFields());
        final ClassName extClass = hasExtension
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

        final BidderImplData bidderImplData = bidderData.getBidder();
        if (bidderImplData != null) {
            modifyImps(bidderClassBuilder, bidderData, extClass);
            modifyRequest(bidderClassBuilder, bidderData, extClass);
        }

        return JavaFile.builder("org.prebid.server.bidder."
                + bidderName.toLowerCase(), bidderClassBuilder.build())
                .skipJavaLangImports(true)
                .build();
    }

    private static void modifyImps(TypeSpec.Builder classBuilder, BidderData bidderData, ClassName extClass) {
        final List<Transformation> impModifications = bidderData.getBidder().getTransformations().stream()
                .filter(transformation -> transformation.getTarget().contains("imp"))
                .map(transformation -> new Transformation(
                        StringUtils.replace(transformation.getTarget(), "imp.", ""),
                        transformation.getStaticValue(), transformation.getFrom()))
                .collect(Collectors.toList());

        final List<Transformation> staticChanges = impModifications.stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getStaticValue()))
                .collect(Collectors.toList());

        final List<Transformation> extChanges = impModifications.stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getFrom()))
                .peek(transformation -> transformation.setFrom(StringUtils.replace(transformation.getFrom(), "ext.", "")))
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
                    final String staticValue = trans.getStaticValue();
                    final String[] split = StringUtils.split(target, ".");
                    if (split.length == 2) {
                        modifyImps.addCode("." + split[0] + "(imp.get" + StringUtils.capitalize(split[0])
                                + "().toBuilder()." + split[1] + "(" + staticValue + ").build())\n");
                    } else {
                        modifyImps.addCode("." + target + "(" + staticValue + ")\n");
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
        final List<Transformation> requestModifications = bidderData.getBidder().getTransformations().stream()
                .filter(transformation -> !transformation.getTarget().contains("imp"))
                .collect(Collectors.toList());

        requestModifications.forEach(System.out::println);

        final List<Transformation> staticChanges = requestModifications.stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getStaticValue()))
                .collect(Collectors.toList());

        final List<Transformation> extChanges = requestModifications.stream()
                .filter(transformation -> StringUtils.isNotBlank(transformation.getFrom()))
                .peek(transformation -> transformation.setFrom(StringUtils.replace(transformation.getFrom(), "ext.", "")))
                .collect(Collectors.toList());

        if (!staticChanges.isEmpty() || !extChanges.isEmpty()) {
            final ClassName bidRequest = ClassName.get("com.iab.openrtb.request", "BidRequest");
            final ClassName impWithExt = ClassName.get("org.prebid.server.bidder.model", "ImpWithExt");
            final MethodSpec.Builder modifyRequest = MethodSpec.methodBuilder("modifyRequest")
                    .addModifiers(Modifier.PROTECTED)
                    .addAnnotation(Override.class)
                    .addParameter(bidRequest, "bidRequest")
                    .addParameter(bidRequest.nestedClass("BidRequestBuilder"), "requestBuilder")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(List.class),
                            ParameterizedTypeName.get(impWithExt, extClass)), "impsWithExts")
                    .addCode("final " + extClass.simpleName() + " impExt = impsWithExts.stream()\n"
                            + ".map(ImpWithExt::getImpExt)\n"
                            + ".filter($T::nonNull)\n"
                            + ".findFirst().orElse(null);\n\n"
                            + "requestBuilder", Objects.class);

            if (!staticChanges.isEmpty()) {
                for (Transformation trans : staticChanges) {
                    final String target = trans.getTarget();
                    final String staticValue = trans.getStaticValue();
                    final String[] split = StringUtils.split(target, ".");
                    if (split.length == 2) {
                        modifyRequest.addCode("\n." + split[0] + "(bidRequest.get" + StringUtils.capitalize(split[0])
                                + "().toBuilder()." + split[1] + "(" + staticValue + ").build())\n");
                    } else {
                        modifyRequest.addCode("\n." + target + "(" + staticValue + ")");
                    }
                }
            }
            if (!extChanges.isEmpty()) {
                for (Transformation trans : extChanges) {
                    final String target = trans.getTarget();
                    final String value = trans.getFrom();
                    final String[] split = StringUtils.split(target, ".");
                    if (split.length == 2) {
                        modifyRequest.addCode("\n." + split[0] + "(bidRequest.get" + StringUtils.capitalize(split[0])
                                + "().toBuilder()." + split[1] + "(impExt.get" + StringUtils.capitalize(value)
                                + "().toString()).build())");
                    } else {
                        modifyRequest.addCode("\n." + target + "(impExt.get" + StringUtils.capitalize(value) + "())");
                    }
                }
            }
            modifyRequest.addCode(";\n");
            classBuilder.addMethod(modifyRequest.build());
        }
    }

    private static void writeBidderFile(JavaFile bidderFile, BidderData bidderData) throws IOException {
        final String bidderFilePath = makeBidderFile(bidderData.getBidderName(), FileType.BIDDER);
        bidderFile.writeTo(Paths.get(bidderFilePath));
    }

    private static void createMataInfoJavaFile(String templateFile,
                                               String templatesDirectory,
                                               BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration(templatesDirectory);
        final MetaInfoData metaInfoData = ParseInputFile.parseMetaInfoData(bidderData);
        final Template metaInfoTemplate = cfg.getTemplate(templateFile);
        final String bidderFile = makeBidderFile(metaInfoData.getBidderName(), FileType.META_INFO);
        final FileWriter writer = new FileWriter(bidderFile);
        metaInfoTemplate.process(metaInfoData, writer);
    }

    private static void createPropertiesYamlFile(String templateFile,
                                                 String templatesDirectory,
                                                 BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration(templatesDirectory);
        final PropertiesData propertiesData = ParseInputFile.parsePropertiesData(bidderData);
        final Template propertiesTemplate = cfg.getTemplate(templateFile);
        final String propertiesFile = makeBidderFile(propertiesData.getBidderName(), FileType.PROPERTIES);
        FileWriter writer = new FileWriter(propertiesFile);
        propertiesTemplate.process(propertiesData, writer);
    }

    private static void createUsersyncerJavaFile(String templateFile,
                                                 String templatesDirectory,
                                                 BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration(templatesDirectory);
        final UsersyncerData usersyncerData = ParseInputFile.parseUsersyncerData(bidderData);
        final Template usersyncerTemplate = cfg.getTemplate(templateFile);
        final String usersyncerFile = makeBidderFile(usersyncerData.getBidderName(), FileType.USERSYNCER);
        FileWriter writer = new FileWriter(usersyncerFile);
        usersyncerTemplate.process(usersyncerData, writer);
    }

    private static void createBidderConfigurationJavaFile(String templateFile,
                                                          String templatesDirectory,
                                                          BidderData bidderData) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration(templatesDirectory);
        final String bidderName = bidderData.getBidderName();
        final Template bidderConfigTemplate = cfg.getTemplate(templateFile);
        final String bidderConfigFile = makeBidderFile(bidderName, FileType.CONFIG);
        FileWriter writer = new FileWriter(bidderConfigFile);
        bidderConfigTemplate.process(Collections.singletonMap("bidderName", bidderName), writer);
    }

    private static String makeBidderFile(String bidderName, FileType fileType) throws IOException {
        final String capitalizedName = StringUtils.capitalize(bidderName);
        final String javaFilesPackages = "java/org/prebid/server/";
        final StringBuilder stringBuilder =
                new StringBuilder("C:/Users/rostyslav.goncharuk/IdeaProjects/prebid-server-java/src/main/");
        switch (fileType) {
            case EXT:
                stringBuilder.append("java/");
                break;
            case BIDDER:
                stringBuilder.append("java/");
                break;
            case META_INFO:
                stringBuilder.append(javaFilesPackages).append("bidder/").append(bidderName.toLowerCase()).append("/")
                        .append(capitalizedName).append("MetaInfo.java");
                break;
            case USERSYNCER:
                stringBuilder.append(javaFilesPackages).append("bidder/").append(bidderName.toLowerCase()).append("/")
                        .append(capitalizedName).append("Usersyncer.java");
                break;
            case PROPERTIES:
                stringBuilder.append("resources/bidder-config/")
                        .append(bidderName.toLowerCase()).append(".yaml");
                break;
            case CONFIG:
                stringBuilder.append(javaFilesPackages).append("spring/config/bidder/")
                        .append(capitalizedName).append("Configuration.java");
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

    private static Configuration defaultConfiguration(String templatesDirectory) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_28);
        try {
            cfg.setDirectoryForTemplateLoading(new File(templatesDirectory));
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
        META_INFO,
        USERSYNCER,
        PROPERTIES,
        CONFIG,
        EXT
    }
}

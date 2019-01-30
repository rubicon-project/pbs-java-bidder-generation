package com.rubicon.service.processing;

import com.rubicon.model.BidderData;
import com.rubicon.model.PropertiesData;
import com.rubicon.model.UsersyncerData;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class TemplateProcessing {

    private static final String TEMPLATES_DIRECTORY = "src/main/resources/templates";
    private static final String USERSYNCER_TEMPLATE = "usersyncer.ftl";
    private static final String PROPERTIES_TEMPLATE = "properties.ftl";
    private static final String BIDDER_CONFIG_TEMPLATE = "configuration.ftl";
    private static final String SCHEMA_TEMPLATE = "schema.ftl";
    private static final String USERSYNC_TEST_TEMPLATE = "usersyncer_test.ftl";
    private static final String SIMPLE_BIDDER_TEST_TEMPLATE = "bidder_test.ftl";

    @Autowired
    private FileCreator fileCreator;

    public void generateBidderFilesFromTemplates(BidderData bidderData) throws IOException, TemplateException {
        createPropertiesYamlFile(bidderData);
        createUsersyncerJavaFile(bidderData);
        createUsersyncerTestFile(bidderData);
        createBidderSchemaJsonFile(bidderData);
        createBidderConfigurationJavaFile(bidderData);

        if (bidderData.getBidderParams() == null && CollectionUtils.isEmpty(bidderData.getTransformations())) {
            createSimpleBidderTestFile(bidderData);
        }
    }

    private void createPropertiesYamlFile(BidderData bidderData) throws IOException, TemplateException {
        final PropertiesData propertiesData = bidderData.getProperties();
        propertiesData.setBidderName(bidderData.getBidderName());
        createFileFromTemplate(bidderData, propertiesData, PROPERTIES_TEMPLATE, FileCreator.FileType.PROPERTIES);
    }

    private void createUsersyncerJavaFile(BidderData bidderData) throws IOException, TemplateException {
        final UsersyncerData usersyncerData = bidderData.getUsersyncer();
        usersyncerData.setBidderName(bidderData.getBidderName());
        createFileFromTemplate(bidderData, usersyncerData, USERSYNCER_TEMPLATE, FileCreator.FileType.USERSYNCER);
    }

    private void createBidderConfigurationJavaFile(BidderData bidderData) throws IOException, TemplateException {
        createFileFromTemplate(bidderData, bidderData, BIDDER_CONFIG_TEMPLATE, FileCreator.FileType.CONFIG);
    }

    private void createBidderSchemaJsonFile(BidderData bidderData) throws IOException, TemplateException {
        final Map<String, Object> schemaData = new HashMap<>();
        schemaData.put("bidderParams", bidderData.getBidderParams());
        schemaData.put("bidderName", bidderData.getBidderName());
        createFileFromTemplate(bidderData, schemaData, SCHEMA_TEMPLATE, FileCreator.FileType.SCHEMA);
    }

    private void createUsersyncerTestFile(BidderData bidderData) throws IOException, TemplateException {
        final UsersyncerData usersyncerData = bidderData.getUsersyncer();
        usersyncerData.setBidderName(bidderData.getBidderName());
        createFileFromTemplate(bidderData, usersyncerData, USERSYNC_TEST_TEMPLATE, FileCreator.FileType.TEST_USERSYNCER);
    }

    private void createSimpleBidderTestFile(BidderData bidderData) throws IOException, TemplateException {
        createFileFromTemplate(bidderData, bidderData, SIMPLE_BIDDER_TEST_TEMPLATE, FileCreator.FileType.TEST_BIDDER);
    }

    private void createFileFromTemplate(BidderData bidderData, Object templateData, String templateFile,
                                        FileCreator.FileType fileType) throws IOException, TemplateException {
        final Configuration cfg = defaultConfiguration();
        final Template propertiesTemplate = cfg.getTemplate(templateFile);
        final String createdFile = fileCreator.makeBidderFile(bidderData, fileType);
        final FileWriter writer = new FileWriter(createdFile);
        propertiesTemplate.process(templateData, writer);
        writer.close();
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
}

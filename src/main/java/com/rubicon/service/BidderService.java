package com.rubicon.service;

import com.rubicon.model.BidderData;
import com.rubicon.service.processing.CodeGenerationProcessing;
import com.rubicon.service.processing.TemplateProcessing;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class BidderService {

    private TemplateProcessing templateProcessing;
    private CodeGenerationProcessing generationProcessing;

    public BidderService(TemplateProcessing templateProcessing, CodeGenerationProcessing generationProcessing) {
        this.templateProcessing = templateProcessing;
        this.generationProcessing = generationProcessing;
    }

    public void generateBidderFiles(BidderData bidderData) throws IOException, TemplateException {
        templateProcessing.generateBidderFilesFromTemplates(bidderData);
        generationProcessing.generateBidderJavaFiles(bidderData);
    }
}

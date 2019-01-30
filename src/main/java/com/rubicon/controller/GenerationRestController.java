package com.rubicon.controller;

import com.rubicon.model.BidderData;
import com.rubicon.service.BidderService;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
public class GenerationRestController {

    @Autowired
    private BidderService bidderService;

    @PostMapping(value = "/generate", consumes = "application/json")
    @ResponseStatus(code = HttpStatus.OK, reason = "Generating bidder files")
    public void generate(@Validated @RequestBody BidderData bidderData) {
        try {
            bidderService.generateBidderFiles(bidderData);
        } catch (IOException | TemplateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}

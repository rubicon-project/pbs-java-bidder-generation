package com.rubicon.configuration;

import com.rubicon.service.TemplateProcessing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public TemplateProcessing templateProcessing() {
        return new TemplateProcessing("src/main/resources/templates", "meta_info.ftl",
                "usersyncer.ftl", "properties.ftl",
                "configuration.ftl", "schema.ftl",
                "usersyncer_test.ftl", "bidder_test.ftl");
    }
}

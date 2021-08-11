package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.${bidderName?lower_case}.${bidderName?cap_first}Bidder;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderDepsAssembler;
import org.prebid.server.spring.config.bidder.util.UsersyncerCreator;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/${bidderName?lower_case}.yaml", factory = YamlPropertySourceFactory.class)
public class ${bidderName?cap_first}Configuration {

    private static final String BIDDER_NAME = "${bidderName?lower_case}";

    @Value("${r"${external-url}"}")
    @NotBlank
    private String externalUrl;

    @Autowired
    private JacksonMapper mapper;

    @Autowired
    @Qualifier("${bidderName?lower_case}ConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Bean("${bidderName?lower_case}ConfigurationProperties")
    @ConfigurationProperties("adapters.${bidderName?lower_case}")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ${bidderName?lower_case}BidderDeps() {
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .usersyncerCreator(UsersyncerCreator.create(externalUrl))
                .bidderCreator(config -> new ${bidderName?cap_first}Bidder(config.getEndpoint(), mapper))
                .assemble();
    }
}

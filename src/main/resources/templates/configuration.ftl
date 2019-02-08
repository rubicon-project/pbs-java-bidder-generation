package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.${bidderName?lower_case}.${bidderName?cap_first}Bidder;
import org.prebid.server.bidder.${bidderName?lower_case}.${bidderName?cap_first}Usersyncer;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/bidder-config/${bidderName?lower_case}.yaml", factory = YamlPropertySourceFactory.class)
public class ${bidderName?cap_first}Configuration {

    private static final String BIDDER_NAME = "${bidderName}";

    @Autowired
    @Qualifier("${bidderName?lower_case}ConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${r"${external-url}"}")
    private String externalUrl;

    @Bean("${bidderName?lower_case}ConfigurationProperties")
    @ConfigurationProperties("adapters.${bidderName?lower_case}")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps ${bidderName}BidderDeps() {
        final Usersyncer usersyncer = new ${bidderName?cap_first}Usersyncer(configProperties.getUsersyncUrl(), externalUrl);
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                        metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                        metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr()))
                .usersyncer(usersyncer)
                .bidderCreator(() -> new ${bidderName?cap_first}Bidder(configProperties.getEndpoint()))
                .assemble();
    }
}

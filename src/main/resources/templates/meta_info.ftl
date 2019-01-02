package org.prebid.server.bidder.${bidderName?lower_case};

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

<#compress>
<#if appMediaTypes?size == 0 || siteMediaTypes?size == 0>import java.util.Collections;</#if>
<#if appMediaTypes?size gt 0 || siteMediaTypes?size gt 0>import java.util.Arrays;</#if>
</#compress>


/**
 * Defines ${bidderName?cap_first} meta info
 */
public class ${bidderName?cap_first}MetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public ${bidderName?cap_first}MetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "${maintainerEmail}",
                <#if appMediaTypes?size == 0>Collections.emptyList()<#else>Arrays.asList("${appMediaTypes?join("\", \"")}")</#if>,
                <#if siteMediaTypes?size == 0>Collections.emptyList()<#else>Arrays.asList("${siteMediaTypes?join("\", \"")}")</#if>,
                null, ${vendorId}, pbsEnforcesGdpr);
    }

    /**
     * Returns ${bidderName?cap_first} bidder related meta information: maintainer email address and supported media types.
     */
    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}

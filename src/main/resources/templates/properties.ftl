adapters:
  ${bidderName?lower_case}:
    enabled: false
    endpoint: ${endpointUrl}
    usersync-url: ${usersyncerUrl}
    pbs-enforces-gdpr: true
    deprecated-names:
    aliases:
    meta-info:
      maintainer-email: ${maintainerEmail}
      app-media-types:
        <#list appMediaTypes as type>
        - ${type}
        <#else>
        </#list>
      site-media-types:
        <#list siteMediaTypes as type>
        - ${type}
        <#else>
        </#list>
      supported-vendors:
        <#list supportedVendors as vendor>
        - ${vendor}
        <#else>
        </#list>
      vendor-id: ${vendorId}
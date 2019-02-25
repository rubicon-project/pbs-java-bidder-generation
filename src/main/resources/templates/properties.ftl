adapters:
  ${bidderName?lower_case}:
    enabled: false
    endpoint: ${endpointUrl}
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
      vendor-id: ${vendorId}
    usersync:
      url: ${usersyncerUrl}
      redirect-url: /setuid?bidder=${bidderName?lower_case}&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&uid=${uidPlaceholder}
      cookie-family-name: ${bidderName?lower_case}
      type: redirect
      support-cors: false
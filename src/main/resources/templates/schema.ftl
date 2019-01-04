{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "title": "${bidderName?cap_first} Params",
  "description": "A schema which validates params accepted by the ${bidderName?cap_first}",
  "type": "object",
  "properties": {
  <#list bidderParams as param>
    "${param.name}": {
      "type": "${param.type?lower_case}"
    }<#if param?has_next>,</#if>
    <#else>
    </#list>
  },
  "required": [
    <#list bidderParams as param>
    "${param.name}"<#if param?has_next>,</#if>
    <#else>
    </#list>
  ]
}

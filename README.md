# bidder-generation-tool

A tool that generates bidder-specific files required for PreBid Server(PBS) from a specific .json file. 
That input file should contain all necessary information provided by client via UI (i.e. should be generated by front-end in future).

See src/test_input.json for example of input json file with data and its format.

Currently supports static (e.g. setting a specific constant value) and dynamic transformations(values taken from bidder-specific extension) for request impressions and static transformations only for other request fields.

The tool generates following files in local PBS directory:
1. `src/main/org/prebid/server/bidder/{biddername}/{BidderName}Bidder.java` - java class that handles bidder request transformations;
2. `src/main/org/prebid/server/bidder/{biddername}/{BidderName}MetaInfo.java` - java class that describes bidder meta information;
3. `src/main/org/prebid/server/bidder/{biddername}/{BidderName}Usersyncer.java` - java class that handles user sync;
4. `src/main/org/prebid/server/spring/config/bidder/{BidderName}Configuration.java` - bidder java configuration class.
5. `src/main/org/prebid/server/proto/openrtb/ext/request/{biddername}/ExtImp{BidderName}.java` - java class that is a model for bidder-specific extension, passed in request.imp.ext.bidder;
6. `src/main/resources/bidder-config/{biddername}.yaml` - bidder configuration properties file;
7. `src/main/resources/static/bidder-params/{bidderName}.json` - bidder json schema that describes bidder-specific parameters;

Prerequisites:
- Java 8+
- Maven 3+

Requires input json file(in corresponding format) and PBS server files.

Steps:

1. Clone the bidder-generation-tool repository with "git clone git@github.rp-core.com:rgoncharuk/bidder-generation-tool.git" command

2. Run with command `mvn clean package exec:java -Dexec.args="arg1 arg2"`, where arg1 is input .json file with bidder data and arg2 - path to PBS server directory. Path can be relative to bidder-generation-tool or absolute, separated by space only, e.g.: 
`mvn clean package exec:java -Dexec.args="src/test_input.json C:/prebid-server-java"`

5. After completion you may check the results in your local PBS directory or project.

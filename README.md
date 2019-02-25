# Bidder Generation Tool

## Description

The tool generates bidder-specific files required for PreBid Server(PBS). 
A user should provide all necessary information via user interface(web page).

## Supported Transformations

The tool doesn't support type casting, therefore fields should be modified with an appropriate type value whether it's a literal or value taken from another field.
Also, tool doesn't support conditional changes, like the ones that could modify a certain field depending on some condition. 

OpenRTB fields that cannot be modified(i.e. have a value assigned to them):
1. `request.site.content`, `request.app.content`;
1. Any exchange-specific extensions to OpenRTB (`.ext` fields);
1. A specific element of list, array or sequence.

Currently supported transformations:
1. Static transformations, e.g. setting a constant value(or null) to a field: 
`request.imp.id = "id"`, `request.at = 1`, `request.imp[i].banner.format = null`;
2. Dynamic transformations, e.g. value taken from another field:
    * On impression level - values can be taken from `request.imp[i]` and `request.imp[i].ext.bidder.`, 
    where `imp[i]` is a current impression and `.ext.bidder` - bidder-specific impression extension. For example: 
    `request.imp[i].tagid <- request.imp[i].ext.bidder.bidderField`
    `request.imp[i].banner.id <- request.imp[i].id`    
    * On request level - values can be taken from any `request` field, except selecting a specific value from list, 
    array or sequence fields, e.g. an array can be assigned from another array field, but `array[i]` cannot be assigned 
    to specific field. For example:
    `request.site.publisher.id <- request.id`
    `request.app.id <- request.app.content.producer.id`
    `request.test <- request.site.privacypolicy`

Finally, if an OpenRTB Object's field needs to be modified, tool either modifies an existing object, or creates a new one and sets target field(other fields would be empty).

All above-mentioned transformations can be manually customized in case something cannot be done via code generation by modifying `{BidderName}Bidder.java` file.
     
## Generated Files

The tool presumes that local PBS directory is in the same parent directory as Bidder Generation Tool and 
it is named according to PBS github repository - `prebid-server-java`. 
Otherwise, it would create `prebid-server-java` folder in Bidder Generation Tool parent directory and generate 
folders and files there. In such case, you would have to manually move files to where the PBS is to submit a Pull Request.

Following files(and folders, if they don't exist) are being generated in local PBS directory:
1. `src/main/java/org/prebid/server/bidder/{biddername}/{BidderName}Bidder.java` - java class that handles bidder request transformations;
1. `src/main/java/org/prebid/server/spring/config/bidder/{BidderName}Configuration.java` - bidder java configuration class;
1. `src/main/java/org/prebid/server/proto/openrtb/ext/request/{biddername}/ExtImp{BidderName}.java` - java class that is a model for bidder-specific extension, passed in request.imp.ext.bidder;
1. `src/main/resources/bidder-config/{biddername}.yaml` - bidder configuration properties, user sync and meta info file;
1. `src/main/resources/static/bidder-params/{bidderName}.json` - bidder json schema that describes bidder-specific parameters;
1. `src/test/java/org/prebid/server/bidder/{biddername}/{BidderName}BidderTest.java` - java test class for Bidder class.

In case the any file at the targeted path already exists - it would be overridden.

## How to run

Prerequisites:
- Java 8+
- Maven 3.3+

Steps:

1. OPTIONAL. Clone prebid-server-java repository with `git@github.com:rubicon-project/prebid-server-java.git` command. 
If you have it already, proceed to step 2. If you want to just generate files and move them at will - skip this step.

2. Clone the bidder-generation-tool repository with `git@github.com:rubicon-project/pbs-java-bidder-generation.git` 
command to the same parent directory where you have `prebid-server-java`. 
In case PBS directory name is different or it is absent - the tool will create `prebid-server-java` folder in 
it's parent directory and write files there so you can move them where you need.

3. Run Bidder Generation Tool from its directory with command `mvn spring-boot:run`. 
After application loads it should open its homepage at your default browser or if it did not - got to `localhost:8080`

4. Fill up the form providing all necessary information and bidder implementation details and click `Generate Bidder Files`.
In case the files already exist (e.g. you need to change something) - make necessary changes in the form and click 
`Generate Bidder Files` again - all necessary files will be overridden.

5. Check generated files in `prebid-server-java` directory.

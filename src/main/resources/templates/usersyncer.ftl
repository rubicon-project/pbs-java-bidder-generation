package org.prebid.server.bidder.${bidderName?lower_case};

import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

/**
 * ${bidderName?cap_first} {@link Usersyncer} implementation
 */
public class ${bidderName?cap_first}Usersyncer implements Usersyncer {

    private final UsersyncInfo usersyncInfo;

    public ${bidderName?cap_first}Usersyncer(String usersyncUrl, String externalUrl) {
        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));
    }

    /**
     * Creates {@link UsersyncInfo} from usersyncUrl and externalUrl
     */
    private static UsersyncInfo createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri = HttpUtil.encodeUrl(externalUrl)
                + "%2Fsetuid%3Fbidder%3D${cookieFamilyName}%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D";

        return UsersyncInfo.of(String.format("%s%s", usersyncUrl, redirectUri), "${usersyncInfoType}", false);
    }

    /**
     * Returns ${bidderName?cap_first} cookie family
     */
    @Override
    public String cookieFamilyName() {
        return "${cookieFamilyName}";
    }

    /**
     * Returns ${bidderName?cap_first} {@link UsersyncInfo}
     */
    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }
}

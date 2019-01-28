package org.prebid.server.bidder.${bidderName?lower_case};

import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class ${bidderName?cap_first}UsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new ${bidderName?cap_first}Usersyncer(null, null));
        assertThatNullPointerException().isThrownBy(() -> new ${bidderName?cap_first}Usersyncer("some_url", null));
    }

    @Test
    public void creationShouldInitExpectedUsersyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org//?ri=partnerId&ru=http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3D${cookieFamilyName}"
                        + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D",
                "redirect", false);

        // when
        final UsersyncInfo result = new ${bidderName?cap_first}Usersyncer("//usersync.org/",
                "http://external.org/").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}

package com.openshift.portal;

import com.openshift.portal.acm.AcmHubClient;
import com.openshift.portal.acm.LiveAcmHubClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** With the simulator off, hubs are read by the live ACM client. */
@SpringBootTest(properties = "openshift.portal.simulator.enabled=false")
@ActiveProfiles("dev")
class LiveClientWiringTest {

    @Autowired
    private AcmHubClient hubClient;

    @Test
    void simulatorOffWiresTheLiveClient() {
        assertThat(hubClient).isInstanceOf(LiveAcmHubClient.class);
    }
}

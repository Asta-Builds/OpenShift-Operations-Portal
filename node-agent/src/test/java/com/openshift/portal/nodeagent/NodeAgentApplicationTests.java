package com.openshift.portal.nodeagent;

import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "node-agent.cluster-name=prod-east-1",
        "node-agent.portal.url=https://portal.example.com/api/v1",
        // Keeps the scheduled report from running against the unreachable portal during the test
        "node-agent.initial-delay=PT1H"
})
class NodeAgentApplicationTests {

    /** No cluster here; the client's own configuration depends on the machine's kubeconfig and proxy settings. */
    @MockBean
    private KubernetesClient kubernetesClient;

    @Autowired
    private NodeAgentProperties properties;

    @Test
    void contextLoadsWithTheBuildVersion() {
        assertThat(properties.getClusterName()).isEqualTo("prod-east-1");
        assertThat(properties.getVersion()).isNotBlank().doesNotContain("@");
    }
}

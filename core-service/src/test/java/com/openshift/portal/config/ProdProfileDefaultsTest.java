package com.openshift.portal.config;

import com.openshift.portal.controller.SimulatorController;
import com.openshift.portal.service.AcmSimulatorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProdProfileDefaultsTest {

    @Test
    void prodProfileFailsClosedWithoutOverrides() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        // Resolve placeholders from their defaults only, whatever this machine's environment contains
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("application-prod", new ClassPathResource("application-prod.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        AcmProperties properties = Binder.get(environment).bind("openshift.portal", AcmProperties.class).get();

        assertThat(properties.getSecurity().isEnabled()).isTrue();
        assertThat(properties.getSimulator().isEnabled()).isFalse();
    }

    @Test
    void simulatorEndpointsExistOnlyWhenTheSimulatorIsEnabled() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withBean(AcmSimulatorService.class, () -> mock(AcmSimulatorService.class))
                .withUserConfiguration(SimulatorController.class);

        runner.withPropertyValues("openshift.portal.simulator.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SimulatorController.class));
        runner.withPropertyValues("openshift.portal.simulator.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(SimulatorController.class));
    }
}

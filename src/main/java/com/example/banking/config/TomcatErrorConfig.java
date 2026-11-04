package com.example.banking.config;

import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class TomcatErrorConfig {

    /** Runs last, so it also replaces the ErrorReportValve registered by Spring Boot. */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> jsonErrorReportValveCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            Pipeline hostPipeline = context.getParent().getPipeline();
            for (Valve valve : hostPipeline.getValves()) {
                if (valve instanceof ErrorReportValve) {
                    hostPipeline.removeValve(valve);
                }
            }
            hostPipeline.addValve(new JsonErrorReportValve());
            // Otherwise the host adds its default ErrorReportValve back when it starts.
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
            }
        });
    }
}

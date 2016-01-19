package org.jboss.fuse.microservice;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class CamelRestRoute extends RouteBuilder {

    private static final String HOST = "0.0.0.0";
    private static final String PORT = "8080";

    @Override public void configure() throws Exception {

        restConfiguration().component("servlet").host(HOST).setPort(PORT);

        // use the rest DSL to define the rest services
        rest("/users/")
                .get("{id}/hello")
                .route()
                .process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("id", String.class);
                        exchange.getOut().setBody("Hello " + id + "! Welcome from pod : " + System.getenv("HOSTNAME") );
                    }
                });

    }
}

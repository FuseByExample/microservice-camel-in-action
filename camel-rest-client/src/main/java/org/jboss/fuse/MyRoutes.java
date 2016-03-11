package org.jboss.fuse;

import javax.inject.Inject;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;

/**
 * Configures all our Camel routes, components, endpoints and beans
 */
@ContextName("myCdiCamelContext")
public class MyRoutes extends RouteBuilder {

    @Inject
    @Uri("timer:foo?period=5000")
    private Endpoint inputEndpoint;

    @Inject
    @Uri("netty4-http:http://{{service:hellorest}}?keepalive=false&disconnect=true")
    private Endpoint httpEndpoint;

    @Inject
    private SomeBean someBean;

    @Override
    public void configure() throws Exception {

        from(inputEndpoint)
            .setHeader("user").method(someBean,"getRandomUser")
            .setHeader("CamelHttpPath").simple("/camel-rest-service/camel/users/${header.user}/hello")
            .to(httpEndpoint)
            .log("Response : ${body}");
    }

}

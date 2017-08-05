package com.hyperadvanced.webscript;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

/**
 * @author Ewan
 */
final class WebScript {

    static final String PAYLOAD = "Payload";
    static final String SCRIPT_URL = "ScriptURL";
    private static final String TIMER_SCRIPT_FILENAME = "timer.groovy";
    private static final String BINDINGS_PROPERTIES = "bindings.properties";

    public static void main(String[] args) throws Exception {
        final CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final JsonDataFormat jsonDataFormat = new JsonDataFormat(JsonLibrary.Jackson);
                restConfiguration().port(2045);
                rest("/webscript")
                    .post()
                    .consumes("application/json")
                    .produces("application/json")
                    .route()
                    .routeId("WebScript")
                    .log(SCRIPT_URL + ": ${header." + SCRIPT_URL + "}")
                    .process(new JsonPayloadSlurper())
                    .to("direct:Invoke")
                    .marshal(jsonDataFormat)
                    .log("Response: ${body}");

                rest("/timer")
                    .get()
                    .produces("text/plain")
                    .route()
                    .routeId("GetTimer")
                    .process(new LoadFileToOutBody(TIMER_SCRIPT_FILENAME))
                    .endRest()
                    .put()
                    .consumes("text/plain")
                    .produces("text/plain")
                    .route()
                    .routeId("UpdateTimer")
                    .process(new WriteInBodyToFile(TIMER_SCRIPT_FILENAME, false))
                    .endRest();


                rest("/bindings")
                    .produces("text/plain")
                    .get().route().routeId("GetBindings")
                    .process(new LoadFileToOutBody(BINDINGS_PROPERTIES))
                    .endRest()
                    .get("/{scriptId}").route().routeId("GetBinding")
                    .process(new LoadFileToInBody(BINDINGS_PROPERTIES))
                    .process(exchange -> {
                        final Properties properties = exchange.getIn().getBody(Properties.class);
                        final String binding = properties.getProperty(exchange.getIn().getHeader("scriptId", String.class));
                        exchange.getOut().setBody(binding);
                    })
                    .endRest()
                    .put("/{scriptId}").route().routeId("PutBinding")
                    .setHeader("binding", bodyAs(String.class))
                    .process(new LoadFileToInBody(BINDINGS_PROPERTIES))
                    .process(exchange -> {
                        final Message in = exchange.getIn();
                        final Properties properties = in.getBody(Properties.class);
                        final String scriptId = in.getHeader("scriptId", String.class);
                        final String binding = in.getHeader("binding", String.class);
                        properties.setProperty(scriptId, binding);
                        in.setBody(properties);
                    })
                    .process(exchange -> exchange.getIn().getBody(Properties.class).store(new FileWriter(BINDINGS_PROPERTIES, false), null))
                    .process(exchange -> {
                        final Properties properties = exchange.getIn().getBody(Properties.class);
                        final String binding = properties.getProperty(exchange.getIn().getHeader("scriptId", String.class));
                        exchange.getOut().setBody(binding);
                    })
                    .endRest();


                from("direct:Invoke")
                    .routeId("Invoke")
                    .log(SCRIPT_URL + ": ${header." + SCRIPT_URL + "}")
                    .to("direct:Resolve")
                    .log("Script: ${body}")
                    .process(new GroovyScriptEvaluator(context.createProducerTemplate()))
                    .log("Result: ${body}");

                from("direct:Resolve")
                    .routeId("Resolve")
                    .process(exchange -> {
                        final String scriptUrl = exchange.getIn().getHeader(SCRIPT_URL, String.class);
                        if (scriptUrl.toLowerCase().startsWith("script:")) {
                            final String scriptId = scriptUrl.replaceAll("(?i)^script:[/]*", "");
                            final Properties scriptNameBindings = new Properties();
                            scriptNameBindings.load(new FileReader(BINDINGS_PROPERTIES));
                            final String remoteUrl = scriptNameBindings.getProperty(scriptId);
                            if (remoteUrl == null) {
                                throw new IllegalArgumentException("Script id '" + scriptId + "' is not bound");
                            } else {
                                exchange.getIn().setHeader(SCRIPT_URL, remoteUrl);
                            }
                        }
                    })
                    .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_GET))
                    .setHeader(CacheConstants.CACHE_KEY, header(SCRIPT_URL))
                    .to("cache://ScriptCache")
                    .choice().when(header(CacheConstants.CACHE_ELEMENT_WAS_FOUND).isNull())
                    .process(new UrlScriptResolver())
                    .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                    .setHeader(CacheConstants.CACHE_KEY, header(SCRIPT_URL))
                    .to("cache://ScriptCache")
                    .end();

                from("timer://secondTrigger?fixedRate=true&period=1000")
                    .process(exchange -> exchange.getIn().setBody(new File(TIMER_SCRIPT_FILENAME), String.class))
                    .process(new GroovyScriptEvaluator(context.createProducerTemplate()));

                // TODO: bindings could be immutable, and reference to bindings passed in as parameter to URL, or argument to script invocation - this allows for strict version control and update policy)
                // TODO: Handle exceptions thrown by script
                // TODO: Timeout script that runs too long
                // TODO: Use missingMethod to allow scripts to be run using 'script-name'(arg...)
            }
        });
        context.start();
    }
}

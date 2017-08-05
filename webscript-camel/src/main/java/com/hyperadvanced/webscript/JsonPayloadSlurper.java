package com.hyperadvanced.webscript;

import groovy.json.JsonSlurper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ewan
 */
final class JsonPayloadSlurper implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonPayloadSlurper.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        final String payloadJson = exchange.getIn().getBody(String.class);
        final Object payload = new JsonSlurper().parseText(payloadJson);
        exchange.getIn().setHeader(WebScript.PAYLOAD, payload);
        LOG.info("Parsed payload: {} into {}", payloadJson, payload);
    }
}

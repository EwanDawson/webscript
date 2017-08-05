package com.hyperadvanced.webscript;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.codehaus.groovy.runtime.MethodClosure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author Ewan
 */
final class GroovyScriptEvaluator implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyScriptEvaluator.class);

    private final ProducerTemplate producerTemplate;

    GroovyScriptEvaluator(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String script = exchange.getIn().getBody(String.class);
        final Object payload = exchange.getIn().getHeader(WebScript.PAYLOAD);
        LOG.info("Payload: {}", payload);
        final Binding binding = new Binding();
        binding.setVariable("payload", payload);
        final MethodClosure methodClosure = new MethodClosure(this, "invoke");
        binding.setVariable("invoke", methodClosure);
        final GroovyShell shell = new GroovyShell(binding);
        LOG.debug("Evaluating script: {}", script);
        final Object result = shell.evaluate(script);
        exchange.getOut().setBody(result);
    }

    public CompletableFuture<Object> invoke(String scriptName, List<Object> payload) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(WebScript.SCRIPT_URL, scriptName);
        headers.put(WebScript.PAYLOAD, payload);
        LOG.info("Invoking script: {} with payload {}", scriptName, payload);
        return producerTemplate.asyncRequestBodyAndHeaders("direct:Invoke", null, headers);
    }
}

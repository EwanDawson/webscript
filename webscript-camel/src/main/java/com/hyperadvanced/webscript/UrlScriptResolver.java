package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Ewan
 */
final class UrlScriptResolver implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        final String scriptUrl = exchange.getIn().getHeader(WebScript.SCRIPT_URL, String.class);
        final HttpURLConnection connection = (HttpURLConnection) new URL(scriptUrl).openConnection();
        final InputStream inputStream = connection.getInputStream();
        exchange.getIn().setBody(inputStream, String.class);
    }
}

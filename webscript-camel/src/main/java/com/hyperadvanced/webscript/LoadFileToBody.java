package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import java.io.File;

/**
 * @author Ewan
 */
abstract class LoadFileToBody implements Processor {

    private final String filename;

    LoadFileToBody(String filename) {
        this.filename = filename;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final File file = new File(filename);
        getMessage(exchange).setBody(file);
    }

    protected abstract Message getMessage(Exchange exchange);
}
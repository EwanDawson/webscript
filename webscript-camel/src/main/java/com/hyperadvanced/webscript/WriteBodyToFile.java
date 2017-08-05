package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import java.io.FileWriter;

/**
 * @author Ewan
 */
abstract class WriteBodyToFile implements Processor {

    private final String filename;
    private final boolean append;

    WriteBodyToFile(String filename, boolean append) {
        this.filename = filename;
        this.append = append;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try (final FileWriter writer = new FileWriter(filename, append)) {
            writer.append(getMessage(exchange).getBody(String.class));
        }
    }

    protected abstract Message getMessage(Exchange exchange);
}

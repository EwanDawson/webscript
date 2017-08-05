package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * @author Ewan
 */
final class WriteOutBodyToFile extends WriteBodyToFile {
    protected WriteOutBodyToFile(String filename, boolean append) {
        super(filename, append);
    }

    @Override
    protected Message getMessage(Exchange exchange) {
        return exchange.getOut();
    }
}

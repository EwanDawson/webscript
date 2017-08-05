package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * @author Ewan
 */
final class WriteInBodyToFile extends WriteBodyToFile {
    WriteInBodyToFile(String filename, boolean append) {
        super(filename, append);
    }

    @Override
    protected Message getMessage(Exchange exchange) {
        return exchange.getIn();
    }
}

package com.hyperadvanced.webscript;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * @author Ewan
 */
final class LoadFileToOutBody extends LoadFileToBody {
    LoadFileToOutBody(String filename) {
        super(filename);
    }

    @Override
    protected Message getMessage(Exchange exchange) {
        return exchange.getOut();
    }
}

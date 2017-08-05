package com.hyperadvanced.webscript;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 09:07
 *
 * @author Ewan
 */
public class SimpleRegistry implements Registry {

    private final ConcurrentHashMap<URI, Function> store = new ConcurrentHashMap<>();

    @Override
    public void registerFunction(URI identifier, Function function) {
        store.put(identifier, function);
    }

    @Override
    public Function getFunction(URI identifier) {
        return store.get(identifier);
    }
}

package com.hyperadvanced.webscript;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public class MapFunctionResolver implements FunctionResolver {

    private final Map<FunctionSignature, Function<?,?>> store = new HashMap<>();

    public MapFunctionResolver(Map<FunctionSignature, Function<?,?>> functions) {
        store.putAll(functions);
    }

    @Override
    public Function<?, ?> resolve(FunctionSignature sig) throws FunctionResolutionException {
        final Function function = store.get(sig);
        if (function == null) throw new FunctionResolutionException(sig.getIdentifier());
        return function;
    }
}

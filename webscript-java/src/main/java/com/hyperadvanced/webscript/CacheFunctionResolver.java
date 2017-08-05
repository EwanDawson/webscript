package com.hyperadvanced.webscript;

import org.boon.cache.Cache;

import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public class CacheFunctionResolver implements FunctionResolver {

    private final Cache<FunctionSignature, Function<?, ?>> cache;
    private final FunctionResolver fallback;

    public CacheFunctionResolver(Cache<FunctionSignature, Function<?, ?>> cache, FunctionResolver fallback) {
        this.cache = cache;
        this.fallback = fallback;
    }

    @Override
    public Function<?, ?> resolve(FunctionSignature uri) throws FunctionResolutionException {
        Function<?, ?> function = cache.get(uri);
        if (function == null) {
            function = fallback.resolve(uri);
            cache.put(uri, function);
        }
        return function;
    }
}

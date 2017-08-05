package com.hyperadvanced.webscript;

import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public interface FunctionResolver {

    Function<?, ?> resolve(FunctionSignature signature) throws FunctionResolutionException;
}

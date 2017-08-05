package com.hyperadvanced.webscript;

import org.boon.di.Inject;

import java.net.URI;
import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 09:02
 *
 * @author Ewan
 */
public class RegistryLookupFunctionProvider implements FunctionProvider {

    @Inject Registry registry;

    @Override
    public <T, R> Function<T, R> get(URI identifier, Class<T> inputType, Class<R> returnType) {
//        final Function<?, ?> function = registry.getFunction(identifier);
//        if (function.inputType().equals(inputType) && function.returnType().equals(returnType)) {
//            //noinspection unchecked
//            return (Function<T, R>) function;
//        }
        throw new UnsupportedOperationException("Conversion of function input/return types not yet supported");
    }

    @Override
    public <T, R> Function<T, R> get(String identifier, Class<T> inputType, Class<R> returnType) {
        return get(URI.create(identifier), inputType, returnType);
    }
}

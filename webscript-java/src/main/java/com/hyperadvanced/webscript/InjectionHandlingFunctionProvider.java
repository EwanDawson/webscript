package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;
import org.boon.di.Context;
import org.boon.di.Inject;

import java.net.URI;
import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public class InjectionHandlingFunctionProvider implements FunctionProvider {

    private final Context context;
    @Inject private FunctionResolver resolver;
    @Inject private FunctionTypeConverter typeConverter;

    public InjectionHandlingFunctionProvider(Context context) {
        this.context = context;
    }

    @Override
    public <T, R> Function<T, R> get(URI identifier, Class<T> inputType, Class<R> returnType) throws FunctionResolutionException {
        final FunctionSignature sig = FunctionSignature.of(identifier, TypeToken.of(inputType), TypeToken.of(returnType));
        final Function<?, ?> function = resolver.resolve(sig);
        context.resolveProperties(function);
        final Function<T, R> convertedFunction = typeConverter.convert(function, inputType, returnType);
        return convertedFunction;
    }

    @Override
    public <T, R> Function<T, R> get(String identifier, Class<T> inputType, Class<R> returnType) throws FunctionResolutionException {
        return get(URI.create(identifier), inputType, returnType);
    }
}

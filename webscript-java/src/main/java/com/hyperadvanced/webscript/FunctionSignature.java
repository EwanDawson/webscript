package com.hyperadvanced.webscript;

import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;

import java.net.URI;
import java.util.Objects;

/**
 * TODO: Write Javadocs for this class.
 * Created: 30/09/2015 23:12
 *
 * @author Ewan
 */
public final class FunctionSignature<IN, OUT> {

    public static <INPUT, OUTPUT> FunctionSignature of(URI identifier, TypeToken<INPUT> inputType, TypeToken<OUTPUT> outputType) {
        return new FunctionSignature<>(identifier, inputType, outputType);
    }

    private final URI identifier;
    private final TypeToken<IN> inputType;
    private final TypeToken<OUT> outputType;

    FunctionSignature(URI identifier, TypeToken<IN> inputType, TypeToken<OUT> outputType) {
        this.identifier = identifier;
        this.inputType = inputType;
        this.outputType = outputType;
    }

    public URI getIdentifier() {
        return identifier;
    }

    public TypeToken<IN> getInputType() {
        return inputType;
    }

    public TypeToken<OUT> getOutputType() {
        return outputType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionSignature that = (FunctionSignature) o;
        return Objects.equals(identifier, that.identifier) &&
                Objects.equals(inputType, that.inputType) &&
                Objects.equals(outputType, that.outputType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, inputType, outputType);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("identifier", identifier)
                .add("inputType", inputType)
                .add("outputType", outputType)
                .toString();
    }
}

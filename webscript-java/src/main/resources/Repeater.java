package com.hyperadvanced.webscript;

import java.util.function.Function;
import com.google.common.base.Joiner;

import static com.google.common.collect.Iterables.cycle;
import static com.google.common.collect.Iterables.limit;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 09:27
 *
 * @author Ewan
 */
public class Repeater implements Function<Repeater.Input,Repeater.Output> {

    @Override
    public Output apply(Input input) {
        final Output output = new Output();
        output.value = Joiner.on("").join(limit(cycle(input.value), input.times));
        return output;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Class<Output> returnType() {
        return Output.class;
    }

    public static class Input {
        String value;
        int times;
    }

    public static class Output {
        String value;
    }
}

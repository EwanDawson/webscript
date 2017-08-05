package com.hyperadvanced.webscript;

/**
 * TODO: Write Javadocs for this class.
 * Created: 25/10/2015 16:10
 *
 * @author Ewan
 */
public final class Machine {

    public static void main(String[] args) {
        final Machine machine = new Machine();
        machine.start();
    }

    private final FunctionResolver functionResolver;
    private final TypeConverterFactory typeConverterFactory;
    private final Runner runner;

    public Machine() {
        functionResolver = new CompilingFunctionResolver();
        typeConverterFactory = new DefaultTypeConverterFactory();
        runner = new Runner();
    }

    public void start() {
        
    }
}

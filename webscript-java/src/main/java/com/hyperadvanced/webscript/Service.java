package com.hyperadvanced.webscript;

import java.util.List;
import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 01/10/2015 08:53
 *
 * @author Ewan
 */
public final class Service {

    public static NamedService named(String name) {
        return new NamedService(name);
    }

    private static class NamedService implements ServiceProvider<Void, Void> {

        private final String name;

        private NamedService(String name) {
            this.name = name;
        }


        @Override
        public Function<Void, Void> get() {
            // TODO: Implement method.
            return null;
        }

        public <IN> ObjectAcceptingService<IN> accepting(Class<IN> objectClass) {
            return new ObjectAcceptingService<>(name, objectClass);
        }

        public <IN> ListAcceptingService<IN> acceptingList(Class<IN> objectClass) {
            return new ListAcceptingService<>(name, objectClass);
        }
    }

    private static class ObjectAcceptingService<IN> implements ServiceProvider<IN, Void> {

        private final String name;
        private final Class<IN> inClass;

        private ObjectAcceptingService(String name, Class<IN> inClass) {
            this.name = name;
            this.inClass = inClass;
        }

        @Override
        public Function<IN, Void> get() {
            // TODO: Implement method.
            return null;
        }

        public <OUT> ObjectAcceptingObjectReturningService<IN, OUT> returning(Class<OUT> objectClass) {
            return new ObjectAcceptingObjectReturningService<>(name, inClass, objectClass);
        }

        public <OUT> ObjectAcceptingListReturningService<IN, OUT> returningList(Class<OUT> outClass) {
            return new ObjectAcceptingListReturningService(name, inClass, outClass);
        }
    }

    private static class ListAcceptingService<IN> implements ServiceProvider<List<IN>, Void> {

        private final String name;
        private final Class<IN> inClass;


        private ListAcceptingService(String name, Class<IN> inClass) {
            this.name = name;
            this.inClass = inClass;
        }

        @Override
        public Function<List<IN>, Void> get() {
            // TODO: Implement method.
            return null;
        }

        public <OUT> ListAcceptingObjectReturningService<IN, OUT> returning(Class<OUT> objectClass) {
            return new ListAcceptingObjectReturningService<>(name, inClass, objectClass);
        }
    }

    private static class ObjectAcceptingObjectReturningService<IN, OUT> implements ServiceProvider<IN, OUT> {

        private final String name;
        private final Class<IN> inClass;
        private final Class<OUT> outClass;

        private ObjectAcceptingObjectReturningService(String name, Class<IN> inClass, Class<OUT> outClass) {
            this.name = name;
            this.inClass = inClass;
            this.outClass = outClass;
        }

        @Override
        public Function<IN, OUT> get() {
            // TODO: Implement method.
            return null;
        }
    }

    private static class ListAcceptingObjectReturningService<IN, OUT> implements ServiceProvider<List<IN>, OUT> {

        private final String name;
        private final Class<IN> inClass;
        private final Class<OUT> outClass;

        private ListAcceptingObjectReturningService(String name, Class<IN> inClass, Class<OUT> outClass) {
            this.name = name;
            this.inClass = inClass;
            this.outClass = outClass;
        }

        @Override
        public Function<List<IN>, OUT> get() {
            // TODO: Implement method.
            return null;
        }
    }

    private static class ObjectAcceptingListReturningService<IN, OUT> implements ServiceProvider<IN, List<OUT>> {

        private final String name;
        private final Class<IN> inClass;
        private final Class<OUT> outClass;

        private ObjectAcceptingListReturningService(String name, Class<IN> inClass, Class<OUT> outClass) {
            this.name = name;
            this.inClass = inClass;
            this.outClass = outClass;
        }

        @Override
        public Function<IN, List<OUT>> get() {
            // TODO: Implement method.
            return null;
        }
    }
}

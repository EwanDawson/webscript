package com.hyperadvanced.webscript;

/**
 * TODO: Write Javadocs for this class.
 * Created: 21/09/2015 22:22
 *
 * @author Ewan
 */
public class Runner {

//    public <T, R> String run(Function<T, R> function, String jsonInput) throws Exception {
//        final ObjectMapper mapper = JsonFactory.createUseJSONDates();
//        final T input = mapper.readValue(jsonInput, function.inputType());
//        final R result = function.apply(input);
//        return mapper.writeValueAsString(result);
//    }

//    public static void main(String[] args) throws Exception {
//        final Map<URI, Function<?, ?>> functions = map(
//                URI.create("repeater"), new Repeater(),
//                URI.create("upperAndEcho"), new UserUpperAndEcho()
//        );
//
//        final Context context = context(objects(
//                new MapFunctionResolver(functions),
//                new ClassCastFunctionTypeConverter()));
//        context.add(objects(new InjectionHandlingFunctionProvider(context)));
//
//        final Runner runner = new Runner();
//        final UserUpperAndEcho.User user = new UserUpperAndEcho.User();
//        user.name = "ewan";
//        final ObjectMapper mapper = JsonFactory.createUseJSONDates();
//        final String input = mapper.toJson(user);
//        System.out.println("input = " + input);
//
//        final FunctionProvider provider = context.get(FunctionProvider.class);
//        final Function<UserUpperAndEcho.User, UserUpperAndEcho.User> upperAndEcho =
//                provider.get("upperAndEcho", UserUpperAndEcho.User.class, UserUpperAndEcho.User.class);
//        final String result = runner.run(upperAndEcho, input);
//        System.out.println("result = " + result);
//    }

}

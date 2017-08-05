package com.hyperadvanced.webscript;

import org.boon.di.Inject;

import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 09:27
 *
 * @author Ewan
 */
public class UserUpperAndEcho implements Function<UserUpperAndEcho.User,UserUpperAndEcho.User> {

    @Inject FunctionProvider function;

    @Override
    public User apply(User user) throws Exception {
        final Function<Repeater.Input, Repeater.Output> repeater = function.get("repeater", Repeater.Input.class, Repeater.Output.class);
        final Repeater.Input repeaterInput = new Repeater.Input();
        repeaterInput.value = user.name.toUpperCase();
        repeaterInput.times = 2;
        user.name = repeater.apply(repeaterInput).value;
        return user;
    }

    @Override
    public Class<User> inputType() {
        return User.class;
    }

    @Override
    public Class<User> returnType() {
        return User.class;
    }

    public static class User {
        String name;
    }

}

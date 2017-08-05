package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;
import net.openhft.compiler.CompilerUtils;

import java.util.function.Function;

import static org.boon.Exceptions.die;

/**
 * TODO: Write Javadocs for this class.
 * Created: 28/09/2015 22:52
 *
 * @author Ewan
 */
public class CompilerTest {

    @SuppressWarnings("unused")
    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        final String className = "example.StringToInteger";
        final String code =
                "package example;\n" +
                        "import java.util.function.Function;\n" +
                        "public class StringToInteger implements Function<String, Integer> {\n" +
                        "        @Override\n" +
                        "        public Integer apply(String s) {\n" +
                        "            return 1;\n" +
                        "        }\n" +
                        "    }";
        Class myClass = CompilerUtils.CACHED_COMPILER.loadFromJava(className, code);
        final Function<?, ?> function = (Function<?, ?>) myClass.newInstance();
        final TypeToken<? extends Function> typeToken = TypeToken.of(function.getClass());
        final TypeToken<?> inputType = typeToken.resolveType(Function.class.getTypeParameters()[0]);
        final TypeToken<?> returnType = typeToken.resolveType(Function.class.getTypeParameters()[1]);
        final boolean correctInputType = inputType.getRawType().equals(String.class) || die();
        final boolean correctReturnType = returnType.getRawType().equals(Integer.class) || die();
    }
}

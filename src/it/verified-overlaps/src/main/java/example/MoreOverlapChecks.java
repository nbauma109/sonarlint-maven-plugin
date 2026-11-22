package example;

import java.util.ArrayList;
import java.util.List;

public final class MoreOverlapChecks {

    public String unnecessaryString() {
        return new String("constant");
    }

    public List<String> redundantTypeArguments() {
        List<String> values = new ArrayList<String>();
        values.add("element");
        return values;
    }

    private static void acceptAll(String... names) {
        for (String name : names) {
            System.out.println(name);
        }
    }

    public void unnecessaryVarargsArray() {
        acceptAll(new String[] {"alpha", "beta"});
    }

    public void redundantVoidReturn() {
        System.out.println("done");
        return;
    }
}

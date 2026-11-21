package example;

import java.util.List;

public final class OverlapChecks extends ParentChecks {

    public String describe() {
        return "child";
    }

    public void useResource() {
        Resource resource = new Resource();
        try {
            resource.work();
        } finally {
            resource.close();
        }
    }

    static final class Resource implements java.io.Closeable {

        void work() {
            System.out.println("resource");
        }

        @Override
        public void close() {
        }
    }
}

class ParentChecks {

    public String describe() {
        return "parent";
    }
}

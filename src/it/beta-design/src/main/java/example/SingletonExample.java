package example;

final class SingletonExample {
    private static final SingletonExample INSTANCE = new SingletonExample();

    private SingletonExample() {
    }

    static SingletonExample getInstance() {
        return INSTANCE;
    }

    int value() {
        return 1;
    }
}

final class OrdinaryClass {
    int value() {
        return 1;
    }
}

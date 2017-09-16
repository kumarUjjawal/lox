package com.usemalloc;

import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable {

    final String name;

    final LoxClass superclass;

    private final Map<String,LoxFunction> methods;

    LoxClass(String name,LoxClass superclass,Map<String,LoxFunction> methods) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
    }

    LoxFunction findMethod(LoxInstance instance, String name) {
        LoxClass klass = this;
        while (klass != null) {
            if (klass.methods.containsKey(name)) {
                return klass.methods.get(name).bind(instance);
            }
            klass = klass.superclass;
        }
        return null;
    }


    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        LoxFunction initializer = methods.get("init");
        if (initializer != null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = methods.get("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter,arguments);
        }
        return instance;
    }
}

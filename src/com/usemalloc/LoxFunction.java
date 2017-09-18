package com.usemalloc;

import java.util.List;

/**
 * Created by ujjawalpathak on 05/08/17.
 */
public class LoxFunction  implements LoxCallable{
    private final Stmt.Function declaration;
    private final Environment closure;
    private boolean isInitializer;

    LoxFunction(Stmt.Function declaration,Environment closure) {
        this.closure = closure;
        this.declaration = declaration;
    }

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance self) {
        Environment environment = new Environment(closure);
        environment.define("this", self);
        return new LoxFunction(declaration, environment, isInitializer);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.parameters.size(); i++) {
            environment.define(declaration.parameters.get(i).lexeme,arguments.get(i));

        }
        try {
            interpreter.executeBlock(declaration.body,environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }

        return null;
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }






}

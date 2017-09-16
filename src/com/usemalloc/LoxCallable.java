package com.usemalloc;

import java.util.List;

/**
 * Created by ujjawalpathak on 01/08/17.
 */
interface LoxCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}

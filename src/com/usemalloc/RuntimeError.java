package com.usemalloc;

/**
 * Created by ujjawalpathak on 10/07/17.
 */

public class RuntimeError extends RuntimeException{

    final Token token;

    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }

}

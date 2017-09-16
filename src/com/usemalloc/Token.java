package com.usemalloc;

/**
 * Created by ujjawalpathak on 06/07/17.
 */

// Made Token class public.

class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line; // [location]

    Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}

package com.usemalloc;

/**
 * Created by ujjawalpathak on 06/07/17.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.usemalloc.TokenType.*;

class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private static final Map<String,TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }

    private int start = 0;
    private int current = 0;
    private int line = 1;

    /*
    The 'start' and 'current' fields are offsets in the string- the first
    character in the current lexeme being scanned and the character we're
    currently considering.

    The other field tracks what source line 'current' is one so we can
    produce tokens that know their location.

    */

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanTokens();
        }
        tokens.add(new Token(EOF,"",null,line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '!': addToken(match('=') ? BANG_EQUAL: BANG);break;
            case '=': addToken(match('=') ? EQUAL_EQUAL: EQUAL);break;
            case '<': addToken(match('=') ? LESS_EQUAL: LESS);break;
            case '>': addToken(match('=') ? GREATER_EQUAL: GREATER);break;
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;
            case '\n':
                line++;
                break;
            case '"': string(); break;
            default:
                if (isDigit(c)) {
                    number();
                } else if(isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }

    }

    // The match() method only consumes the current character if it's what we're looking for.

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // The peek() method is a lookahead.Since it only looks at the current unconsumed character, we have one character of lookahead.

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    // The string() method for the string.

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        // Unterminated String.
        if (isAtEnd()) {
            Lox.error(line,"Unterminated String.");
            return;
        }

        // The Closing.

        advance();

        // Trim the surrounding quotes.

        String value = source.substring(start + 1,current -1);
        addToken(STRING,value);

    }

    // Number Literal

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Look for fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Coonsume the "."
            advance();

            while (isDigit(peek())) advance();
        }

        addToken(NUMBER,Double.parseDouble(source.substring(start,current)));
    }

    // Identirier method

    private void identifier() {
        while (isAlphanumeric(peek())) advance();

        // See if the identifier is a reserved keyword.

        String text = source.substring(start,current);

        TokenType type = keywords.get(text);

        if (type == null) type = IDENTIFIER;

        addToken(IDENTIFIER);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';

    }

    private boolean isAlphanumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // Helper function that tells if we've consumed all the characters.

    private boolean isAtEnd() {
        return  current >= source.length();
    }

    // The advance() method consumes the nest character in the source file and returns it.

    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    // The addToken() method is for output. It grabs the test of the current lexeme and create a new token fot it.

    private void addToken(TokenType type) {
        addToken(type,null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start,current);
        tokens.add(new Token(type,text,literal,line));
    }

}


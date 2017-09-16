package com.usemalloc;

/**
 * Created by ujjawalpathak on 06/07/17.
 */

import java.beans.Expression;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.usemalloc.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /* It parses a series of statements, as many as it can find until
     it hits the end of the input.
    */

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }


    // This method is called repeatedly when parsing a series of statements in a block or script.

    private Stmt declaration() {
        try {
            if (match(FUN)) return function("function");
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER,"Expect " + kind + "name.");
        //Parse Parameters
        consume(LEFT_PAREN,"Expect '(' after" + kind + "name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 8) {
                    error(peek(),"Can not have more than 8 parameters.");
                }
                parameters.add(consume(IDENTIFIER,"Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN,"Expect ')' after parameters");
        consume(LEFT_BRACE,"Expect '{' before" + kind + "body.");
        List<Stmt> body = block();
        return new Stmt.Function(name,parameters,body);
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON,"Expect ';' after return value.");
        return new Stmt.Return(keyword,value);
    }

    // It parses the subexpression, consumes the terminating semicolon, and emits the syntax tree.

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER,"Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON,"Expect ';' after variable declaraction.");
        return new Stmt.Var(name, initializer);
    }

    // It parses and expression followed by a semicolon. It wraps that expr in a Stmt and returns it.

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON,"Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // It creates an empty list and then parses statements and adds them to the list until it reaches the end of the block marked by closing }.

    private List<Stmt> block() {
        List<Stmt> statments = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statments.add(declaration());
        }

        consume(RIGHT_BRACE,"Expect '}' after block.");
        return statments;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object,get.name,value);
            }
            error(equals,"Invalid assignment target");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr,operator,right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr,operator,right);
        }
        return expr;
    }

    // The left comparison() nonterminal in th body is translated to the first call to comparison() and store the value
    //  in local variable.

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL,EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr,operator,right);
        }
        return expr;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN,"Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition,thenBranch,elseBranch);

    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN,"Expect '(' after while.");
        Expr condition = expression();
        consume(RIGHT_PAREN,"Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition,body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN,"Expect '(' after for.");
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON,"Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN,"Expect ')' after for clauses.");
        Stmt body = statement();

        /*The increment, if there is one, executes after the body in each iteration of the loop.
        We do that by replacing the body with a little block that contains the original body
        followed by an expression statement that evaluates the increment.
        */

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body,new Stmt.Expression(increment)));
        }

        /*Next, we take the condition and the body and build the loop using a primitive
        while loop. If the condition is omitted, we jam in true to make an infinite loop.
         */

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition,body);

        /*Finally, if there is an initializer, it runs once before the entire loop.
         We do that by, again, replacing the whole statement with a block that runs
         the initializer and then executes the loop.
         */

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer,body));
        }

        return body;
    }

    // It checks to see if the current token is any of the given type if yes it consumes it and returns true otherwise
    // it returns false.
    private boolean match(TokenType... types) {
        for (TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // To parse parenthesize expression after parsing the expression it looks for the ')'.
    // It checks if the next token is of the expected type. If so it consumes it, otherwise we hit an error.

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(),message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token,message);
        return new ParseError();
    }

    // It shows the error to the user.
    // It reports an error at a given token. It also shows the location and token itself.
//    static void error(Token token,String message) {
//        if (token.type == TokenType.EOF) {
//            report(token.line,"at end",message);
//        } else {
//            report(token.line, "at'" + token.lexeme + "'",message);
//        }
//    }

    // If the current token is any of the keywords, we're probably about to start a statement.
    // It discards tokens until it think it found a statement boundry.

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }


    // It returns true if the current token is of the given type unlike match() it doesn't consumes it but just look at it.

    private boolean check(TokenType tokenType) {
        if (!isAtEnd()) return false;
        return peek().type == tokenType;
    }

    // It consumes the current token and returns it.
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    // It checks if we've run out of token to parse.
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // It returns the current token we've yet to consume.
    private Token peek() {
        tokens.get(current);
    }

    // It returns the most recently consumed token.
    private Token previous() {
        return tokens.get(current - 1);
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER,GREATER_EQUAL,LESS,LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr,operator,right);
        }
        return expr;
    }

    // Binary Operator.

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS,PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr,operator,right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH,STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr,operator,right);
        }
        return expr;
    }

    // Unary Operator.

    private Expr unary() {
        if (match(BANG,MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator,right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 8) {
                    error(peek(),"Can not have more than 8 arguments");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN,"Expect ')' after arguments.");
        return new Expr.Call(callee,paren,arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE))  return new Expr.Literal(true);
        if (match(NIL))   return new Expr.Literal(null);

        if (match(NUMBER,STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN,"Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(),"Expect expression.");

    }


}

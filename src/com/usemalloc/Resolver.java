package com.usemalloc;


import sun.jvm.hotspot.debugger.cdbg.FunctionType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private final Interpreter interpreter;
    // It keeps track to the stack of scopes currently in scope.
    // It is only used for local block scopes.
    private final Stack<Map<String,Boolean>> scopes = new Stack<>();

    // It stores the resolution information to be used when the variable or assignment expression is later executed.
    private final Map<Expr,Integer> locals = new HashMap<>();

    // It walks the tree and track whether or not the current code is inside a function declaration.
    private FunctionType currentFuction = FunctionType.NONE;

    private enum FunctionType {
        NONE,
        FUNCTION
    }

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }


    //Resolving Blocks
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(Stmt.statements);
        endScope();
        return null;
    }

    // Resolving a variable declaration.

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(Stmt.name);
            if (stmt.initializer != null) {
                resolve(stmt.initializer);
            }
            define(stmt.name);
            return null;
    }

    // It check to see if the variable is being accessed inside its own initializer.
    // If the variable exists in the current scope but its value is false, that means
    // we have declared it but not defined it yet. Hence report an error.
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        if (scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name,"Can not read local variable in its own initializer.");
        }
        resolveLocal(expr,expr.name);
        return lookUpVariable(expr.name,expr);
    }

    // It look up the resolved distance in the map. If it doesn't find the distance in the map, it must be global.
    // In that case it look it up dynamically, directly in the global environment.
    // That throws an runtime error if the variable isn't defined.
    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance,name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    // Resolving assignment Expressions.

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        resolve(expr.value);
        resolveLocal(expr, expr.name);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance,expr.name,value);
        } else {
            globals.assign(expr.name,value);
        }
        return value;
    }

    // Resolving Function Declaration.
    // It defines the name eagerly, before resolving the function's body.
    // This lets a function recursively refer to itself inside its own body.
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    // Resolving other syntax tree nodes.
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {

        if (currentFuction == FunctionType.NONE) {
            Lox.error(stmt.keyword,"Can not return from top-level code");
        }

        if (stmt.value != null) {
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr arguments: expr.arguments) {
            resolve(arguments);
        }
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    // It starts at the innermost scope and work outwards, looking in each map for a maching name.
    // If it find the variable, it tell the interpreter it has been resolved, passing in the number of
    // scopes between the current innermost scope and the scope where the variable was found,
    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() -1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr,scopes.size() - 1 - i);
                return;
            }
        }
    }

    // The resolve calls.
    // It is similar to evaluate() and execute() methods in Interpreter class.
    // It applies the Visitor pattern to the given syntax tree node.
    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    // Walks a list of statements and resolve each one.
    void resolve(List<Stmt> statements) {
        for (Stmt statement: statements) {
            resolve(statement);
        }
    }

    // Helper method for resolving function expression.
    // It creates a new scope for the body and then binds variables for each of the function's parameters.
    // Then it resolves the function body in that scope.
    private void resolveFunction(Stmt.Function function, FunctionType type) {

        // To keep track of if we're in a function and also how many we're in.
        FunctionType enclosingFuction = currentFuction;
        currentFuction = type;

        beginScope();
        for (Token param: function.parameters) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        // When the resolving the function body is done, it restores the field to that value.
        currentFuction = enclosingFuction;
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr,depth);
    }

    // This adds the variable to the innermost scope so that it shadows any
    // outer one and so that we know the variables exists. It is marked as "not ready yet"
    // by binding its name to false in the scope the map.
    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();

        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,"Variable with this name already declared in this scope");
        }

        scope.put(name.lexeme,false);
    }

    // Variables value in the scope, marked as fully initialized and available for use.
    private void define(Token name) {
        if (scopes.isEmpty()) return;
        scopes.peek().put(name.lexeme,true);
    }

    Object getAt(int distance, String name ) {
        Environment environment = this;
        for (int i = 0; i<distance; i++) {
            environment = environment.enclosing;
        }
        return environment.values.get(name);
    }

    // It walks a fixed number of environments and then stuffs the new value in that map.
    void assignAt(int distance,Token name,Object value) {
        Environment environment = this;
        for (int i=0; i<distance; i++) {
            environment = environment.enclosing;
        }
        environment.values.put(name.lexeme,value);
    }


    private void beginScope() {
        scopes.push(new HashMap<String,Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }





}

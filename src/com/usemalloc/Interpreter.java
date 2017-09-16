package com.usemalloc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.usemalloc.TokenType.*;

/**
 * Created by ujjawalpathak on 08/07/17.
 */

// It declares that it's a visitor. The return type of the visit method is Object.
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr,Integer> locals = new HashMap<>();


    Interpreter() {
        globals.define("clock",new LoxCallable() {
            @Override
                    public int arity() {
                return 0;
        }
        @Override
                public Object call(Interpreter interpreter,List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
        }
        });
    }

    // Evaluating Literals

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // Evaluating Parentheses

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // It sends the expression back into the interpreter's visitor implementation.

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    // Evaluating Unary Expression.

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator,right);
                return -(double)right;
        }
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator,"Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator,"Operands must be a number.");
    }


    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        // nil is only equal to nil.
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    // Evaluating Binary Expression.

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {

            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;

            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;

            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;

            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;

            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;

            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operands must ve two numbers or two strings.");

            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;

            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;

            case BANG_EQUAL:
                return !isEqual(left,right);

            case EQUAL_EQUAL:
                return isEqual(left,right);

        }

        return null;
    }

    // Interpreter's public API.
    /* It takes a syntax tree for an expression and evaluates it. If that
       succeeds, evaluate() returns and object for the result value.
       interpret() converts that to a string and shows it to the user.
     */

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement: statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0,text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }

    // It evaluates the inner expression using existing evaluate() method and discards the value.

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt,environment);
        //LoxFunction function = new LoxFunction(stmt);
        environment.define(stmt.name.lexeme, function);
        return null;
    }


    // Before discarding the expression's value it converts it to a string using the stringify() method and then dumps it to stdout.

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        throw new Return(value);
    }

    // If the variable has an initializer, it evaluates it.

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme,value);
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name,value);
        return value;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements,new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
       environment.define(stmt.name.lexeme,null);

        Map<String,LoxFunction> methods = new HashMap<>();
        Object superClass = null;
        if (stmt.superclass != null) {
            superClass = evaluate(stmt.superclass);
            if (!(superClass instanceof LoxClass)) {
                throw new RuntimeError(stmt.name,"Superclass must be a class.");
            }
            environment = new Environment(environment);
            environment.define("super",superClass);
        }
        for (Stmt.Function method: stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme,function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme,(LoxClass)superClass, methods);

        if (superClass != null) {
            environment = environment.enclosing;
        }

        environment.assign(stmt.name,klass);
        return null;

    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement: statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }


    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    // Logical Operator
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    // While loop
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }


    // It evaluates the expression for the callee. It is a identifier that looks up the function by its name.
    // Then it evaluates each of the argument expressions in order and stores the resulting values in a list.
    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));

            if (!(callee instanceof LoxCallable)) {
                throw new RuntimeError(expr.paren,"Can only call functions and classes.");
            }
        }
        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expect" + function.arity() + "arguments but got" + arguments.size() + ".");
        }
        return function.call(this,arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).getProperty(expr.name);
        }
        throw new RuntimeError(expr.name,"Only instances have properties.");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object value = evaluate(expr.value);
        Object object = evaluate(expr.object);

        if (object instanceof LoxInstance) {
            ((LoxInstance)object).fields.put(expr.name.lexeme,value);
            return value;
        }
        throw new RuntimeError(expr.name,"Only instances have fields.");
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superClass = (LoxClass)environment.getAt(distance,"super");

        LoxInstance receiver = superClass.findMethod(receiver,expr.method.lexeme);
        if (method == null) {
            throw new RuntimeError(expr.method,"Undefined property" + expr.method.lexeme + ".");
        }
        return method
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword,expr);
    }






}




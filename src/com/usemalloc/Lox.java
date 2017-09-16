package com.usemalloc;

/**
 * Created by ujjawalpathak on 06/07/17.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


public class Lox {

    static boolean hadError = false;

    static boolean hadRuntimeError = false;

    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox[script]");
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes,Charset.defaultCharset()));

        //Indicate an error in the exit code.
        if (hadError) System.exit(65);

        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            run(reader.readLine());
            hadError = false;
        }

    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);


        // Stop if there was a syntax error.

        if (hadError) return;

        List<Stmt> statements = parser.parse();

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        interpreter.interpret(statements);

//      System.out.println(new ASTPrinter().print(expression));

        //Print the Tokens
//        for (Token token: tokens) {
//            System.out.println(token);
//        }

    }

    //Error Handling
    // To-do
    // 1. Add capability to show where actully the error is.

    static void error(int line, String message) {
        report(line, "",message);
    }

    static private void report(int line, String where, String message) {
        System.err.println("[line" + line + "] error" + where + ": "+ message);
        hadError = true;
    }

    // It uses the token associated with the RuntimeError to tell the user what line
    // of code was executing when the error occured.

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line "+ error.token.line + "]");
        hadRuntimeError = true;
    }



}


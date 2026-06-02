package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/**
 * Exact arithmetic for the agent. LLMs are unreliable at math (and small local models even
 * mangle it), so the Brain calls this whenever a request involves a calculation — it decides
 * from context, this isn't a command. Pure, safe expression evaluation (a tiny recursive-descent
 * parser — no {@code eval}, no code execution, no I/O): {@code + - * / % ^}, parentheses, unary
 * minus, decimals, the functions sqrt/abs/round/floor/ceil/ln/log/exp/sin/cos/tan, and pi/e.
 */
@Component
@RequiredArgsConstructor
public class CalculateTool implements Tool {

    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("calculate",
                "Evaluate a mathematical expression EXACTLY. Use this for any arithmetic or math so the "
                + "answer is correct (e.g. '3*3', '1234*5678', '(2+3)^4 / 7', 'sqrt(144)', 'round(19.99*1.17, 2)'). "
                + "Supports + - * / % ^, parentheses, and sqrt/abs/round/floor/ceil/ln/log/exp/sin/cos/tan, pi, e.",
                "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\","
                + "\"description\":\"The math expression to evaluate.\"}},\"required\":[\"expression\"]}");
    }

    @Override
    public String execute(String args) {
        String expr = ToolArgs.str(mapper, args, "expression");
        if (expr == null || expr.isBlank()) {
            return "No expression provided.";
        }
        try {
            double result = new Eval(expr).parse();
            return expr.strip() + " = " + format(result);
        } catch (Exception e) {
            return "Couldn't evaluate \"" + expr + "\": " + e.getMessage();
        }
    }

    private static String format(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return String.valueOf(d);
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);   // whole number → no trailing .0
        }
        return String.valueOf(d);
    }

    /** Minimal safe recursive-descent expression evaluator (no eval / no side effects). */
    private static final class Eval {
        private final String s;
        private int pos = 0;

        Eval(String s) {
            this.s = s;
        }

        double parse() {
            double v = expr();
            skipSpaces();
            if (pos < s.length()) {
                throw new IllegalArgumentException("unexpected '" + s.charAt(pos) + "'");
            }
            return v;
        }

        private double expr() {            // + and -
            double v = term();
            while (true) {
                skipSpaces();
                if (eat('+')) {
                    v += term();
                } else if (eat('-')) {
                    v -= term();
                } else {
                    return v;
                }
            }
        }

        private double term() {            // * / %
            double v = factor();
            while (true) {
                skipSpaces();
                if (eat('*')) {
                    v *= factor();
                } else if (eat('/')) {
                    v /= factor();
                } else if (eat('%')) {
                    v %= factor();
                } else {
                    return v;
                }
            }
        }

        private double factor() {          // unary +/- and ^ (right-assoc)
            skipSpaces();
            if (eat('+')) {
                return factor();
            }
            if (eat('-')) {
                return -factor();
            }
            double base = primary();
            skipSpaces();
            if (eat('^')) {
                return Math.pow(base, factor());
            }
            return base;
        }

        private double primary() {
            skipSpaces();
            if (eat('(')) {
                double v = expr();
                skipSpaces();
                if (!eat(')')) {
                    throw new IllegalArgumentException("missing ')'");
                }
                return v;
            }
            char c = peek();
            if (Character.isLetter(c)) {
                String id = readIdent();
                skipSpaces();
                if (eat('(')) {                       // function call
                    double arg = expr();
                    skipSpaces();
                    if (!eat(')')) {
                        throw new IllegalArgumentException("missing ')' after " + id);
                    }
                    return applyFunction(id, arg);
                }
                return switch (id.toLowerCase()) {     // constant
                    case "pi" -> Math.PI;
                    case "e" -> Math.E;
                    default -> throw new IllegalArgumentException("unknown name '" + id + "'");
                };
            }
            return readNumber();
        }

        private double applyFunction(String fn, double x) {
            return switch (fn.toLowerCase()) {
                case "sqrt" -> Math.sqrt(x);
                case "abs" -> Math.abs(x);
                case "round" -> Math.rint(x);
                case "floor" -> Math.floor(x);
                case "ceil" -> Math.ceil(x);
                case "ln" -> Math.log(x);
                case "log" -> Math.log10(x);
                case "exp" -> Math.exp(x);
                case "sin" -> Math.sin(x);
                case "cos" -> Math.cos(x);
                case "tan" -> Math.tan(x);
                default -> throw new IllegalArgumentException("unknown function '" + fn + "'");
            };
        }

        private double readNumber() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("expected a number");
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private String readIdent() {
            int start = pos;
            while (pos < s.length() && Character.isLetterOrDigit(s.charAt(pos))) {
                pos++;
            }
            return s.substring(start, pos);
        }

        private void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private boolean eat(char c) {
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("unexpected end of expression");
            }
            return s.charAt(pos);
        }
    }
}

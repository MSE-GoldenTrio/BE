package com.example.iplan.util;

public class SimplePair<L, R> {
    private final L left;
    private final R right;

    public SimplePair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "(" + left + ", " + right + ")";
    }
}

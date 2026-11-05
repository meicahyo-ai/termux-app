package com.termux.app;

/**
 * Single-use approval state machine. All transitions are invoked on the UI thread.
 * Keeping state separate from Views makes duplicate-execution protection testable.
 */
final class TermuxLabActionLifecycle {
    enum State { PENDING, REVIEWING, EXECUTING, SUCCEEDED, FAILED, CANCELLED, SUPERSEDED }

    private State state = State.PENDING;

    State state() { return state; }

    boolean beginReview() {
        if (state != State.PENDING) return false;
        state = State.REVIEWING;
        return true;
    }

    boolean declineReview() {
        if (state != State.REVIEWING) return false;
        state = State.PENDING;
        return true;
    }

    boolean approve() {
        if (state != State.REVIEWING) return false;
        state = State.EXECUTING;
        return true;
    }

    boolean complete(boolean ok) {
        if (state != State.EXECUTING) return false;
        state = ok ? State.SUCCEEDED : State.FAILED;
        return true;
    }

    boolean cancel() {
        if (state != State.PENDING && state != State.REVIEWING && state != State.EXECUTING) return false;
        state = State.CANCELLED;
        return true;
    }

    boolean supersede() {
        if (state != State.PENDING && state != State.REVIEWING) return false;
        state = State.SUPERSEDED;
        return true;
    }

    boolean isTerminal() {
        return state == State.SUCCEEDED || state == State.FAILED ||
               state == State.CANCELLED || state == State.SUPERSEDED;
    }
}

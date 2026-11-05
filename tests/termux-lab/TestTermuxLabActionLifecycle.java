package com.termux.app;

/** Standalone Java tests. No Android runtime/JUnit dependencies. */
public final class TestTermuxLabActionLifecycle {
    private static int tests = 0;
    private static void check(boolean condition, String message) {
        tests++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        TermuxLabActionLifecycle success = new TermuxLabActionLifecycle();
        check(success.state() == TermuxLabActionLifecycle.State.PENDING, "initial pending");
        check(!success.approve(), "no approve before confirmation");
        check(success.beginReview(), "first dialog");
        check(!success.beginReview(), "reject duplicate dialog");
        check(success.approve(), "positive confirmation");
        check(success.state() == TermuxLabActionLifecycle.State.EXECUTING, "executing");
        check(!success.approve(), "reject duplicate execution");
        check(!success.cancel() || success.state() == TermuxLabActionLifecycle.State.CANCELLED,
                "cancel valid only if running");
        check(!success.complete(true), "cancelled must not be resurrected");
        check(success.isTerminal(), "cancel is terminal");

        TermuxLabActionLifecycle complete = new TermuxLabActionLifecycle();
        check(complete.beginReview(), "review complete");
        check(complete.approve(), "approve complete");
        check(complete.complete(true), "complete success");
        check(complete.state() == TermuxLabActionLifecycle.State.SUCCEEDED, "success state");
        check(!complete.beginReview(), "success cannot review again");
        check(!complete.approve(), "success cannot execute again");
        check(!complete.complete(true), "success cannot complete again");
        check(complete.isTerminal(), "success terminal");

        TermuxLabActionLifecycle failure = new TermuxLabActionLifecycle();
        check(failure.beginReview(), "review failure");
        check(failure.approve(), "approve failure");
        check(failure.complete(false), "complete failure");
        check(failure.state() == TermuxLabActionLifecycle.State.FAILED, "failure state");
        check(!failure.beginReview(), "failure cannot reexecute");

        TermuxLabActionLifecycle decline = new TermuxLabActionLifecycle();
        check(decline.beginReview(), "dialog open");
        check(decline.declineReview(), "dialog dismiss");
        check(decline.state() == TermuxLabActionLifecycle.State.PENDING, "return to pending");
        check(decline.beginReview(), "can review after dismiss");
        check(decline.supersede(), "old plan superseded");
        check(!decline.approve(), "superseded approval invalid");
        check(decline.isTerminal(), "superseded terminal");

        TermuxLabActionLifecycle cancelled = new TermuxLabActionLifecycle();
        check(cancelled.cancel(), "cancel pending");
        check(!cancelled.approve(), "cancelled cannot execute");
        check(!cancelled.supersede(), "cancelled cannot supersede twice");
        System.out.println("PASS=" + tests + " FAIL=0");
    }
}

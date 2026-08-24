package io.cloudchains.app.console;

/**
 * Legacy argument-menu compatibility shell.
 *
 * Secret-bearing command-line arguments are not accepted. The active launcher
 * uses {@link ConsoleMenu}, which reads secrets from stdin.
 */
public final class ArgMenu {
    public ArgMenu(String[] ignoredArguments) {
        // Retain source compatibility without retaining or inspecting values.
    }

    public void init() {
        throw new IllegalStateException(
                "The legacy argument menu is disabled; use ConsoleMenu with stdin.");
    }
}

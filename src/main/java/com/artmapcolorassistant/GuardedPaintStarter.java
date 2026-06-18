package com.artmapcolorassistant;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class GuardedPaintStarter {
    private GuardedPaintStarter() {
    }

    static boolean start(String filename, Runnable stopPainters,
                         Predicate<String> sessionStarter, BooleanSupplier modeStarter) {
        stopPainters.run();
        if (!sessionStarter.test(filename)) {
            return false;
        }
        return modeStarter.getAsBoolean();
    }
}

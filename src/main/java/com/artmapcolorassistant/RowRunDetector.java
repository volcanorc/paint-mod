package com.artmapcolorassistant;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class RowRunDetector {
    private RowRunDetector() {
    }

    public static int sameItemRowRun(List<PaintStep> steps, int startIndex, Predicate<PaintStep> allowed) {
        if (startIndex < 0 || startIndex >= steps.size()) {
            return 0;
        }
        PaintStep first = steps.get(startIndex);
        if (first.skip() || first.item() == null || !allowed.test(first)) {
            return 0;
        }
        int length = 0;
        for (int index = startIndex; index < steps.size(); index++) {
            PaintStep current = steps.get(index);
            if (current.y() != first.y()
                    || current.skip()
                    || !Objects.equals(current.item(), first.item())
                    || !allowed.test(current)) {
                break;
            }
            length++;
        }
        return length;
    }
}

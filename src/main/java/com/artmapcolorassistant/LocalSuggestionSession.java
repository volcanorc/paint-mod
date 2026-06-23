package com.artmapcolorassistant;

import java.util.List;
import java.util.Objects;

public final class LocalSuggestionSession {
    private List<CommandGuide.Completion> candidates = List.of();
    private String queryInput = "";
    private String lastApplied;
    private int selectedIndex = -1;
    private int viewportStart;

    public void sync(String input, List<CommandGuide.Completion> nextCandidates) {
        String current = input == null ? "" : input;
        if (lastApplied != null && lastApplied.equals(current) && !candidates.isEmpty()) {
            return;
        }
        List<CommandGuide.Completion> safeCandidates = nextCandidates == null ? List.of() : List.copyOf(nextCandidates);
        if (Objects.equals(queryInput, current) && candidates.equals(safeCandidates)) {
            return;
        }
        queryInput = current;
        candidates = safeCandidates;
        lastApplied = null;
        selectedIndex = -1;
        viewportStart = 0;
    }

    public CommandGuide.Completion cycle(int direction, int visibleRows) {
        if (candidates.isEmpty()) {
            return null;
        }
        int step = direction < 0 ? -1 : 1;
        int index = selectedIndex;
        for (int checked = 0; checked < candidates.size(); checked++) {
            if (index < 0) {
                index = step > 0 ? 0 : candidates.size() - 1;
            } else {
                index = Math.floorMod(index + step, candidates.size());
            }
            CommandGuide.Completion candidate = candidates.get(index);
            if (candidate.insertable()) {
                selectedIndex = index;
                keepSelectionVisible(visibleRows);
                return candidate;
            }
        }
        return null;
    }

    public CommandGuide.Completion select(int absoluteIndex, int visibleRows) {
        if (absoluteIndex < 0 || absoluteIndex >= candidates.size()) {
            return null;
        }
        CommandGuide.Completion candidate = candidates.get(absoluteIndex);
        if (!candidate.insertable()) {
            return null;
        }
        selectedIndex = absoluteIndex;
        keepSelectionVisible(visibleRows);
        return candidate;
    }

    public void markApplied(String completion) {
        lastApplied = completion;
    }

    public void reset() {
        candidates = List.of();
        queryInput = "";
        lastApplied = null;
        selectedIndex = -1;
        viewportStart = 0;
    }

    public List<CommandGuide.Completion> candidates() {
        return candidates;
    }

    public List<CommandGuide.Completion> visible(int visibleRows) {
        int count = Math.max(1, visibleRows);
        int end = Math.min(candidates.size(), viewportStart + count);
        return candidates.subList(Math.min(viewportStart, end), end);
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public int viewportStart() {
        return viewportStart;
    }

    private void keepSelectionVisible(int visibleRows) {
        int count = Math.max(1, visibleRows);
        if (selectedIndex < viewportStart) {
            viewportStart = selectedIndex;
        } else if (selectedIndex >= viewportStart + count) {
            viewportStart = selectedIndex - count + 1;
        }
        int maximumStart = Math.max(0, candidates.size() - count);
        viewportStart = Math.max(0, Math.min(viewportStart, maximumStart));
    }
}

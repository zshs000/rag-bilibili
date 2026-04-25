package com.example.subtitleeval.cleaning;

import com.example.subtitleeval.model.EvalDocument;

import java.util.ArrayList;
import java.util.List;

public class CleaningTrace {
    private final EvalDocument originalDocument;
    private final EvalDocument cleanedDocument;
    private final List<String> originalSegments;
    private final List<String> keptSegments;
    private final List<Decision> decisions;

    public CleaningTrace(EvalDocument originalDocument,
                         EvalDocument cleanedDocument,
                         List<String> originalSegments,
                         List<String> keptSegments,
                         List<Decision> decisions) {
        this.originalDocument = originalDocument;
        this.cleanedDocument = cleanedDocument;
        this.originalSegments = List.copyOf(originalSegments);
        this.keptSegments = List.copyOf(keptSegments);
        this.decisions = List.copyOf(decisions);
    }

    public EvalDocument getOriginalDocument() {
        return originalDocument;
    }

    public EvalDocument getCleanedDocument() {
        return cleanedDocument;
    }

    public List<String> getOriginalSegments() {
        return originalSegments;
    }

    public List<String> getKeptSegments() {
        return keptSegments;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public int getDroppedSegmentCount() {
        return originalSegments.size() - keptSegments.size();
    }

    public static class Decision {
        private final int lineNumber;
        private final String segment;
        private final Action action;
        private final List<String> reasons;

        public Decision(int lineNumber, String segment, Action action, List<String> reasons) {
            this.lineNumber = lineNumber;
            this.segment = segment;
            this.action = action;
            this.reasons = List.copyOf(reasons);
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getSegment() {
            return segment;
        }

        public Action getAction() {
            return action;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }

    public enum Action {
        KEEP,
        DROP,
        SKIP_AS_ALREADY_DROPPED,
        SKIP_AS_ADJACENT_DUPLICATE
    }

    public static class DecisionBuilder {
        private final int lineNumber;
        private final String segment;
        private final List<String> reasons = new ArrayList<>();
        private Action action = Action.KEEP;

        public DecisionBuilder(int lineNumber, String segment) {
            this.lineNumber = lineNumber;
            this.segment = segment;
        }

        public DecisionBuilder action(Action action) {
            this.action = action;
            return this;
        }

        public DecisionBuilder reason(String reason) {
            this.reasons.add(reason);
            return this;
        }

        public Decision build() {
            return new Decision(lineNumber, segment, action, reasons);
        }
    }
}

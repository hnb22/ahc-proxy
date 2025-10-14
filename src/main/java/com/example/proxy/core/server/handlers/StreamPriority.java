package com.example.proxy.core.server.handlers;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* 
 * Data container class for managing streams, held in HashMap in Http/2 handler
 * Data obtained from PriorityFrame request.
 */
public class StreamPriority {
    private int streamId;
    private int dependency;
    private short weight;
    private boolean exclusive;

    public StreamPriority(int streamId, int dependency, short weight, boolean exclusive) {
        this.streamId = streamId;
        this.dependency = dependency;
        this.weight = weight;
        this.exclusive = exclusive;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getDependency() {
        return dependency;
    }

    public short getWeight() {
        return weight;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public static Comparator<Integer> createComparator(Map<Integer, StreamPriority> streamPriorities) {
        return new StreamPriorityComparator(streamPriorities);
    }

    private static class StreamPriorityComparator implements Comparator<Integer> {

        private final Map<Integer, StreamPriority> streamPriorities;
        
        public StreamPriorityComparator(Map<Integer, StreamPriority> streamPriorities) {
            this.streamPriorities = streamPriorities;
        }

        @Override
        public int compare(Integer streamId1, Integer streamId2) {
            StreamPriority p1 = streamPriorities.get(streamId1);
            StreamPriority p2 = streamPriorities.get(streamId2);

            if (p1 == null && p2 == null) return 0;
            if (p1 == null) return 1;
            if (p2 == null) return -1;

            if (isDependentOn(streamId1, streamId2)) return 1;
            if (isDependentOn(streamId2, streamId1)) return -1;

            int weightComparison = Integer.compare(p2.weight, p1.weight);
            if (weightComparison != 0) return weightComparison;

            return Integer.compare(streamId1, streamId2);
        }

        private boolean isDependentOn(int streamId, int potentialParent) {
            StreamPriority priority = streamPriorities.get(streamId);
            if (priority == null || priority.dependency == 0) return false;

            if (priority.dependency == potentialParent) return true;

            Set<Integer> visited = new HashSet<>();
            int current = priority.dependency;
            while (current != 0 && !visited.contains(current)) {
            visited.add(current);
            StreamPriority currentPriority = streamPriorities.get(current);
            if (currentPriority == null) break;
            if (currentPriority.dependency == potentialParent) return true;
            current = currentPriority.dependency;
            }
            return false;
        }
    }
}

package org.tpunn.autoblade.scores;

public interface ScoreManager {
    int get();
    void set(int score);
    void add(int delta);
}

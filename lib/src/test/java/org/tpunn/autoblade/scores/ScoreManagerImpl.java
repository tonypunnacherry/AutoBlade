package org.tpunn.autoblade.scores;

import javax.inject.Inject;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Scoped;
import org.tpunn.autoblade.core.Anchor;

@Scoped
@Anchored(Anchor.PLAYER)
public class ScoreManagerImpl implements ScoreManager {
    private int score;

    @Inject
    public ScoreManagerImpl() {
        this.score = 0;
    }

    @Override
    public int get() {
        return score;
    }

    @Override
    public void set(int score) {
        this.score = score;
    }

    @Override
    public void add(int delta) {
        this.score += delta;
    }
}

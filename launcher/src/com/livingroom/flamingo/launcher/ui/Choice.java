package com.livingroom.flamingo.launcher.ui;

import java.util.List;

/** Inline chooser state for a setting: ← → move through choices, OK confirms, Back cancels. */
public final class Choice {
    public final Sections.Item item;
    private final List<String> choices;
    private int index;

    public Choice(Sections.Item item) {
        this.item = item;
        this.choices = item.choices;
        this.index = Math.max(0, choices.indexOf(item.value));
    }

    public void move(int d) { index = (index + d + choices.size()) % choices.size(); }

    public String value() { return choices.get(index); }

    /** e.g. "‹ Movie ›" */
    public String display() { return "‹ " + value() + " ›"; }
}

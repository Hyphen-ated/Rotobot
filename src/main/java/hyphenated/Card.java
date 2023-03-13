package hyphenated;

import org.jetbrains.annotations.NotNull;

public class Card implements Comparable<Card> {
    @NotNull public final String name;
    @NotNull public final String color;
    public Card(@NotNull String name, @NotNull String color) {
        this.name = name;
        this.color = color;
    }


    @Override
    public int compareTo(Card that) {
        return this.name.compareTo(that.name);
    }
}

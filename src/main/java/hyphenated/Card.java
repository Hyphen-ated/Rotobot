package hyphenated;

import org.jetbrains.annotations.NotNull;

public class Card implements Comparable<Card> {
    @NotNull public final String name;
    @NotNull public final String color;
    @NotNull public final String setCode;
    public Card(@NotNull String name, @NotNull String color, @NotNull String setCode) {
        this.name = name;
        this.color = color;
        this.setCode = setCode;
    }


    @Override
    public int compareTo(Card that) {
        return this.name.compareTo(that.name);
    }
}

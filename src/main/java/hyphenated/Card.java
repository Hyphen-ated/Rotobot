package hyphenated;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class Card implements Comparable<Card> {
    @NotNull public final String name;
    @NotNull public final String color;
    @NotNull public final String setCode;
    @NotNull public final String imageUrl;

    public Card(@NotNull String name, @NotNull String color, @NotNull String setCode, @NotNull String imageUrl) {
        this.name = name;
        this.color = color;
        this.setCode = setCode;
        this.imageUrl = imageUrl;
    }

    @Override
    public int compareTo(Card that) {
        return this.name.compareTo(that.name);
    }
}

package com.harbourpvp.ranked;

import java.util.UUID;

public record Match(Kit kit, UUID one, UUID two) {
    public boolean contains(UUID id) { return one.equals(id) || two.equals(id); }
    public UUID opponent(UUID id) { return one.equals(id) ? two : one; }
}

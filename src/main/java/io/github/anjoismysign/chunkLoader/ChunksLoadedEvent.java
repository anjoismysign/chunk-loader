package io.github.anjoismysign.chunkLoader;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ChunksLoadedEvent extends Event {

    private static final HandlerList HANDLERS_LIST = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    public ChunksLoadedEvent(){
        super(false);
    }
}

package systems.kinau.fishingbot.network.protocol.common;

import com.google.common.io.ByteArrayDataOutput;
import lombok.Getter;
import lombok.NoArgsConstructor;
import systems.kinau.fishingbot.FishingBot;
import systems.kinau.fishingbot.event.common.ResourcePackEvent;
import systems.kinau.fishingbot.network.protocol.NetworkHandler;
import systems.kinau.fishingbot.network.protocol.Packet;
import systems.kinau.fishingbot.network.utils.ByteArrayDataInputWrapper;

import java.io.IOException;

@NoArgsConstructor
public class PacketInResourcePack extends Packet {

    @Getter private String url;
    @Getter private String hash;
    @Getter private boolean forced;
    @Getter private String prompt;

    @Override
    public void write(ByteArrayDataOutput out, int protocolId) throws IOException {
        // Only incoming packet
    }

    @Override
    public void read(ByteArrayDataInputWrapper in, NetworkHandler networkHandler, int length, int protocolId) throws IOException {
        this.url = readString(in);
        this.hash = readString(in);
        this.forced = in.readBoolean();
        if (in.readBoolean())
            this.prompt = readString(in);
        FishingBot.getInstance().getCurrentBot().getEventManager().callEvent(new ResourcePackEvent(url, hash, forced, prompt));
    }
}

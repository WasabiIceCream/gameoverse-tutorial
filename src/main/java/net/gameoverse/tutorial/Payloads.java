package net.gameoverse.tutorial;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class Payloads {
    private Payloads() {
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    /**
     * Show a hint. {@code seconds} 0 keeps it up until a {@link Hide}; {@code progress} is the bar's starting share,
     * or -1 for no bar; {@code screens} are the screen classes whose opening the client should report.
     */
    public record Show(String id, String icon, List<String> keys, int seconds, float progress, List<String> screens)
        implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Show::id,
            ByteBufCodecs.STRING_UTF8, Show::icon,
            STRINGS, Show::keys,
            ByteBufCodecs.VAR_INT, Show::seconds,
            ByteBufCodecs.FLOAT, Show::progress,
            STRINGS, Show::screens,
            Show::new);

        @Override
        public Type<Show> type() {
            return TYPE;
        }
    }

    /** Update the progress bar of the hint on screen. */
    public record Progress(String id, float progress) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Progress::id,
            ByteBufCodecs.FLOAT, Progress::progress,
            Progress::new);

        @Override
        public Type<Progress> type() {
            return TYPE;
        }
    }

    /** The step is done (or the tutorial was switched off): take its hint down. */
    public record Hide(String id) implements CustomPacketPayload {
        public static final Type<Hide> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "hide"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hide> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Hide::id, Hide::new);

        @Override
        public Type<Hide> type() {
            return TYPE;
        }
    }

    /** Client to server: a screen the current step waits for has opened. */
    public record ScreenOpened(String id) implements CustomPacketPayload {
        public static final Type<ScreenOpened> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "screen_opened"));
        public static final StreamCodec<FriendlyByteBuf, ScreenOpened> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ScreenOpened::id, ScreenOpened::new);

        @Override
        public Type<ScreenOpened> type() {
            return TYPE;
        }
    }

    /** Client to server: something only the client sees happened (e.g. "f3_closed": the debug screen was closed). */
    public record ClientEvent(String name) implements CustomPacketPayload {
        public static final Type<ClientEvent> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "client_event"));
        public static final StreamCodec<FriendlyByteBuf, ClientEvent> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClientEvent::name, ClientEvent::new);

        @Override
        public Type<ClientEvent> type() {
            return TYPE;
        }
    }
}

/*
 * Copyright 2016 Johannes Donath <johannesd@torchmind.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rocks.spud.mc.discord;

import com.google.common.collect.ImmutableSet;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;

/**
 * Provides a simple implementation which manages the synchronization of messages between Minecraft
 * and Discord.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
public class ChatBridge {
    private final JDA jda;
    private final DiscordListener discordListener = new DiscordListener();
    private final MinecraftForgeListener forgeListener = new MinecraftForgeListener();

    // Bot Details
    private final Guild guild;
    private final String botId;
    private final Set<TextChannel> channels;

    // Settings
    private final boolean enableTTS;
    private final boolean ignoreBots;
    private final boolean sendAchievements;
    private final boolean sendConnects;
    private final boolean sendDisconnects;
    private final boolean sendDeaths;
    private final boolean sendMessages;

    // Patterns
    private final String minecraftMessagePattern;
    private final String discordJoinPattern;
    private final String discordPartPattern;
    private final String discordAchievementPattern;
    private final String discordDeathPattern;
    private final String discordMessagePattern;

    private ChatBridge(@Nonnull String botToken, @Nonnull String guildId, @Nonnull Set<String> channels, boolean enableTTS, boolean ignoreBots, boolean sendAchievements, boolean sendConnects, boolean sendDisconnects, boolean sendDeaths, boolean sendMessages, @Nonnull String minecraftMessagePattern, @Nonnull String discordJoinPattern, @Nonnull String discordPartPattern, @Nonnull String discordAchievementPattern, @Nonnull String discordDeathPattern, @Nonnull String discordMessagePattern) {
        this.enableTTS = enableTTS;
        this.ignoreBots = ignoreBots;
        this.minecraftMessagePattern = minecraftMessagePattern;
        this.discordJoinPattern = discordJoinPattern;
        this.discordPartPattern = discordPartPattern;
        this.discordAchievementPattern = discordAchievementPattern;
        this.discordDeathPattern = discordDeathPattern;
        this.discordMessagePattern = discordMessagePattern;
        this.sendAchievements = sendAchievements;
        this.sendConnects = sendConnects;
        this.sendDisconnects = sendDisconnects;
        this.sendDeaths = sendDeaths;
        this.sendMessages = sendMessages;

        try {
            this.jda = new JDABuilder()
                    .setBotToken(botToken)
                    .setAutoReconnect(true)
                    .setAudioEnabled(false)
                    .addListener(this.discordListener)
                    .buildBlocking();
        } catch (LoginException ex) {
            throw new IllegalArgumentException("Could not authenticate with Discord: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            throw new RuntimeException("Interrupted while awaiting Discord authentication: " + ex.getMessage(), ex);
        }

        this.botId = this.jda.getSelfInfo().getId();
        this.guild = this.jda.getGuildById(guildId);

        if (this.guild == null) {
            throw new IllegalArgumentException("No such guild: " + guildId);
        }

        // unify channel names before attempting to locate them within the Guild
        final Set<String> unifiedChannels = channels.stream()
                .map((c) -> (c.startsWith("#") ? c.substring(1) : c).toLowerCase())
                .collect(Collectors.toSet());

        // cache a list of known channels
        this.channels = Collections.unmodifiableSet(
                this.guild.getTextChannels().stream()
                        .filter((c) -> unifiedChannels.contains(c.getName()))
                        .collect(Collectors.toSet())
        );
    }

    /**
     * Creates a new bridge factory.
     *
     * @return a factory.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Dispatches a message to the Discord server.
     *
     * @param message a message.
     */
    public void sendMessage(@Nonnull MessageBuilder builder) {
        builder.setTTS(this.enableTTS);
        final Message message = builder.build();

        this.channels.forEach((c) -> c.sendMessageAsync(message, new Consumer<Message>() {
            @Override
            public void accept(Message message) {
            }
        }));
    }

    /**
     * Sends a message to the Discord server.
     *
     * @param sender  a sender.
     * @param message a message.
     */
    public void sendMessage(@Nonnull ICommandSender sender, @Nonnull String message) {
        // TODO: Add support for mentions
        this.sendMessage(new MessageBuilder().appendFormat(this.discordMessagePattern, sender.getDisplayName().getUnformattedText(), message));
    }

    /**
     * Provides a factory for chat bridges.
     */
    public static class Builder {
        // Bot Settings
        private Set<String> channels = new HashSet<>();

        // Settings
        private boolean enableTTS = false;
        private boolean ignoreBots = false;
        private boolean sendAchievements = true;
        private boolean sendConnects = true;
        private boolean sendDisconnects = true;
        private boolean sendDeaths = true;
        private boolean sendMessages = true;

        // Patterns
        private String minecraftMessagePattern = "<%1$s@Discord> %2$s";
        private String discordJoinPattern = ":space_invader: %1$s has entered the server";
        private String discordPartPattern = ":broken_heart: %1$s has left the server";
        private String discordAchievementPattern = ":trophy: %1$s has just earned the achievement [%2$s]";
        private String discordDeathPattern = ":skull_crossbones: %2$s";
        private String discordMessagePattern = ":speech_balloon: <%1$s> %2$s";

        private Builder() {
        }

        /**
         * Builds a new chat bridge instance.
         *
         * @param botToken a bot token.
         * @param guildId  a guild identifier.
         * @return a chat bridge instance.
         *
         * @throws IllegalArgumentException when the provided login details are invalid.
         */
        @Nonnull
        public ChatBridge build(@Nonnull String botToken, @Nonnull String guildId) {
            return new ChatBridge(botToken, guildId, ImmutableSet.copyOf(this.channels), this.enableTTS, this.ignoreBots, this.sendAchievements, this.sendConnects, this.sendDisconnects, this.sendDeaths, this.sendMessages, this.minecraftMessagePattern, this.discordJoinPattern, this.discordPartPattern, this.discordAchievementPattern, this.discordDeathPattern, this.discordMessagePattern);
        }

        /**
         * Adds a channel to the bridge.
         *
         * @param channel a channel.
         * @return a reference to this builder.
         */
        @Nonnull
        public Builder addChannel(@Nonnull String channel) {
            if (channel.startsWith("#")) {
                channel = channel.substring(1);
            }

            this.channels.add(channel.toLowerCase());
            return this;
        }

        /**
         * Adds a collection of channels to the bridge.
         *
         * @param channels a collection.
         * @return a reference to this builder.
         */
        @Nonnull
        public Builder addChannel(@Nonnull Collection<String> channels) {
            channels.forEach(this::addChannel);
            return this;
        }

        /**
         * Adds an array of channels to the bridge.
         *
         * @param channels an array.
         * @return a reference to this builder.
         */
        @Nonnull
        public Builder addChannel(@Nonnull String[] channels) {
            for (String channel : channels) {
                this.addChannel(channel);
            }

            return this;
        }

        public boolean enableTTS() {
            return this.enableTTS;
        }

        @Nonnull
        public Builder enableTTS(boolean enableTTS) {
            this.enableTTS = enableTTS;
            return this;
        }

        public boolean ignoreBots() {
            return this.ignoreBots;
        }

        @Nonnull
        public Builder ignoreBots(boolean ignoreBots) {
            this.ignoreBots = ignoreBots;
            return this;
        }

        public boolean sendAchievements() {
            return this.sendAchievements;
        }

        @Nonnull
        public Builder sendAchievements(boolean sendAchievements) {
            this.sendAchievements = sendAchievements;
            return this;
        }

        public boolean sendConnects() {
            return this.sendConnects;
        }

        @Nonnull
        public Builder sendConnects(boolean sendConnects) {
            this.sendConnects = sendConnects;
            return this;
        }

        public boolean sendDisconnects() {
            return this.sendDisconnects;
        }

        @Nonnull
        public Builder sendDisconnects(boolean sendDisconnects) {
            this.sendDisconnects = sendDisconnects;
            return this;
        }

        public boolean sendDeaths() {
            return this.sendDeaths;
        }

        @Nonnull
        public Builder sendDeaths(boolean sendDeaths) {
            this.sendDeaths = sendDeaths;
            return this;
        }

        public boolean sendMessages() {
            return this.sendMessages;
        }

        @Nonnull
        public Builder sendMessages(boolean sendMessages) {
            this.sendMessages = sendMessages;
            return this;
        }

        @Nonnull
        public String minecraftMessagePattern() {
            return this.minecraftMessagePattern;
        }

        @Nonnull
        public Builder minecraftMessagePattern(@Nonnull String minecraftMessagePattern) {
            this.minecraftMessagePattern = minecraftMessagePattern;
            return this;
        }

        @Nonnull
        public String discordJoinPattern() {
            return this.discordJoinPattern;
        }

        @Nonnull
        public Builder discordJoinPattern(@Nonnull String discordJoinPattern) {
            this.discordJoinPattern = discordJoinPattern;
            return this;
        }

        @Nonnull
        public String discordPartPattern() {
            return this.discordPartPattern;
        }

        @Nonnull
        public Builder discordPartPattern(@Nonnull String discordPartPattern) {
            this.discordPartPattern = discordPartPattern;
            return this;
        }

        @Nonnull
        public String discordAchievementPattern() {
            return this.discordAchievementPattern;
        }

        @Nonnull
        public Builder discordAchievementPattern(@Nonnull String discordAchievementPattern) {
            this.discordAchievementPattern = discordAchievementPattern;
            return this;
        }

        @Nonnull
        public String discordDeathPattern() {
            return this.discordDeathPattern;
        }

        @Nonnull
        public Builder discordDeathPattern(@Nonnull String discordDeathPattern) {
            this.discordDeathPattern = discordDeathPattern;
            return this;
        }

        @Nonnull
        public String discordMessagePattern() {
            return this.discordMessagePattern;
        }

        @Nonnull
        public Builder discordMessagePattern(@Nonnull String discordMessagePattern) {
            this.discordMessagePattern = discordMessagePattern;
            return this;
        }
    }

    /**
     * Provides a basic listener implementation which is capable of forwarding messages from Discord
     * to Minecraft.
     */
    private class DiscordListener extends ListenerAdapter {
        private final AtomicBoolean hooked = new AtomicBoolean(false);

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReady(@Nonnull ReadyEvent event) {
            if (this.hooked.compareAndSet(false, true)) {
                MinecraftForge.EVENT_BUS.register(forgeListener);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
            if (event.getAuthor().isBot() && ignoreBots) {
                return;
            }

            if (event.getAuthor().getId().equals(botId)) {
                return;
            }

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

            if (server != null) {
                server.callFromMainThread((Callable<Object>) () -> {
                    ITextComponent component = new TextComponentString(String.format(minecraftMessagePattern, (event.getAuthorNick() != null ? event.getAuthorNick() : event.getAuthorName()), event.getMessage().getStrippedContent()));
                    server.getPlayerList().sendChatMsg(component);
                    return component;
                });
            }
        }
    }

    /**
     * Provides a basic listener implementation which is capable of forwarding Minecraft messages to
     * Discord.
     */
    private class MinecraftForgeListener {

        /**
         * Handles all achievements that are received on the server and forwards them to Discord.
         *
         * @param event an event.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onAchievement(@Nonnull AchievementEvent event) {
            if (!sendAchievements) {
                return;
            }

            EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();

            if (player.getStatFile().hasAchievementUnlocked(event.getAchievement()) || !player.getStatFile().canUnlockAchievement(event.getAchievement())) {
                return;
            }

            sendMessage(new MessageBuilder().appendFormat(discordAchievementPattern, event.getEntityPlayer().getDisplayName().getUnformattedText(), event.getAchievement().getStatName().getUnformattedText()));
        }

        /**
         * Handles all player deaths and forwards them to Discord.
         *
         * @param event an event.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onLivingDeath(@Nonnull LivingDeathEvent event) {
            if (!sendDeaths) {
                return;
            }

            if (!(event.getEntity() instanceof EntityPlayer)) {
                return;
            }

            EntityPlayer player = (EntityPlayer) event.getEntity();
            sendMessage(new MessageBuilder().appendFormat(discordDeathPattern, player.getDisplayName().getUnformattedText(), player.getCombatTracker().getDeathMessage().getUnformattedText()));
        }

        /**
         * Handles all player messages.
         *
         * @param event an event.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onServerChat(@Nonnull ServerChatEvent event) {
            if (!sendMessages) {
                return;
            }

            sendMessage(event.getPlayer(), event.getMessage());
        }

        /**
         * Handles player logins.
         *
         * @param event an event.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent event) {
            if (!sendConnects) {
                return;
            }

            sendMessage(new MessageBuilder().appendFormat(discordJoinPattern, event.player.getDisplayName().getUnformattedText()));
        }

        /**
         * Handles player disconnects.
         *
         * @param event an event.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
            if (!sendDisconnects) {
                return;
            }

            sendMessage(new MessageBuilder().appendFormat(discordPartPattern, event.player.getDisplayName().getUnformattedText()));
        }
    }
}

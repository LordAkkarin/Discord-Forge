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

import net.dv8tion.jda.MessageBuilder;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * Provides a basic modification which forwards all chat traffic between Discord and Minecraft.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mod(modid = "discord", serverSideOnly = true, acceptableRemoteVersions = "*")
public class DiscordMod {
    private ChatBridge bridge;
    private Configuration configuration;

    @Mod.EventHandler
    public void onPreInitialization(@Nonnull FMLPreInitializationEvent event) {
        this.configuration = new Configuration(event.getSuggestedConfigurationFile(), "0.1.0");
    }

    @Mod.EventHandler
    public void onInitialization(@Nonnull FMLInitializationEvent event) {
        final String botToken;
        final String guildId;

        ChatBridge.Builder builder = ChatBridge.builder();

        // Authentication Settings
        {
            Property property = this.configuration.get("authentication", "botToken", "");
            property.setComment("Specifies the bot's access token in order to authenticate with the Discord API (check your application credentials to retrieve this information).");
            property.setValidationPattern(Pattern.compile("^[A-Za-z0-9/+=]+.[A-Za-z0-9/+=]+.[A-Za-z0-9/+=]$"));

            botToken = property.getString();
        }
        {
            Property property = this.configuration.get("authentication", "guildId", "0000");
            property.setComment("Specifies the Guild (Server) to retrieve messages from and send messages back to (check your server's widget settings to retrieve this information).");
            property.setValidationPattern(Pattern.compile("^\\d+$"));

            guildId = property.getString();
        }

        // Bridge Settings
        {
            Property property = this.configuration.get("bridge", "channels", "");
            property.setComment("Specifies a list of channels the bot will respond to.");

            builder.addChannel(property.getStringList());
        }
        {
            Property property = this.configuration.get("bridge", "tts", builder.enableTTS());
            property.setComment("Enables or disables text-to-speech (TTS) for all messages.");

            builder.enableTTS(property.getBoolean());
        }
        {
            Property property = this.configuration.get("bridge", "ignoreBots", builder.ignoreBots());
            property.setComment("Indicates whether bot messages shall be ignored.");

            builder.ignoreBots(property.getBoolean());
        }

        // Message Types
        {
            Property property = this.configuration.get("messages", "achievements", builder.sendAchievements());
            property.setComment("Enables or disables the bridging of achievement messages.");

            builder.sendAchievements(property.getBoolean());
        }
        {
            Property property = this.configuration.get("messages", "connects", builder.sendConnects());
            property.setComment("Enables or disables the bridging of connect messages.");

            builder.sendConnects(property.getBoolean());
        }
        {
            Property property = this.configuration.get("messages", "disconnects", builder.sendDisconnects());
            property.setComment("Enables or disables the bridging of disconnect messages.");

            builder.sendConnects(property.getBoolean());
        }
        {
            Property property = this.configuration.get("messages", "deaths", builder.sendDeaths());
            property.setComment("Enables or disables the bridging of death messages.");

            builder.sendConnects(property.getBoolean());
        }
        {
            Property property = this.configuration.get("messages", "chat", builder.sendMessages());
            property.setComment("Enables or disables the bridging of chat messages.");

            builder.sendMessages(property.getBoolean());
        }

        // Formats
        {
            Property property = this.configuration.get("formats", "minecraftChat", builder.minecraftMessagePattern());
            builder.minecraftMessagePattern(property.getString());
        }
        {
            Property property = this.configuration.get("formats", "discordJoin", builder.discordJoinPattern());
            builder.discordJoinPattern(property.getString());
        }
        {
            Property property = this.configuration.get("formats", "discordPart", builder.discordPartPattern());
            builder.discordPartPattern(property.getString());
        }
        {
            Property property = this.configuration.get("formats", "discordAchievement", builder.discordAchievementPattern());
            builder.discordAchievementPattern(property.getString());
        }
        {
            Property property = this.configuration.get("formats", "discordDeath", builder.discordDeathPattern());
            builder.discordDeathPattern(property.getString());
        }
        {
            Property property = this.configuration.get("formats", "discordMessage", builder.discordMessagePattern());
            builder.discordMessagePattern(property.getString());
        }

        if (!botToken.isEmpty() && !guildId.isEmpty()) {
            this.bridge = builder.build(botToken, guildId);
        }
    }

    @Mod.EventHandler
    public void onPostInitialization(@Nonnull FMLPostInitializationEvent event) {
        this.configuration.save();

        if (this.bridge != null) {
            // TODO: Add an option for this?
            this.bridge.sendMessage(new MessageBuilder().appendFormat("The server is now back online."));
        }
    }
}

/*
 * Copyright (c) 2026, jumpshot7 <https://github.com/jumpshot7>

 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

@Slf4j
@PluginDescriptor(
	name = "Word of the Day",
		description = "A plugin that provides a daily word of the day."
)
public class WordOfTheDayPlugin extends Plugin
{
	@Inject
	private WordOfTheDayConfig config;

	@Inject
	private Gson gson;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	private boolean loggingIn;
	private boolean pendingLogin;
	List<WordEntry> words;
	private List<WordEntry> shuffledWords;

	@Provides
	WordOfTheDayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WordOfTheDayConfig.class);
	}

	@Override
	protected void startUp()
	{
		try(InputStream inputStream = getClass().getResourceAsStream("/words.json");
		    InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8))
		{
			words = gson.fromJson(reader, new TypeToken<List<WordEntry>>(){}.getType());
			log.debug("Loaded {} words", words.size());
			initShuffledWords();
		}
		catch (Exception e)
		{
			log.error("Failed to load words", e);
		}
		loggingIn = true;
	}

	@Override
	protected void shutDown()
	{

	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// LOGGED_IN also fires on region load and world hops, so we only
		// treat it as a real login if we passed through LOGGING_IN first
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (pendingLogin)
			{
				loggingIn = true;
				pendingLogin = false;
			}
		}

		if (event.getGameState() == GameState.LOGGING_IN)
		{
			pendingLogin = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (loggingIn)
		{
			loggingIn = false;
			checkWordOfDay();
		}
	}
	// create a method that calls the Collecton.shuffle to shuffle a new list called shuffledWords
	private void initShuffledWords(){
		shuffledWords = new ArrayList<>(words);
		Collections.shuffle(shuffledWords, new Random(1234567891L));
	}

	public void checkWordOfDay()
	{
		// create todayDate to store local datetime as a string.
		String todayDate = LocalDate.now().toString();
		if (config.showOncePerDay())
		{
			String storedValue = configManager.getConfiguration("wordoftheday", "lastShownDate");
			if (todayDate.equals(storedValue))
			{
				return;
			}
		}
		// index will be date time passed into epoch day (1/1/1970) mod the size of shuffledWords.
		// result with be a number x through the size of shuffledWords
		int index = (int)(LocalDate.now().toEpochDay() % shuffledWords.size());

		// grab the word using the index
		WordEntry entry = shuffledWords.get(index);

		String formattedWord = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("Word of The Day: ").append(Color.ORANGE, entry.word).append(ChatColorType.NORMAL).append(" (").append(entry.pos.toLowerCase()).append(") - ").append(entry.definition).build();

		// display the word of the day
		sendChatMessage(formattedWord);

		String formattedExample = new ChatMessageBuilder().append(ChatColorType.NORMAL).append("Example: \"").append(entry.example).append("\"").build();

		// display the example of the word
		sendChatMessage(formattedExample);

		if (config.showOncePerDay())
		{
			configManager.setConfiguration("wordoftheday", "lastShownDate", todayDate);
		}
	}

	private void sendChatMessage(String chatMessage){
		chatMessageManager.queue(
				QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(chatMessage)
						.build());
	}
}

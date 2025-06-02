package geminitranslator;

import mindustry.mod.*;
import mindustry.game.EventType.*;
import mindustry.gen.Player;
import mindustry.Vars;
import arc.Events;

// yummy ai
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

public class ChatTranslator extends Plugin {

    private Client geminiClient;
    private final String GEMINI_API_KEY = "GEMINI_API_KEY";
    private final String MODEL_NAME = "gemini-2.0-flash-001";

    private final float TEMPERATURE = 0.4f;
    private final String SYSTEM_INSTRUCTION =
        "You are a highly specialized translation AI. Your sole task is to translate the user's input text into English.\n" +
        "Your response MUST follow these rules strictly:\n" +
        "1. If the input text is already in English, your entire response MUST be the exact string 'ENGLISH_INPUT_SKIP'. Do not add any other text or explanation.\n" +
        "2. If the input text is NOT in English, translate it to English. Your response MUST be ONLY the English translation, followed by a space, and then the detected ISO 639-1 language code of the original input text enclosed in square brackets (e.g., 'Translated text [fr]').\n" +
        "3. Do not include any pleasantries, apologies, or any text other than the direct translation + language code, or 'ENGLISH_INPUT_SKIP'.\n" +
        "Example for non-English input 'Bonjour le monde': Hello world [fr]\n" +
        "Example for English input 'Hello world': ENGLISH_INPUT_SKIP";

    private GenerateContentConfig translationConfig;

    @Override
    public void init() {
        try {
            if (GEMINI_API_KEY.equals("GEMINI_API_KEY") || GEMINI_API_KEY.isEmpty()) {
                geminiClient = null;
            } else {
                geminiClient = Client.builder().apiKey(GEMINI_API_KEY).build();

                Content systemInstructionContent = Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION));
                translationConfig = GenerateContentConfig.builder()
                                        .systemInstruction(systemInstructionContent)
                                        .temperature(TEMPERATURE)
                                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            geminiClient = null;
        }

        Events.on(PlayerChatEvent.class, event -> {
            if (event.player == null) {
                return;
            }

            Player player = event.player;
            String originalMessage = event.message;

            if (originalMessage == null || originalMessage.trim().isEmpty()) {
                return;
            }

            String playerName = player.name();

            if (geminiClient != null && translationConfig != null) {
                translateMessage(playerName, event.player, originalMessage);
            }
        });

    }

    private void translateMessage(String playerName, Player player, String messageToTranslate) {
        try {
            geminiClient.async.models.generateContent(MODEL_NAME, messageToTranslate, translationConfig)
                .thenAccept(response -> {
                    String geminiResponseText = response.text().trim();

                    if ("ENGLISH_INPUT_SKIP".equalsIgnoreCase(geminiResponseText)) {
                    } else if (geminiResponseText.isEmpty()) {
                    }
                    else {
                        Vars.player.sendMessage("tr - [[[#" + player.color().toString().substring(0, 6) + "]" + playerName + "[white]] " + geminiResponseText);
                    }
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
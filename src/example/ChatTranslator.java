package geminitranslator;

import arc.Core;
import arc.Events;
import arc.scene.style.TextureRegionDrawable;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Player;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog;

// yummy ai
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;

import static arc.Core.bundle;

public class ChatTranslator extends Mod {

    private Client geminiClient;
    private GenerateContentConfig translationConfig;

    private static final float FIXED_TEMPERATURE = 0.4f;

    @Override
    public void init() {
        Vars.ui.settings.addCategory(bundle.get("gemini.settings.title"), new TextureRegionDrawable(Core.atlas.find("gemini-translator-frog")), table -> {
            table.checkPref("gemini-translator-enabled", true);
            table.areaTextPref("gemini-api-key", "", s -> loadClient());
            table.textPref("gemini-model-name", "gemini-2.0-flash", s -> loadClient()); //https://ai.google.dev/gemini-api/docs/models
        });

        loadClient();

        Events.on(PlayerChatEvent.class, event -> {
            if (!Core.settings.getBool("gemini-translator-enabled", true)) {
                return;
            }

            if (event.player == null) {
                return;
            }

            if (geminiClient == null || translationConfig == null) {
                return;
            }

            String originalMessage = event.message;
            if (originalMessage == null || originalMessage.trim().isEmpty()) {
                return;
            }

            translateMessage(event.player, originalMessage);
        });
    }

    private void loadClient() {
        String apiKey = Core.settings.getString("gemini-api-key", "");
        if (apiKey.isEmpty()) {
            Log.warn("[GeminiTranslator] API key is empty. Translation disabled until configured.");
            geminiClient = null;
            return;
        }

        try {
            geminiClient = Client.builder().apiKey(apiKey).build();

            String systemInstructionText = getDefaultInstruction();
            Content systemInstructionContent = Content.fromParts(Part.fromText(systemInstructionText));

            translationConfig = GenerateContentConfig.builder()
                    .systemInstruction(systemInstructionContent)
                    .temperature(FIXED_TEMPERATURE)
                    .build();
            Log.info("[GeminiTranslator] Client loaded successfully.");
        } catch (Exception e) {
            Log.err("[GeminiTranslator] Failed to initialize client. Check if API key is valid or has access to the models.", e);
            geminiClient = null;
            if (Vars.player != null) {
                 Vars.player.sendMessage("[scarlet]Gemini Translator: Failed to initialize client. Check API key/settings.");
            }
        }
    }

    private void translateMessage(Player player, String messageToTranslate) {
        try {
            String modelName = Core.settings.getString("gemini-model-name", "gemini-2.0-flash");

            geminiClient.async.models.generateContent(modelName, messageToTranslate, translationConfig)
                    .thenAccept(response -> {
                        String geminiResponseText = response.text().trim();

                        if (!"INPUT_SKIP".equalsIgnoreCase(geminiResponseText) && !geminiResponseText.isEmpty()) {
                            String coloredPlayerName = "[#" + player.color().toString().substring(0, 6) + "]" + player.name() + "[]";
                            Vars.player.sendMessage("[#b5b5b5]tr - [white][[" + coloredPlayerName + "[white]]: " + geminiResponseText);
                        }
                    })
                    .exceptionally(ex -> {
                        Log.err("[GeminiTranslator] API error during translation for message: \"" + messageToTranslate + "\"", ex);
                        if (Vars.player != null) {
                             Vars.player.sendMessage("[scarlet]Gemini Translator: API error during translation. Check if API key is valid or has access to the models.");
                        }
                        return null;
                    });
        } catch (Exception e) {
            Log.err("[GeminiTranslator] Unexpected error when starting translation request for message: \"" + messageToTranslate + "\"", e);
            if (Vars.player != null) {
                Vars.player.sendMessage("[scarlet]Gemini Translator: Unexpected error.");
            }
        }
    }

    private String getDefaultInstruction() {
        return "You are a highly specialized translation AI. Your sole task is to translate the user's input text into English.\n" +
               "Your response MUST follow these rules strictly:\n" +
               "1. If the input text is already in English, your entire response MUST be the exact string 'INPUT_SKIP'. Do not add any other text or explanation.\n" +
               "2. If the input text is NOT in English, translate it to English. Your response MUST be ONLY the English translation, followed by a space, and then the detected ISO 639-1 language code of the original input text enclosed in square brackets (e.g., 'Translated text [fr]').\n" +
               "3. Do not include any pleasantries, apologies, or any text other than the direct translation + language code, or 'INPUT_SKIP'.\n" +
               "4. Do not try to translate(if it is in the target language) typos, slangs, or grammar issues just say 'INPUT_SKIP'.\n" +
               "Example for non-English input 'Bonjour le monde': Hello world [fr]\n" +
               "Example for English input 'Hello world': INPUT_SKIP";
    }
}
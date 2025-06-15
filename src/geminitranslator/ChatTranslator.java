package geminitranslator;

import arc.Core;
import arc.Events;
import arc.scene.style.TextureRegionDrawable;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
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
    private static final int MAX_RETRIES = 3;

    @Override
    public void init() {
        Vars.ui.settings.addCategory(bundle.get("gemini.settings.title"), new TextureRegionDrawable(Core.atlas.find("gemini-translator-frog")), table -> {
            table.checkPref("gemini-translator-enabled", true);
            table.areaTextPref("gemini-api-key", "", s -> loadClient());
            table.textPref("gemini-model-name", "gemini-2.0-flash-lite", s -> loadClient()); // https://ai.google.dev/gemini-api/docs/models
            table.row(); // will do for now
            table.button(bundle.get("gemini.settings.getapikey"), () -> {
                Core.app.openURI("https://aistudio.google.com/apikey");
            }).width(180f).pad(6f);
        });

        loadClient();

        Events.on(EventType.PlayerJoin.class, event -> {
            if (Core.settings.getBool("gemini-translator-enabled", true) &&
                Core.settings.getString("gemini-api-key", "").isEmpty()) {

                if (Vars.player != null) {
                    Vars.player.sendMessage(bundle.get("gemini.warning.noapikey"));
                }
            }
        });

        Events.on(EventType.PlayerChatEvent.class, event -> {
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
            translateWithRetries(player, messageToTranslate, modelName, 1);
        } catch (Exception e) {
            Log.err("[GeminiTranslator] Unexpected synchronous error when starting translation request for message: \"" + messageToTranslate + "\"", e);
            if (Vars.player != null) {
                Vars.player.sendMessage("[scarlet]Gemini Translator: Unexpected error.");
            }
        }
    }

    private void translateWithRetries(Player player, String messageToTranslate, String modelName, int attemptNumber) {
        geminiClient.async.models.generateContent(modelName, messageToTranslate, translationConfig)
        .thenAccept(response -> {
                    String geminiResponseText = response.text();

                    if (geminiResponseText == null) {
                        Log.warn("[GeminiTranslator] Received a null text response from API. Skipping message.");
                        return;
                    }

                    String trimmedText = geminiResponseText.trim();

                    if (trimmedText.isEmpty() || "INPUT_SKIP".equalsIgnoreCase(trimmedText)) {
                        return;
                    }

                    String coloredPlayerName = "[#" + player.color().toString().substring(0, 6) + "]" + player.name() + "[]";
                    Vars.player.sendMessage("[#b5b5b5]tr - [white][[" + coloredPlayerName + "[white]]: " + trimmedText);
                })
                .exceptionally(ex -> {
                    if (attemptNumber < MAX_RETRIES) {
                        Log.warn("[GeminiTranslator] API call failed on attempt " + attemptNumber + ". Retrying in 1 second...");
                        Time.runTask(1f, () -> {
                            translateWithRetries(player, messageToTranslate, modelName, attemptNumber + 1);
                        });
                    } else {
                        Log.err("[GeminiTranslator] API error after " + MAX_RETRIES + " attempts for message: \"" + messageToTranslate + "\"", ex);
                        if (Vars.player != null) {
                             Vars.player.sendMessage("[scarlet]Gemini Translator: API error after multiple retries. Check if API key is valid or has access to the models.");
                        }
                    }
                    return null;
                });
    }

    private String getDefaultInstruction() {
        return bundle.get("gemini.system.instruction");
    }
}
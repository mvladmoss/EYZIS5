package com.eyzis;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TelegramBot extends TelegramLongPollingCommandBot {

    private static final String INCORRECT_MESSAGE = "Please enter /french or /english command and then record your voice";

    private final String name = System.getProperty("telegram.access.name");
    private final String token = System.getProperty("telegram.access.token");
    private final Map<Language, String> languageToCodeMap;
    private String currentLanguageCode;

    public TelegramBot(DefaultBotOptions botOptions) {
        super(botOptions, true);
        this.languageToCodeMap = new HashMap<>();
        languageToCodeMap.put(Language.ENGLISH, "en-US");
        languageToCodeMap.put(Language.FRENCH, "fr-FR");
    }

    @Override
    public String getBotUsername() {
        return name;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @SneakyThrows
    @Override
    public void processNonCommandUpdate(Update update) {
        if (!update.hasMessage()) {
            throw new IllegalStateException("Update doesn't have a body!");
        }

        Message message = update.getMessage();
        String text = message.getText();
        if (!StringUtils.isEmpty(text) && Language.getValues().contains(text)) {
            this.currentLanguageCode = languageToCodeMap.get(Language.getFromValue(text));
            SendMessage answer = new SendMessage();
            answer.setText("Bot is ready to accept " + Language.getFromValue(text).name().toLowerCase() + " speech");
            answer.setChatId(message.getChatId());
            execute(answer);
        } else if (!StringUtils.isEmpty(text) && !Language.getValues().contains(text)) {
            SendMessage answer = new SendMessage();
            answer.setText(INCORRECT_MESSAGE);
            answer.setChatId(message.getChatId());
            execute(answer);
        } else if(currentLanguageCode != null) {
            GetFile getFile = new GetFile().setFileId(update.getMessage().getVoice().getFileId());
            String filePath = execute(getFile).getFilePath();
            File file = downloadFile(filePath, new File("message.ogg"));
            File fileToTranslate = encodeToWav(file);
            try {
                String convertedText = translate(fileToTranslate.getAbsolutePath(), currentLanguageCode);
                SendMessage answer = new SendMessage();
                answer.setText(convertedText);
                answer.setChatId(message.getChatId());
                execute(answer);
            } finally {
                file.delete();
                fileToTranslate.delete();
            }
        } else {
            SendMessage answer = new SendMessage();
            answer.setText(INCORRECT_MESSAGE);
            answer.setChatId(message.getChatId());
            execute(answer);
        }
    }

    @SneakyThrows
    public String translate(String filePath, String languageCode) {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(ServiceAccountCredentials
                .fromStream(new FileInputStream(ResourceUtils.getFile("classpath:elkcoursework-7b780cdc02af.json"))));
        SpeechSettings speechSettings = SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();

        try (SpeechClient speechClient = SpeechClient.create(speechSettings)) {

            // The language of the supplied audio
            RecognitionConfig config =
                    RecognitionConfig.newBuilder().setModel("default")
                            .setAudioChannelCount(1)
                            .setLanguageCode(languageCode).build();
            Path path = Paths.get(filePath);
            byte[] data = Files.readAllBytes(path);
            ByteString content = ByteString.copyFrom(data);
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(content).build();
            RecognizeRequest request =
                    RecognizeRequest.newBuilder().setConfig(config).setAudio(audio).build();
            RecognizeResponse response = speechClient.recognize(request);
            for (SpeechRecognitionResult result : response.getResultsList()) {
                // First alternative is the most probable result
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                System.out.println(alternative.getTranscript());
                return alternative.getTranscript();
            }
        } catch (Exception exception) {
            System.err.println("Failed to create the client due to: " + exception);
        }
        return "Your voice couldn't be recognized";
    }

    private File encodeToWav(File mp3) {
        try {
            String outputFileName = mp3.getAbsolutePath().replace("ogg", "wav");
            ProcessBuilder process = new ProcessBuilder("ffmpeg/bin/ffmpeg.exe", "-i",
                    mp3.getAbsolutePath(), "-ar", "16000", "-ac", "1", outputFileName);
            process.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT);
            process.start().waitFor();
            Files.deleteIfExists(mp3.toPath());
            log.info("File {} is deleted and converted to {}", mp3.getName(), outputFileName);
            return new File(outputFileName);
        } catch (InterruptedException | IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

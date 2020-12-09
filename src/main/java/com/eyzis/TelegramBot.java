package com.eyzis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
public class TelegramBot extends TelegramLongPollingCommandBot {

    private String name = System.getProperty("telegram.access.name");

    private String token = System.getProperty("telegram.access.token");

    public TelegramBot(DefaultBotOptions botOptions) {
        super(botOptions, true);
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

        var message = update.getMessage();
        GetFile getFile = new GetFile().setFileId(update.getMessage().getVoice().getFileId());
        String filePath = execute(getFile).getFilePath();
        File file = downloadFile(filePath, new File("/Users/vladmoss/Desktop/LanguageRecognizer/message.ogg"));
        encodeToWav(file);
    }

    private File encodeToWav(File mp3) {
        try {
            var outputFileName = mp3.getAbsolutePath().replace("ogg", "wav");
            var process = new ProcessBuilder("ffmpeg", "-i", mp3.getAbsolutePath(), "-ar", "16000", "-ac", "1", outputFileName);
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

package com.example.bybitbotai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * Klasa pomocnicza do odtwarzania dźwięków
 */
@Component
@Slf4j
public class SoundUtils {

    private static boolean soundEnabled = true;
    private static float volume = 0.8f;

    private static String buySound = "success";
    private static String sellSound = "alert";
    private static String waitSound = "notification";

    private static boolean onAnalysisSound = true;
    private static boolean onOrderSound = true;
    private static boolean onErrorSound = true;

    @Value("${sound.enabled:true}")
    public void setSoundEnabled(boolean enabled) {
        SoundUtils.soundEnabled = enabled;
    }

    @Value("${sound.volume:0.8}")
    public void setVolume(float volume) {
        SoundUtils.volume = volume;
    }

    @Value("${sound.decision.buy:success}")
    public void setBuySound(String sound) {
        SoundUtils.buySound = sound;
    }

    @Value("${sound.decision.sell:alert}")
    public void setSellSound(String sound) {
        SoundUtils.sellSound = sound;
    }

    @Value("${sound.decision.wait:notification}")
    public void setWaitSound(String sound) {
        SoundUtils.waitSound = sound;
    }

    @Value("${sound.on-analysis:true}")
    public void setOnAnalysisSound(boolean enabled) {
        SoundUtils.onAnalysisSound = enabled;
    }

    @Value("${sound.on-order:true}")
    public void setOnOrderSound(boolean enabled) {
        SoundUtils.onOrderSound = enabled;
    }

    @Value("${sound.on-error:true}")
    public void setOnErrorSound(boolean enabled) {
        SoundUtils.onErrorSound = enabled;
    }

    /**
     * Odtwarza dźwięk dla decyzji tradingowej
     *
     * @param decision Decyzja (BUY, SELL, WAIT)
     */
    public static void playDecisionSound(String decision) {
        if (!soundEnabled || !onAnalysisSound) {
            return;
        }

        switch (decision.toUpperCase()) {
            case "BUY":
                playSound(buySound);
                break;
            case "SELL":
                playSound(sellSound);
                break;
            case "WAIT":
            default:
                playSound(waitSound);
                break;
        }
    }

    /**
     * Odtwarza dźwięk dla zlecenia
     */
    public static void playOrderSound() {
        if (soundEnabled && onOrderSound) {
            playSound("success");
        }
    }

    /**
     * Odtwarza dźwięk błędu
     */
    public static void playErrorSound() {
        if (soundEnabled && onErrorSound) {
            playSound("error");
        }
    }

    /**
     * Odtwarza dźwięk powiadomienia
     *
     * @param soundType Typ dźwięku: "notification", "alert", "success", "error"
     */
    public static void playSound(String soundType) {
        if (!soundEnabled) {
            return;
        }

        try {
            // Wybierz odpowiedni plik dźwiękowy
            String soundFile;
            switch (soundType.toLowerCase()) {
                case "alert":
                    soundFile = "/sounds/alert.wav";
                    break;
                case "success":
                    soundFile = "/sounds/success.wav";
                    break;
                case "error":
                    soundFile = "/sounds/error.wav";
                    break;
                case "notification":
                default:
                    soundFile = "/sounds/notification.wav";
                    break;
            }

            // Otwórz strumień do pliku dźwiękowego
            try (InputStream inputStream = SoundUtils.class.getResourceAsStream(soundFile)) {
                if (inputStream == null) {
                    log.warn("Nie znaleziono pliku dźwiękowego: {}", soundFile);
                    // Użyj wbudowanego dźwięku systemowego jako fallback
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                // Tworzenie AudioInputStream
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputStream);

                // Pobierz format dźwięku
                AudioFormat format = audioInputStream.getFormat();

                // Utwórz DataLine.Info
                DataLine.Info info = new DataLine.Info(Clip.class, format);

                // Sprawdź, czy system obsługuje ten format
                if (!AudioSystem.isLineSupported(info)) {
                    log.warn("Nie obsługiwany format dźwięku: {}", format);
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                // Utwórz i otwórz Clip
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(audioInputStream);

                // Ustaw głośność, jeśli obsługiwane
                try {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log10(volume) * 20.0f);
                    gainControl.setValue(dB);
                } catch (IllegalArgumentException e) {
                    log.debug("Nie można ustawić głośności: {}", e.getMessage());
                }

                // Dodaj LineListener, aby zwolnić zasoby po zakończeniu odtwarzania
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

                // Odtwórz dźwięk
                clip.start();
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            log.error("Błąd podczas odtwarzania dźwięku: {}", e.getMessage());
            // Fallback - użyj systemowego beep
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Odtwarza prosty dźwięk beep używając systemowego beep
     */
    public static void beep() {
        if (soundEnabled) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
}
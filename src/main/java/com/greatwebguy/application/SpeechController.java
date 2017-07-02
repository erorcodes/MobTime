package com.greatwebguy.application;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javazoom.jl.player.Player;

public class SpeechController {
	private static boolean doingSomething = false;
	public static String speechProvider = "https://api.wit.ai/message?v=20170622&q=";


	private boolean record = false;

	@FXML
	private Button speechButton;

	@FXML
	private Label recordingLabel;
	private float sampleRate = 16000;
	private int sampleSizeInBits = 8;
	private int channels = 1;
	private float level;


	@FXML
	private void startRecording(MouseEvent event) {
		if (!doingSomething) {
			try {
				playBoop();
			} catch (Exception e) {
				e.printStackTrace();
			}
			CompletableFuture.supplyAsync(() -> getAudio());
		}

	}

	private Void getAudio() {
		record = true;
		doingSomething = true;
		TargetDataLine line = null;

		AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, true, false);
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		try {
			line = (TargetDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
			int bufferSize = (int) format.getSampleRate() * format.getFrameSize();
			byte buffer[] = new byte[bufferSize];
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			boolean signalStop = false;
			int numberOfLoops = 0;
			while (record) {
				int count = line.read(buffer, 0, buffer.length);
				calculateLevel(buffer, 0, 0, format);
				System.out.println("Level: " + level);
				if (!signalStop) {
					if (numberOfLoops > 1 && level < .04) {
						signalStop = true;
						record = false;
					}
				}

				if (count > 0) {
					out.write(buffer, 0, count);
				}
				numberOfLoops++;
			}
			System.out.println("Stopping");

			byte audio[] = out.toByteArray();
			InputStream input = new ByteArrayInputStream(audio);
			AudioInputStream ais = new AudioInputStream(input, format, audio.length / format.getFrameSize());
			out.close();
			// temporarily save file
			AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;
			File wavFile = new File("mob-audio.wav");
			AudioSystem.write(ais, fileType, wavFile);
			callSpeechApi(wavFile);
		} catch (Exception e) {
			System.out.println("failed to record");
			doingSomething = false;
			record = false;
		} finally {
			if (line != null) {
				line.stop();
				line.close();
			}
		}
		return null;
	}

	private void callSpeechApi(File audiofile) {
		doingSomething = true;
		Platform.runLater(() -> recordingLabel.setText("Processing"));
		HttpURLConnection urlConnection = null;
		try {
			String contentDisposition = "Content-Disposition: form-data; name=\"audio\"; filename=\""
					+ audiofile.getName() + "\"";
			String contentType = "Content-Type: audio/wav";
			String BOUNDARY = "*****";
			String CRLF = "\r\n";
			StringBuffer requestBody = new StringBuffer();
			requestBody.append("--");
			requestBody.append(BOUNDARY);
			requestBody.append(CRLF);
			requestBody.append(contentDisposition);
			requestBody.append(CRLF);
			requestBody.append(contentType);
			requestBody.append(CRLF);
			requestBody.append(CRLF);

			StringBuffer requestBodyClose = new StringBuffer();
			requestBodyClose.append(CRLF);
			requestBodyClose.append("--");
			requestBodyClose.append(BOUNDARY);
			requestBodyClose.append("--");

			URL url = new URL(speechProvider + "");
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setUseCaches(false);
			urlConnection.setRequestMethod("POST");
			urlConnection.setRequestProperty("Content-Length", String.valueOf(audiofile.length()));
			urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
			urlConnection.setRequestProperty("Cache-Control", "no-cache");
			urlConnection.setRequestProperty("Accept", "audio/mp3;audio/wav");
			urlConnection.setRequestProperty("Authorization",
					String.format("Bearer %s", "HIY55XYXO2QUODW533SY5XBH5WYOMK3J"));

			DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
			wr.writeBytes(requestBody.toString());
			wr.flush();
			try (final FileInputStream inputStream = new FileInputStream(audiofile);) {
				final byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					wr.write(buffer, 0, bytesRead);
				}
				wr.flush();
			}
			wr.writeBytes(requestBodyClose.toString());
			wr.flush();
			wr.close();
			if (audiofile.exists()) {
				audiofile.delete();
			}
			if (urlConnection.getResponseCode() == 200) {
				InputStream in = new BufferedInputStream(urlConnection.getInputStream());
				String headerField = urlConnection.getHeaderField("Content-Type");
				playAudio(in, headerField);
				in.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
	}

	private void playAudio(InputStream in, String format) throws Exception, IOException {
		if ("audio/wav".equals(format)) {
			playWav(in);
		} else {
			playMp3(in);
		}
		doingSomething = false;
	}

	Boolean isByte = new Boolean(false);

	private void playMp3(InputStream stream) throws IOException {
		try {
			Player playMP3;
			playMP3 = new Player(stream);
			playMP3.play();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
	}

	public void playWav(InputStream stream) throws Exception {
		SourceDataLine sourceLine;
		int BUFFER_SIZE = 128000;
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(stream);

		AudioFormat audioFormat = audioInputStream.getFormat();

		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		sourceLine = (SourceDataLine) AudioSystem.getLine(info);
		sourceLine.open(audioFormat);

		sourceLine.start();

		int nBytesRead = 0;
		byte[] abData = new byte[BUFFER_SIZE];
		while (nBytesRead != -1) {
			try {
				nBytesRead = audioInputStream.read(abData, 0, abData.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (nBytesRead >= 0) {
				@SuppressWarnings("unused")
				int nBytesWritten = sourceLine.write(abData, 0, nBytesRead);
			}
		}

		sourceLine.drain();
		sourceLine.close();
	}

	private void calculateLevel(byte[] buffer, int readPoint, int leftOver, AudioFormat format) {
		int max = 0;
		for (int i = readPoint; i < buffer.length - leftOver; i += 2) {
			int value = 0;
			int hiByte = buffer[i + 1];
			int loByte = buffer[i];
			short shortVal = (short) hiByte;
			shortVal = (short) ((shortVal << 8) | (byte) loByte);
			value = shortVal;
			max = Math.max(max, value);
		}
		level = (float) max / Short.MAX_VALUE;
	}

	private void playBoop() throws Exception {
		playAudio(getClass().getResourceAsStream("boop.mp3"), "audio/mp3");
	}

}

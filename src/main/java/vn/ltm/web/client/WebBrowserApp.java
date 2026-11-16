package vn.ltm.web.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/** JavaFX browser and media workspace for the HTTP lab. */
public final class WebBrowserApp extends Application {
    private final TextField address = new TextField("http://192.168.1.100:8080/");
    private final Label status = new Label("READY");
    private final Label httpStatus = new Label("HTTP —");
    private final javafx.scene.web.WebView web = new javafx.scene.web.WebView();
    private final javafx.scene.web.WebEngine engine = web.getEngine();
    private final ListView<String> history = new ListView<>();
    private final TextArea inspector = new TextArea();
    private final List<String> visited = new ArrayList<>();
    private final TabPane tabs = new TabPane();
    private final Tab pdfTab = new Tab("PDF Viewer");
    private final Tab audioTab = new Tab("Audio Player");
    private final Tab videoTab = new Tab("Video Player");
    private final ImageView pdfImage = new ImageView();
    private final Label pdfPageLabel = new Label("Chưa mở tài liệu");
    private final Slider audioProgress = new Slider();
    private final Label audioTime = new Label("00:00 / 00:00");
    private final Slider videoProgress = new Slider();
    private final Label videoTime = new Label("00:00 / 00:00");
    private final MediaView videoView = new MediaView();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8)).build();

    private String base = "http://192.168.1.100:8080";
    private Path localPdf;
    private int pdfPageCount;
    private int pdfPageIndex;
    private int pdfRenderSequence;
    private MediaPlayer audioPlayer;
    private MediaPlayer videoPlayer;
    private boolean audioLoaded;
    private boolean videoLoaded;

    @Override
    public void start(Stage stage) {
        loadConfig();
        tabs.getTabs().addAll(createBrowserTab(), createPdfTab(), createAudioTab(), createVideoTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == pdfTab && localPdf == null) loadPdf();
            if (newTab == audioTab && !audioLoaded) loadAudio();
            if (newTab == videoTab && !videoLoaded) loadVideo();
        });

        VBox shell = new VBox(12, header(), toolbar(), tabs, statusBar());
        VBox.setVgrow(tabs, Priority.ALWAYS);
        shell.setPadding(new Insets(16));
        BorderPane root = new BorderPane(shell);
        root.setStyle("-fx-background-color: #f3f6fb;");

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("LTM Explorer · Lab 04 — HTTP Browser & Media");
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();

        engine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                address.setText(engine.getLocation());
                status.setText("Trang đã tải");
                httpStatus.setText("WebView · Loaded");
                addHistory(engine.getLocation());
            } else if (newState == Worker.State.FAILED) {
                status.setText("Không tải được trang");
                httpStatus.setText("WebView · Error");
                inspector.setText("Không thể tải: " + engine.getLocation());
            }
        });
        Platform.runLater(this::navigate);
    }

    private void loadConfig() {
        try {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(Path.of("config", "client.properties"))) {
                properties.load(input);
            }
            String host = properties.getProperty("server.host", "192.168.1.100").trim();
            int port = Integer.parseInt(properties.getProperty("web.port", "8080").trim());
            base = "http://" + host + ":" + port;
            address.setText(base + "/");
        } catch (Exception ignored) {
            // The address field remains editable if config/client.properties is absent.
        }
    }

    private HBox header() {
        VBox title = new VBox(3, new Label("LTM Explorer"), new Label("WEB WORKSPACE · LAB 04"));
        title.getChildren().get(0).getStyleClass().add("title");
        title.getChildren().get(1).getStyleClass().add("subtitle");
        Label badge = new Label("HTTP/1.1 · PDF · AUDIO · VIDEO");
        badge.getStyleClass().add("badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, title, spacer, badge);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private HBox toolbar() {
        Button back = new Button("←");
        back.setTooltip(new javafx.scene.control.Tooltip("Quay lại"));
        back.setOnAction(event -> {
            if (engine.getHistory().getCurrentIndex() > 0) engine.getHistory().go(-1);
        });
        Button forward = new Button("→");
        forward.setTooltip(new javafx.scene.control.Tooltip("Tiến tới"));
        forward.setOnAction(event -> {
            if (engine.getHistory().getCurrentIndex() < engine.getHistory().getEntries().size() - 1) {
                engine.getHistory().go(1);
            }
        });
        Button reload = new Button("↻");
        reload.setOnAction(event -> engine.reload());
        Button home = new Button("⌂");
        home.setOnAction(event -> {
            address.setText(base + "/");
            navigate();
        });
        address.setPromptText("Nhập địa chỉ HTTP Server…");
        HBox.setHgrow(address, Priority.ALWAYS);
        address.setOnAction(event -> navigate());
        Button go = new Button("Truy cập");
        go.getStyleClass().add("button-primary");
        go.setOnAction(event -> navigate());

        Button pdf = new Button("PDF");
        pdf.setOnAction(event -> tabs.getSelectionModel().select(pdfTab));
        Button audio = new Button("MP3");
        audio.setOnAction(event -> tabs.getSelectionModel().select(audioTab));
        Button video = new Button("MP4");
        video.setOnAction(event -> tabs.getSelectionModel().select(videoTab));
        MenuButton download = new MenuButton("↓ Tải tệp");
        for (String name : List.of("document.pdf", "music.mp3", "video.mp4", "sample.txt")) {
            MenuItem item = new MenuItem(name);
            item.setOnAction(event -> downloadFile(name));
            download.getItems().add(item);
        }
        HBox bar = new HBox(7, back, forward, reload, home, address, go,
                new Separator(javafx.geometry.Orientation.VERTICAL), pdf, audio, video, download);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("card");
        return bar;
    }

    private Tab createBrowserTab() {
        Tab tab = new Tab("Web Browser");
        VBox browser = new VBox(web);
        browser.getStyleClass().add("card");
        VBox.setVgrow(web, Priority.ALWAYS);
        web.setMinHeight(500);
        HBox.setHgrow(browser, Priority.ALWAYS);
        VBox side = inspectorPanel();
        side.setPrefWidth(290);
        HBox layout = new HBox(12, browser, side);
        tab.setContent(layout);
        return tab;
    }

    private VBox inspectorPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");
        Label title = new Label("Network inspector");
        title.getStyleClass().add("card-title");
        Label methods = new Label("HTTP METHODS");
        methods.getStyleClass().add("subtitle");
        Button get = actionButton("GET · Lấy dữ liệu", () -> request("GET"));
        Button head = actionButton("HEAD · Kiểm tra header", () -> request("HEAD"));
        Button post = actionButton("POST · Gửi dữ liệu thử", () -> request("POST"));
        Button upload = actionButton("POST · Upload tệp", this::uploadFile);
        Label historyTitle = new Label("LỊCH SỬ TRUY CẬP");
        historyTitle.getStyleClass().add("subtitle");
        history.setPrefHeight(210);
        history.setPlaceholder(new Label("Chưa có lịch sử"));
        history.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && history.getSelectionModel().getSelectedItem() != null) {
                address.setText(history.getSelectionModel().getSelectedItem());
                navigate();
            }
        });
        inspector.setEditable(false);
        inspector.setWrapText(true);
        inspector.setPrefRowCount(8);
        inspector.setPromptText("Response status / headers / body preview");
        VBox.setVgrow(inspector, Priority.ALWAYS);
        panel.getChildren().addAll(title, methods, get, head, post, upload, new Separator(),
                historyTitle, history, new Separator(), new Label("RESPONSE DETAIL"), inspector);
        return panel;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Tab createPdfTab() {
        pdfTab.setClosable(false);
        Button open = new Button("Mở document.pdf");
        open.getStyleClass().add("button-primary");
        open.setOnAction(event -> loadPdf());
        Button previous = new Button("← Trang trước");
        previous.setOnAction(event -> showPdfPage(pdfPageIndex - 1));
        Button next = new Button("Trang tiếp →");
        next.setOnAction(event -> showPdfPage(pdfPageIndex + 1));
        Button download = new Button("↓ Tải PDF");
        download.setOnAction(event -> downloadFile("document.pdf"));
        HBox controls = new HBox(9, open, previous, next, pdfPageLabel, new Region(), download);
        HBox.setHgrow(controls.getChildren().get(4), Priority.ALWAYS);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("card");
        pdfImage.setPreserveRatio(true);
        pdfImage.setFitWidth(980);
        StackPane page = new StackPane(pdfImage);
        page.setPadding(new Insets(18));
        page.setStyle("-fx-background-color: #e9eef6;");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        VBox layout = new VBox(10, controls, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        layout.setPadding(new Insets(12));
        pdfTab.setContent(layout);
        return pdfTab;
    }

    private Tab createAudioTab() {
        audioTab.setClosable(false);
        Label artwork = new Label("♫");
        artwork.getStyleClass().add("media-artwork");
        Label title = new Label("LTM Network Lab · Sample Music");
        title.getStyleClass().add("title");
        Label description = new Label("MP3 sample · streamed over HTTP from the Server PC");
        description.getStyleClass().add("subtitle");
        audioProgress.setMin(0);
        audioProgress.setMax(1);
        audioProgress.setValue(0);
        audioProgress.setOnMouseReleased(event -> seek(audioPlayer, audioProgress));
        Button play = new Button("▶ Phát");
        play.getStyleClass().add("button-primary");
        play.setOnAction(event -> {
            if (audioPlayer == null) loadAudio();
            if (audioPlayer != null) audioPlayer.play();
        });
        Button pause = new Button("Ⅱ Tạm dừng");
        pause.setOnAction(event -> { if (audioPlayer != null) audioPlayer.pause(); });
        Button stop = new Button("■ Dừng");
        stop.setOnAction(event -> { if (audioPlayer != null) audioPlayer.stop(); });
        Button download = new Button("↓ Tải MP3");
        download.setOnAction(event -> downloadFile("music.mp3"));
        VBox card = new VBox(16, artwork, title, description, audioProgress, audioTime,
                new HBox(8, play, pause, stop, download));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(760);
        card.getStyleClass().add("card");
        StackPane center = new StackPane(card);
        center.setPadding(new Insets(26));
        audioTab.setContent(center);
        return audioTab;
    }

    private Tab createVideoTab() {
        videoTab.setClosable(false);
        videoView.setPreserveRatio(true);
        videoView.setFitWidth(980);
        videoView.setFitHeight(540);
        StackPane videoFrame = new StackPane(videoView);
        videoFrame.setMinHeight(400);
        videoFrame.setStyle("-fx-background-color: #101b30; -fx-background-radius: 16px;");
        Button play = new Button("▶ Phát");
        play.getStyleClass().add("button-primary");
        play.setOnAction(event -> {
            if (videoPlayer == null) loadVideo();
            if (videoPlayer != null) videoPlayer.play();
        });
        Button pause = new Button("Ⅱ Tạm dừng");
        pause.setOnAction(event -> { if (videoPlayer != null) videoPlayer.pause(); });
        Button stop = new Button("■ Dừng");
        stop.setOnAction(event -> { if (videoPlayer != null) videoPlayer.stop(); });
        Button download = new Button("↓ Tải MP4");
        download.setOnAction(event -> downloadFile("video.mp4"));
        videoProgress.setMin(0);
        videoProgress.setMax(1);
        videoProgress.setOnMouseReleased(event -> seek(videoPlayer, videoProgress));
        HBox controls = new HBox(8, play, pause, stop, videoProgress, videoTime, download);
        HBox.setHgrow(videoProgress, Priority.ALWAYS);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox layout = new VBox(12, videoFrame, controls);
        layout.getStyleClass().add("card");
        layout.setPadding(new Insets(18));
        videoTab.setContent(layout);
        return videoTab;
    }

    private HBox statusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, status, spacer, httpStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card");
        return bar;
    }

    private void navigate() {
        String url = address.getText().trim();
        if (url.isBlank()) return;
        if (!url.matches("(?i)^https?://.*")) url = "http://" + url;
        try {
            URI uri = URI.create(url);
            base = uri.getScheme() + "://" + uri.getAuthority();
            address.setText(url);
            status.setText("Đang kết nối…");
            engine.load(url);
        } catch (Exception exception) {
            status.setText("URL không hợp lệ");
            inspector.setText(exception.getMessage());
        }
    }

    private void request(String method) {
        String url = method.equals("POST") ? base + "/api/login" : address.getText().trim();
        if (url.isBlank()) return;
        status.setText("Đang gửi HTTP " + method + "…");
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(12));
                if (method.equals("POST")) {
                    builder.header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"username\":\"student\",\"password\":\"demo\"}", StandardCharsets.UTF_8));
                } else if (method.equals("HEAD")) {
                    builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.GET();
                }
                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                String body = new String(response.body(), StandardCharsets.UTF_8);
                String preview = body.substring(0, Math.min(1000, body.length()));
                String result = method + " " + url + "\nStatus: " + response.statusCode()
                        + "\nHeaders:\n" + response.headers().map() + "\n\nBody preview:\n" + preview;
                Platform.runLater(() -> {
                    inspector.setText(result);
                    httpStatus.setText("HTTP · " + response.statusCode());
                    status.setText("Request hoàn tất");
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    status.setText("HTTP request thất bại");
                    inspector.setText("Request failed: " + exception.getMessage());
                });
            }
        });
    }

    private void uploadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp upload lên Web Server");
        java.io.File file = chooser.showOpenDialog(address.getScene().getWindow());
        if (file == null) return;
        String url = base + "/api/upload";
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("X-File-Name", file.getName())
                        .timeout(java.time.Duration.ofMinutes(2))
                        .POST(HttpRequest.BodyPublishers.ofFile(file.toPath())).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                Platform.runLater(() -> {
                    httpStatus.setText("HTTP · " + response.statusCode());
                    inspector.setText("POST " + url + "\nFile: " + file.getName() + "\n" + response.body());
                    status.setText("Upload hoàn tất");
                });
            } catch (Exception exception) {
                Platform.runLater(() -> inspector.setText("Upload failed: " + exception.getMessage()));
            }
        });
    }

    private void downloadFile(String fileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu tệp tải về");
        chooser.setInitialFileName(fileName);
        java.io.File target = chooser.showSaveDialog(address.getScene().getWindow());
        if (target == null) return;
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String url = base + "/download/" + encodedName;
        status.setText("Đang tải " + fileName + "…");
        CompletableFuture.runAsync(() -> {
            try {
                Path destination = target.toPath();
                if (destination.getParent() != null) Files.createDirectories(destination.getParent());
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofMinutes(2)).GET().build();
                HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Files.deleteIfExists(destination);
                    throw new IOException("HTTP " + response.statusCode());
                }
                Platform.runLater(() -> {
                    httpStatus.setText("HTTP · " + response.statusCode());
                    status.setText("Đã tải " + fileName);
                    inspector.setText("GET " + url + "\nHTTP " + response.statusCode()
                            + "\nSaved: " + destination.toAbsolutePath() + "\nBytes: " + safeSize(destination));
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    status.setText("Download thất bại");
                    inspector.setText("Download failed: " + exception.getMessage());
                    notice(exception.getMessage());
                });
            }
        });
    }

    private long safeSize(Path file) {
        try { return Files.size(file); } catch (IOException ignored) { return -1; }
    }

    private void loadPdf() {
        if (localPdf != null && Files.exists(localPdf)) {
            tabs.getSelectionModel().select(pdfTab);
            if (pdfImage.getImage() == null) showPdfPage(pdfPageIndex);
            return;
        }
        status.setText("Đang tải PDF từ Server…");
        String url = base + "/files/document.pdf";
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) throw new IOException("Server trả HTTP " + response.statusCode());
                Path cache = Path.of("target", "preview-cache");
                Files.createDirectories(cache);
                Path file = cache.resolve("document.pdf");
                Files.write(file, response.body());
                int pages;
                try (PDDocument document = Loader.loadPDF(file.toFile())) { pages = document.getNumberOfPages(); }
                if (pages < 1) throw new IOException("PDF không có trang nào");
                return new PdfInfo(file, pages);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).thenAccept(info -> Platform.runLater(() -> {
            localPdf = info.file();
            pdfPageCount = info.pages();
            pdfPageIndex = 0;
            tabs.getSelectionModel().select(pdfTab);
            showPdfPage(0);
            status.setText("PDF đã tải · " + pdfPageCount + " trang");
        })).exceptionally(exception -> {
            Platform.runLater(() -> {
                status.setText("Không mở được PDF");
                notice("PDF viewer: " + rootMessage(exception));
            });
            return null;
        });
    }

    private void showPdfPage(int index) {
        if (localPdf == null || index < 0 || index >= pdfPageCount) return;
        pdfPageIndex = index;
        int sequence = ++pdfRenderSequence;
        pdfPageLabel.setText("Đang render trang " + (index + 1) + "/" + pdfPageCount + "…");
        CompletableFuture.supplyAsync(() -> {
            try (PDDocument document = Loader.loadPDF(localPdf.toFile())) {
                BufferedImage page = new PDFRenderer(document).renderImageWithDPI(index, 110);
                Path imageFile = Path.of("target", "preview-cache", "pdf-page-" + (index + 1) + ".png");
                ImageIO.write(page, "png", imageFile.toFile());
                return imageFile;
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).thenAccept(imageFile -> Platform.runLater(() -> {
            if (sequence != pdfRenderSequence) return;
            pdfImage.setImage(new Image(imageFile.toUri().toString(), false));
            pdfPageLabel.setText("Trang " + (index + 1) + " / " + pdfPageCount);
            status.setText("PDF · Page " + (index + 1));
        })).exceptionally(exception -> {
            Platform.runLater(() -> notice("Không render được trang PDF: " + rootMessage(exception)));
            return null;
        });
    }

    private void loadAudio() {
        disposePlayer(audioPlayer);
        audioPlayer = null;
        audioLoaded = true;
        status.setText("Đang kết nối MP3 stream…");
        try {
            Media media = new Media(base + "/files/music.mp3");
            audioPlayer = new MediaPlayer(media);
            MediaPlayer player = audioPlayer;
            bindProgress(player, audioProgress, audioTime);
            player.setOnReady(() -> {
                status.setText("MP3 sẵn sàng · " + formatDuration(player.getTotalDuration()));
                audioTime.setText("00:00 / " + formatDuration(player.getTotalDuration()));
            });
            player.setOnError(() -> mediaError("Audio", player.getError()));
            player.setOnEndOfMedia(player::stop);
        } catch (Exception exception) {
            audioLoaded = false;
            mediaError("Audio", exception);
        }
    }

    private void loadVideo() {
        disposePlayer(videoPlayer);
        videoPlayer = null;
        videoLoaded = true;
        status.setText("Đang kết nối MP4 stream…");
        try {
            Media media = new Media(base + "/files/video.mp4");
            videoPlayer = new MediaPlayer(media);
            MediaPlayer player = videoPlayer;
            videoView.setMediaPlayer(player);
            bindProgress(player, videoProgress, videoTime);
            player.setOnReady(() -> status.setText("MP4 sẵn sàng · " + formatDuration(player.getTotalDuration())));
            player.setOnError(() -> mediaError("Video", player.getError()));
            player.setOnEndOfMedia(player::stop);
        } catch (Exception exception) {
            videoLoaded = false;
            mediaError("Video", exception);
        }
    }

    private void bindProgress(MediaPlayer player, Slider slider, Label timeLabel) {
        player.currentTimeProperty().addListener((observable, oldTime, currentTime) -> {
            Duration total = player.getTotalDuration();
            if (total != null && !total.isUnknown() && total.toSeconds() > 0) {
                slider.setMax(total.toSeconds());
                if (!slider.isValueChanging()) slider.setValue(currentTime.toSeconds());
                timeLabel.setText(formatDuration(currentTime) + " / " + formatDuration(total));
            }
        });
        slider.setOnMouseReleased(event -> seek(player, slider));
    }

    private void seek(MediaPlayer player, Slider slider) {
        if (player != null && player.getTotalDuration() != null && !player.getTotalDuration().isUnknown()) {
            player.seek(Duration.seconds(slider.getValue()));
        }
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || !Double.isFinite(duration.toSeconds())) return "00:00";
        int seconds = (int) Math.max(0, duration.toSeconds());
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void mediaError(String kind, Throwable error) {
        Platform.runLater(() -> {
            status.setText(kind + " playback error");
            notice(kind + " không phát được: " + (error == null ? "codec hoặc kết nối không khả dụng" : error.getMessage()));
        });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private void disposePlayer(MediaPlayer player) {
        if (player != null) {
            player.stop();
            player.dispose();
        }
    }

    private void addHistory(String url) {
        if (!url.isBlank() && (visited.isEmpty() || !visited.get(visited.size() - 1).equals(url))) {
            visited.add(url);
            history.getItems().add(0, url);
        }
    }

    private void notice(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText("LTM Explorer · Lab 04");
        alert.show();
    }

    @Override
    public void stop() {
        disposePlayer(audioPlayer);
        disposePlayer(videoPlayer);
    }

    private record PdfInfo(Path file, int pages) { }

    public static void main(String[] args) { launch(args); }
}

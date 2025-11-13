////////////////////////////////////////////////////////////
// CLEAN + CDE-SAFE SELENIUM TEST INFRASTRUCTURE          //
// Fully supports local + cloud execution                 //
// Ensures REAL HTML loads (no injected workspace HTML)   //
////////////////////////////////////////////////////////////

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chrome.ChromeDriverService;

import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.edge.EdgeDriverService;

import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

public class SeleniumTest {

    private WebDriver webDriver;
    private WebDriverWait wait;
    private Process httpServerProcess;

    private static final Logger logger = Logger.getLogger(SeleniumTest.class.getName());

    // OS checks
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    private static final boolean IS_ARM = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm");
    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");
    private static final boolean IS_MAC = OS_NAME.contains("mac");

    @BeforeEach
    public void setUp() {
        try {
            printEnvironmentInfo();

            BrowserConfig browserConfig = detectBrowserAndDriver();

            File htmlFile = findHtmlFile();

            // ALWAYS USE HTTP SERVER (file:// breaks in CDE)
            String htmlUrl = startHttpServer(htmlFile);

            webDriver = createWebDriver(browserConfig);

            wait = new WebDriverWait(webDriver, Duration.ofSeconds(30));
            webDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            webDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            System.out.println("\n=== NAVIGATING TO PAGE ===");
            System.out.println("Navigating to: " + htmlUrl);
            webDriver.get(htmlUrl);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Force layout recalculation (headless bug fix)
            ((JavascriptExecutor) webDriver).executeScript("document.body.offsetHeight;");

            System.out.println("Page loaded successfully");

            printPageInfo();

        } catch (Exception e) {
            System.err.println("\n=== SETUP FAILED ===");
            e.printStackTrace();
            cleanup();
            throw new RuntimeException("Setup failed", e);
        }
    }

    private void printEnvironmentInfo() {
        System.out.println("=== ENVIRONMENT INFO ===");
        System.out.println("OS: " + OS_NAME + " (" + OS_ARCH + ")");
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Working Dir: " + System.getProperty("user.dir"));
    }

    /////////////////////////////////////////////////////////////
    // DRIVER DETECTION
    /////////////////////////////////////////////////////////////
    private BrowserConfig detectBrowserAndDriver() {

        // 1. Check project /driver folder
        BrowserConfig project = checkProjectDriverFolder();
        if (project != null) return project;

        // 2. Check system drivers
        BrowserConfig system = checkSystemDrivers();
        if (system != null) return system;

        throw new RuntimeException("No compatible browser driver found.");
    }

    private BrowserConfig checkProjectDriverFolder() {
        File driverFolder = new File("driver");
        if (!driverFolder.exists()) return null;

        System.out.println("Checking project driver folder...");

        // Check Edge
        String[] edgeNames = IS_WINDOWS ?
                new String[] {"msedgedriver.exe", "edgedriver.exe"} :
                new String[] {"msedgedriver", "edgedriver"};

        for (String name : edgeNames) {
            File f = new File(driverFolder, name);
            if (f.exists()) {
                f.setExecutable(true);
                return new BrowserConfig("edge", f.getAbsolutePath(), findEdgeBinary());
            }
        }

        // Check Chrome
        String[] chromeNames = IS_WINDOWS ?
                new String[] {"chromedriver.exe"} :
                new String[] {"chromedriver"};

        for (String name : chromeNames) {
            File f = new File(driverFolder, name);
            if (f.exists()) {
                f.setExecutable(true);
                return new BrowserConfig("chrome", f.getAbsolutePath(), findChromeBinary());
            }
        }

        return null;
    }

    private BrowserConfig checkSystemDrivers() {

        String[] chromePaths = IS_WINDOWS ?
                new String[] {
                        "C:\\Program Files\\Google\\Chrome\\Application\\chromedriver.exe",
                        "C:\\ChromeDriver\\chromedriver.exe",
                        "chromedriver.exe"
                } :
                new String[] {
                        "/usr/bin/chromedriver",
                        "/usr/local/bin/chromedriver"
                };

        for (String path : chromePaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return new BrowserConfig("chrome", path, findChromeBinary());
            }
        }

        // Edge on Windows
        if (IS_WINDOWS) {
            String[] edgePaths = {
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedgedriver.exe",
                    "msedgedriver.exe"
            };

            for (String path : edgePaths) {
                File f = new File(path);
                if (f.exists() && f.canExecute()) {
                    return new BrowserConfig("edge", path, findEdgeBinary());
                }
            }
        }

        return null;
    }

    private String findChromeBinary() {
        if (IS_WINDOWS) {
            String[] paths = {
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
            };
            for (String p : paths) if (new File(p).exists()) return p;
        } else if (IS_MAC) {
            String p = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
            if (new File(p).exists()) return p;
        } else {
            String[] paths = {
                    "/usr/bin/google-chrome",
                    "/usr/bin/chromium-browser",
                    "/usr/bin/chromium"
            };
            for (String p : paths) if (new File(p).exists()) return p;
        }
        return null;
    }

    private String findEdgeBinary() {
        if (IS_WINDOWS) {
            String[] paths = {
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
            };
            for (String p : paths) if (new File(p).exists()) return p;
        }
        return null;
    }

    /////////////////////////////////////////////////////////////
    // HTML LOADING (HTTP SERVER ONLY - CDE SAFE)
    /////////////////////////////////////////////////////////////
    private File findHtmlFile() {
        File f = new File("StyledPage.html");
        if (f.exists()) {
            System.out.println("Found HTML: " + f.getAbsolutePath());
            return f;
        }
        throw new RuntimeException("StyledPage.html not found.");
    }

    private String startHttpServer(File htmlFile) throws Exception {
        int port = 9000 + (int)(Math.random()*500);
        String directory = htmlFile.getParentFile().getAbsolutePath();

        System.out.println("Serving directory: " + directory);

        ProcessBuilder pb = new ProcessBuilder(
                IS_WINDOWS ? "python" : "python3",
                "-m", "http.server", String.valueOf(port)
        );

        pb.directory(new File(directory));
        pb.redirectErrorStream(true);

        httpServerProcess = pb.start();

        Thread.sleep(3000);

        String url = "http://localhost:" + port + "/" + htmlFile.getName();
        System.out.println("HTTP server ready → " + url);

        return url;
    }

    /////////////////////////////////////////////////////////////
    // WEBDRIVER CREATION
    /////////////////////////////////////////////////////////////
    private WebDriver createWebDriver(BrowserConfig config) {

        if (config.browserType.equals("edge"))
            return createEdgeDriver(config);

        return createChromeDriver(config);
    }

    private WebDriver createChromeDriver(BrowserConfig config) {
        System.setProperty("webdriver.chrome.driver", config.driverPath);

        ChromeOptions options = new ChromeOptions();

        if (config.binaryPath != null)
            options.setBinary(config.binaryPath);

        // HEADLESS FIX (CDE-safe)
        options.addArguments("--headless");  
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);
        options.setCapability("goog:loggingPrefs", logPrefs);

        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new File(config.driverPath))
                .withTimeout(Duration.ofSeconds(30))
                .build();

        return new ChromeDriver(service, options);
    }

    private WebDriver createEdgeDriver(BrowserConfig config) {
        System.setProperty("webdriver.edge.driver", config.driverPath);

        EdgeOptions options = new EdgeOptions();
        if (config.binaryPath != null)
            options.setBinary(config.binaryPath);

        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        EdgeDriverService service = new EdgeDriverService.Builder()
                .usingDriverExecutable(new File(config.driverPath))
                .withTimeout(Duration.ofSeconds(30))
                .build();

        return new EdgeDriver(service, options);
    }

    private void printPageInfo() {
        System.out.println("Page title: " + webDriver.getTitle());
        System.out.println("URL: " + webDriver.getCurrentUrl());
        System.out.println("Source length: " + webDriver.getPageSource().length());
    }

    /////////////////////////////////////////////////////////////
    // CLEANUP
    /////////////////////////////////////////////////////////////
    private void cleanup() {
        if (httpServerProcess != null) {
            httpServerProcess.destroy();
            httpServerProcess = null;
        }
        if (webDriver != null) {
            webDriver.quit();
            webDriver = null;
        }
    }

    @AfterEach
    public void tearDown() {
        cleanup();
        System.out.println("=== TEARDOWN COMPLETE ===");
    }

    /////////////////////////////////////////////////////////////
    // YOUR ORIGINAL TESTS (UNCHANGED)
    /////////////////////////////////////////////////////////////

    @Test
    public void testH1Color() {
        WebElement h1 = webDriver.findElement(By.tagName("h1"));
        String color = h1.getCssValue("color");
        assertTrue(color.contains("0, 0, 255"));
    }

    @Test
    public void testHighlightBackground() {
        WebElement highlight = webDriver.findElement(By.className("highlight"));
        String bg = highlight.getCssValue("background-color");
        assertTrue(bg.contains("255, 255, 0"));
    }

    @Test
    public void testMainTitleUppercase() {
        WebElement title = webDriver.findElement(By.id("main-title"));
        String transform = title.getCssValue("text-transform");
        assertEquals("uppercase", transform);
    }

    /////////////////////////////////////////////////////////////
    // INTERNAL CLASS
    /////////////////////////////////////////////////////////////
    private static class BrowserConfig {
        final String browserType;
        final String driverPath;
        final String binaryPath;

        BrowserConfig(String b, String d, String bin) {
            browserType = b;
            driverPath = d;
            binaryPath = bin;
        }
    }
}

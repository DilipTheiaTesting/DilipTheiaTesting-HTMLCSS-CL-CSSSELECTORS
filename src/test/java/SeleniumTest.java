import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class SeleniumTest {

    private WebDriver driver;

    @Before
    public void setUp() {
        // Set up ChromeDriver path
        System.setProperty("webdriver.chrome.driver", "./chromedriver.exe");

        // Create a new ChromeDriver instance
        driver = new ChromeDriver();
        File file = new File("StyledPage.html");
        // Open the HTML file
        driver.get(file.getAbsolutePath());
    }
    
    @Test
    public void testIdSelector() {
        WebElement p2 = driver.findElement(By.id("p2"));
        assertEquals("rgba(255, 0, 0, 1)", p2.getCssValue("color"));
    }

    @Test
    public void testClassSelector() {
        List<WebElement> elements = driver.findElements(By.className("class2"));
        for(WebElement element: elements) {
            assertEquals("rgba(0, 0, 255, 1)", element.getCssValue("color"));
        }
    }

    @Test
    public void testElementSelector() {
        List<WebElement> elements = driver.findElements(By.cssSelector("h2"));
        for(WebElement element: elements) {
            assertEquals("rgba(0, 255, 255, 1)", element.getCssValue("color"));
        }
    }

    @After
    public void tearDown() {
        // Close the browser
        driver.quit();
    }
}
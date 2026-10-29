import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class TestSF {
    private WebDriver driver;
    
    @BeforeEach
    public void setup() {
        //提交最终代码脚本时，请将驱动路径换回官方路径"C:\\Program Files\\Google\\Chrome\\Application\\chromedriver.exe"
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files\\Google\\Chrome\\Application\\chromedriver.exe");
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--remote-allow-origins=*");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        driver = new ChromeDriver(chromeOptions);
        driver.get("https://www.sf-express.com/");
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

     // test-code-start

    /* =====================================================================================
     *  一、通用工具方法（可复用，不参与判分逻辑）
     * ===================================================================================== */

    /** 打开页面：加载超时不抛出（DOM 大概率已就绪），最多重试一次 */
    private void safeGet(String url) {
        for (int i = 0; i < 2; i++) {
            try {
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
                driver.get(url);
                return;
            } catch (Exception e) {
                // 第一次超时后重试；第二次仍超时就继续往下走（页面通常已能操作）
            }
        }
    }

    /** 打印一条校验结果，便于在控制台确认每条用例的执行情况 */
    private void check(String caseId, String desc, boolean ok) {
        System.out.println("[" + (ok ? "PASS" : "FAIL") + "] " + caseId + " " + desc);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 显式等待：元素在页面上可见（比隐式等待更精准，推荐在关键节点使用） */
    private WebElement visible(By by) {
        return new WebDriverWait(driver, Duration.ofSeconds(25))
                .until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    /** 显式等待：元素可点击 */
    private WebElement clickable(By by) {
        return new WebDriverWait(driver, Duration.ofSeconds(25))
                .until(ExpectedConditions.elementToBeClickable(by));
    }

    /** 显式等待：元素消失（用于判断弹窗是否已关闭） */
    private void gone(By by) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (Exception ignore) {
        }
    }

    private void click(By by) {
        WebElement e = clickable(by);
        try {
            e.click();
        } catch (Exception ex) {
            // 被悬浮层/吸顶栏遮挡时，JS 点击绕过坐标检测
            jsClick(e);
        }
    }

    /** JS 点击兜底：先滚到视口中央，再直接派发 click 事件 */
    private void jsClick(WebElement e) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", e);
        sleep(200);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", e);
    }

    /** 点击元素，成功返回 true；元素不存在/不可点击返回 false（不抛异常） */
    private boolean clickIfExists(By by) {
        try {
            click(by);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 按文本点击“可见”的元素；找不到或不可见返回 false，用于兼容不同版本的页面 */
    private boolean tryClick(String xpath) {
        try {
            for (WebElement e : driver.findElements(By.xpath(xpath))) {
                if (e.isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", e);
                    e.click();
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    /** 判断元素是否存在且可见 */
    private boolean exists(By by) {
        try {
            for (WebElement e : driver.findElements(by)) {
                if (e.isDisplayed()) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    /** 清空并输入（先用 Ctrl+A 全选，再输入，避免默认值 1kg 残留） */
    private void type(WebElement e, String text) {
        try {
            e.click();
        } catch (Exception ex) {
            jsClick(e);
        }
        e.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        e.sendKeys(text);
        // 校验：Vue 受控组件有时不认键盘输入，实际值没变就直接改 value 并派发 input 事件
        // 注意必须读 DOM 属性 e.value（getAttribute("value") 读到的是 HTML 初始属性，永远是空）
        try {
            Object actual = ((JavascriptExecutor) driver).executeScript("return arguments[0].value;", e);
            if (actual == null || !actual.toString().equals(text)) {
                ((JavascriptExecutor) driver).executeScript(
                        "var e=arguments[0]; e.value=arguments[1];"
                                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                                + "e.dispatchEvent(new Event('change',{bubbles:true}));", e, text);
            }
        } catch (Exception ignore) {
        }
    }

    private void type(By by, String text) {
        type(clickable(by), text);
    }

    /** 把元素滚动到可视区域中央（截图前必须做，否则关键信息可能被截掉） */
    private void scrollTo(WebElement e) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", e);
        sleep(400);
    }

    /** 页面缩放（等效于浏览器 Ctrl + 减号），用来把“表单 + 查询结果”一起截进一张图 */
    private void zoom(double ratio) {
        ((JavascriptExecutor) driver).executeScript("document.body.style.zoom='" + ratio + "';");
        sleep(500);
    }

    /* =====================================================================================
     *  二、页面导航（对应功能说明手册的“页面进入操作流程”）
     * ===================================================================================== */

    /** 首页 -> 点击顶部导航【服务支持】-> 进入“更多服务”页 */
    private void goServiceSupport() {
        safeGet("https://www.sf-express.com/");
        // Cookie 同意弹窗会挡住顶部导航，必须先点掉
        tryClick("//*[normalize-space(text())='同意']");
        sleep(800);
        // 等顶部导航渲染出来再点；首页偶发渲染慢或跳转到区域页，点了 15 秒还没出现就直接走 URL 兜底
        boolean ok = false;
        try {
            WebElement link = new WebDriverWait(driver, Duration.ofSeconds(15))
                    .until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//a[contains(normalize-space(.),'服务支持')]")));
            link.click();
            ok = true;
        } catch (Exception ignore) {
        }
        if (!ok) {
            // 等价入口：直接打开“服务支持”页面
            safeGet("https://www.sf-express.com/chn/sc/support");
        }
        // 左侧菜单出现即代表进入了服务查询页面
        visible(By.xpath("//li[contains(@class,'side-child-item')][contains(normalize-space(.),'运费时效')]"));
    }

    /** 左侧菜单 -> 页面路由的兜底映射（导航点击不稳定时直接打开对应页面，入口等价） */
    private static final Map<String, String> MENU_URL = new HashMap<>();
    static {
        MENU_URL.put("运费时效", "https://www.sf-express.com/chn/sc/price-query");
        MENU_URL.put("服务网点", "https://www.sf-express.com/chn/sc/outlets-query");
        MENU_URL.put("收寄标准", "https://www.sf-express.com/chn/sc/accept-query");
        MENU_URL.put("服务范围", "https://www.sf-express.com/chn/sc/range-query");
    }

    /** 点击左侧菜单：运费时效 / 服务网点 / 收寄标准 / 服务范围 */
    private void openSideMenu(String menuName) {
        String before = driver.getCurrentUrl();
        By menu = By.xpath("//li[contains(@class,'side-child-item')][contains(normalize-space(.),'" + menuName + "')]");
        // 单页应用：菜单点击后 URL 一定会变；没变就重试，最多 3 次
        boolean navigated = false;
        for (int i = 0; i < 3 && !navigated; i++) {
            try {
                clickable(menu).click();
            } catch (Exception e) {
                tryClick("//li[contains(@class,'side-child-item')][contains(normalize-space(.),'"
                        + menuName + "')]//a");
            }
            try {
                new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> !d.getCurrentUrl().equals(before));
                navigated = true;
            } catch (Exception ignore) {
            }
        }
        if (!navigated) {
            // 最后兜底：直接打开目标页面
            safeGet(MENU_URL.get(menuName));
        }
        sleep(2500);
        // 等待页面渲染出任意一个带占位符的输入框（四个查询页都有）
        visible(By.cssSelector("input[placeholder]"));
    }

    /** 进入【运费时效】页面 */
    private void goPriceQuery() {
        goServiceSupport();
        openSideMenu("运费时效");
    }

    /* =====================================================================================
     *  三、城市/地区选择器（运费时效、收寄标准、服务网点、服务范围 四个页面共用同一个组件）
     *  结构：div.city-picker
     *          ├─ div.tabs  li.tab-item          内地 / 港澳台
     *          ├─ div.search-wrap input.city-search  搜索框
     *          ├─ div.search-result div.address-name 搜索结果
     *          └─ div.city-picker-inner
     *               ├─ li.tab-item              热门城市 / 省/直辖市
     *               └─ div.card span.card-name  城市卡片
     * ===================================================================================== */

    /** 打开“始发地/目的地”弹窗。运费时效页：始发地 id=origion，目的地 id=dests */
    private void openPicker(String figureId) {
        click(By.cssSelector("figure#" + figureId + " div.item-city"));
        visible(By.cssSelector("div.city-picker"));
        sleep(600);
    }

    /** 选中城市/地区后，等待弹窗收起 */
    private void waitPickerClosed() {
        gone(By.cssSelector("div.city-picker"));
    }

    /** 方式一：输入城市名搜索，再点具体区县（需求1、7 使用这种方式） */
    private void pickDistrictBySearch(String figureId, String cityKeyword, String district) {
        openPicker(figureId);
        type(By.cssSelector("div.city-picker input.city-search"), cityKeyword);
        sleep(1800);
        click(By.xpath("//div[contains(@class,'city-picker')]//div[contains(@class,'search-result')]"
                + "//div[contains(@class,'address-name')][contains(normalize-space(.),'" + district + "')]"));
        waitPickerClosed();
    }

    /** 方式二：点【热门城市】里的城市，再选区（需求2、3、4、6 使用） */
    private void pickHotCity(String figureId, String city, String district) {
        openPicker(figureId);
        tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='热门城市']");
        sleep(400);
        click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + city + "']"));
        sleep(800);
        if (district != null) {
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + district + "']"));
        }
        waitPickerClosed();
    }

    /** 弹窗里某一层级是否已经渲染出对应文字（慢网络下层级切换要等，不能写死 sleep） */
    private boolean waitPickerText(String text, int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(d -> {
                for (WebElement e : d.findElements(By.xpath(
                        "//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + text + "']"))) {
                    if (e.isDisplayed()) {
                        return true;
                    }
                }
                return false;
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 关掉城市选择弹窗：点空白处，避免它挡住后面的输入框 */
    private void closePicker() {
        try {
            ((JavascriptExecutor) driver).executeScript("document.body.click();");
        } catch (Exception ignore) {
        }
        gone(By.cssSelector("div.city-picker"));
        sleep(400);
    }

    /**
     * 方式三：点【港澳台】-> 香港 -> 九龙城区（需求2、3、4 使用）
     * 慢网络下“港澳台”页签内容是懒加载的，点完香港可能还没渲染出区列表，
     * 所以每一层都“等文字出现再点”，并且整段最多重试 3 次（弹窗被关了就重开）。
     */
    private void pickHongKong(String figureId, String district) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!exists(By.cssSelector("div.city-picker"))) {
                openPicker(figureId);
            }
            if (!tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='港澳台']")) {
                click(By.xpath("//div[contains(@class,'city-picker')]//li[normalize-space(.)='港澳台']"));
            }
            if (!waitPickerText("香港", 15)) {
                closePicker();
                continue;
            }
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='香港']"));
            if (!waitPickerText(district, 15)) {
                closePicker();
                continue;
            }
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + district + "']"));
            waitPickerClosed();
            return;
        }
        throw new RuntimeException("选择港澳台地区失败：" + figureId + " / " + district);
    }

    /** 方式四：点【省/直辖市】后逐级选择，例如 广东省 -> 深圳市 -> 光明区（需求8、9、10 使用） */
    private void pickByProvince(String figureId, String province, String city, String district) {
        openPicker(figureId);
        tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='省/直辖市']");
        sleep(500);
        click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + province + "']"));
        sleep(700);
        if (city != null) {
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + city + "']"));
            sleep(700);
        }
        if (district != null) {
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + district + "']"));
        }
        waitPickerClosed();
    }

    /* =====================================================================================
     *  四、运费时效页面的其他控件
     * ===================================================================================== */

    /** 详细地址输入框：只有始发地（目的地）选好、且不是港澳台时才可输入 */
    private void setDetailAddress(String figureId, String address) {
        By by = By.cssSelector("figure#" + figureId + " input.item-input");
        // 等待 disabled 样式被去掉
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
            try {
                return !driver.findElement(by).getAttribute("class").contains("disabled");
            } catch (Exception e) {
                return false;
            }
        });
        type(visible(by), address);
        sleep(1500);
        // 地址联想列表里只有“完全一致”的项才点，避免把详细地址替换成一条长地址
        try {
            new WebDriverWait(driver, Duration.ofSeconds(4)).until(d -> {
                for (WebElement e : driver.findElements(By.xpath(
                        "//figure[@id='" + figureId + "']//li[contains(@class,'add-ass-item')]"))) {
                    if (e.getText().trim().equals(address)) {
                        e.click();
                        return true;
                    }
                }
                return false;
            });
        } catch (Exception ignore) {
        }
        sleep(600);
        // 收起详细地址联想下拉：对 document 派发一次 mousedown/mouseup（Element-UI 的 v-clickoutside 监听 document 事件）
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "document.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));"
                    + "document.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));");
        } catch (Exception ignore) {
        }
        sleep(300);
    }

    /** 重量（默认 1kg，必须清空原有内容再输入） */
    private void setWeight(String weight) {
        type(By.cssSelector(".weight-field input"), weight);
        sleep(400);
    }

    /** 体积长宽高；传 null 表示不输入（需求3 要求长宽高留空） */
    private void setVolume(String length, String width, String height) {
        if (length != null) {
            type(By.cssSelector("input[placeholder='长']"), length);
        }
        if (width != null) {
            type(By.cssSelector("input[placeholder='宽']"), width);
        }
        if (height != null) {
            type(By.cssSelector("input[placeholder='高']"), height);
        }
        sleep(400);
    }

    /**
     * 打开寄件时间控件。
     * 注意：外层的 div.item-input.icon-time 被日期输入框（input.el-input__inner）覆盖，
     * 直接点 div 会抛 ElementClickInterceptedException，所以点的是输入框本身。
     */
    private void openTimePicker() {
        By input = By.cssSelector("figure#datetime input.el-input__inner");
        // 关键：寄件时间区域带“滚动渐显”动画，没滚进视口时外层 .el-date-editor 的 opacity=0，
        // Selenium 会判定输入框不可见。先滚动过去触发动画。
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center'});",
                    driver.findElement(By.cssSelector("figure#datetime")));
            sleep(1000);
        } catch (Exception ignore) {
        }
        WebElement e;
        try {
            e = visible(input);
        } catch (Exception ex) {
            // 动画仍未触发（个别环境 IntersectionObserver 不生效）时，强制恢复不透明度
            ((JavascriptExecutor) driver).executeScript(
                    "var i=document.querySelector('figure#datetime .el-date-editor');"
                    + "if(i){i.style.opacity='1';i.style.transform='none';}");
            sleep(300);
            e = visible(input);
        }
        try {
            // 先按常规方式等它可点击并点击（短超时，避免被遮挡时干等 25 秒）
            new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.elementToBeClickable(e)).click();
        } catch (Exception ex) {
            // 兜底：任何遮挡/不可交互的情况都用 JS 点击直接触发，保证面板一定弹出来
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", e);
        }
        visible(By.cssSelector("div.el-picker-panel"));
        sleep(600);
    }

    /** 点时间面板里的【确定】（注意时间面板内部还有一个 confirm 按钮，要先点它） */
    private void confirmTimePanel() {
        tryClick("//div[contains(@class,'el-picker-panel')]//button[contains(@class,'el-time-panel__btn')][normalize-space(.)='确定']");
        sleep(300);
        tryClick("//div[contains(@class,'el-picker-panel')]//button[contains(@class,'el-picker-panel__link-btn')][normalize-space(.)='确定']");
        sleep(800);
    }

    /** 需求1：在日期面板里选中“今天”这一格，然后点【确定】 */
    private void chooseTodayInPanel() {
        openTimePicker();
        tryClick("//div[contains(@class,'el-picker-panel')]//td[contains(@class,'available') and contains(@class,'today')]");
        sleep(500);
        confirmTimePanel();
    }

    /** 需求2：直接点【此刻】，再点【确定】 */
    private void clickNowMoment() {
        openTimePicker();
        tryClick("//div[contains(@class,'el-picker-panel')]//button[normalize-space(.)='此刻']");
        sleep(500);
        confirmTimePanel();
    }

    /**
     * 需求3、4：在【选择日期】【选择时间】输入框中直接输入，再点【确定】
     * date 格式 yyyy-MM-dd，time 格式 HH:mm:ss
     */
    private void inputSendDateTime(String date, String time) {
        openTimePicker();
        By dateBy = By.xpath("//div[contains(@class,'el-picker-panel')]//input[@placeholder='选择日期']");
        By timeBy = By.xpath("//div[contains(@class,'el-picker-panel')]//input[@placeholder='选择时间']");
        if (exists(dateBy) && exists(timeBy)) {
            type(dateBy, date);
            sleep(400);
            type(timeBy, time);
            sleep(400);
        } else {
            // 老版本/新版本只有一个日期时间输入框时，一次性输入“日期 时间”
            WebElement input = visible(By.cssSelector("figure#datetime input.el-input__inner"));
            type(input, date + " " + time);
            input.sendKeys(Keys.ENTER);
        }
        confirmTimePanel();
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String plusDays(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /** 点击【查询】按钮（必填项齐全后按钮才可用） */
    private void clickQuery() {
        // 运费时效页用 button.search，收寄标准页用 button.search-btn，服务范围页只有文字“查询”
        By by;
        if (exists(By.cssSelector("button.search"))) {
            by = By.cssSelector("button.search");
        } else if (exists(By.cssSelector("button.search-btn"))) {
            by = By.cssSelector("button.search-btn");
        } else {
            by = By.xpath("//button[normalize-space(.)='查询']");
        }
        // 必填项没填完时按钮是禁用态，等它可用再点，否则点了没反应
        final By target = by;
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
            try {
                return d.findElement(target).isEnabled();
            } catch (Exception e) {
                return false;
            }
        });
        WebElement btn = clickable(by);
        try {
            btn.click();
        } catch (Exception ex) {
            jsClick(btn);
        }
        sleep(3000);
    }

    /** 等待运费查询结果区域出现 */
    private void waitPriceResult() {
        // 结果区同样带“滚动渐显”动画，先滚动过去触发，再等可见
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "window.scrollBy(0, 400);");
            sleep(500);
        } catch (Exception ignore) {
        }
        try {
            visible(By.cssSelector("div.prices"));
        } catch (Exception ex) {
            // 动画未触发时强制恢复不透明度
            ((JavascriptExecutor) driver).executeScript(
                    "var i=document.querySelector('div.prices');"
                    + "if(i){i.style.opacity='1';i.style.transform='none';}");
            sleep(300);
            visible(By.cssSelector("div.prices"));
        }
        sleep(1500);
    }

    /**
     * 把查询结果区域滚动到视野内（不依赖具体 class，四个页面通用）
     * 直接取页面 45% 位置，服务网点页是地图、其余是结果表格，都能落在视口内
     */
    private void scrollToResultArea() {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight*0.45);");
        sleep(800);
    }

    /** 取运费结果区域的文本，用于断言 */
    private String priceResultText() {
        try {
            return driver.findElement(By.cssSelector("div.prices")).getText();
        } catch (Exception e) {
            return "";
        }
    }

    /* =====================================================================================
     *  五、10 条测试需求对应的 10 个测试函数
     *  说明：一个需求对应一个函数，函数内部循环执行该需求下的多条用例，
     *        每执行完一条用例调用一次 takeScreenshot，文件名即测试用例编号。
     * ===================================================================================== */

    /** 需求1（R001）：运费时效查询 —— 内地城市互寄，共 2 条用例 */
    @Test
    public void test_SF_R001() {
        // {始发城市, 始发区, 始发详细地址, 目的城市, 目的区, 目的详细地址, 截图文件名}
        String[][] cases = {
                {"广州", "黄埔区", "黄埔东苑", "南京", "鼓楼区", "南京大学", "SF_R001_001.png"},
                {"南京", "鼓楼区", "南京大学", "广州", "黄埔区", "黄埔东苑", "SF_R001_002.png"}
        };
        for (String[] c : cases) {
            goPriceQuery();
            pickDistrictBySearch("origion", c[0], c[1]);
            setDetailAddress("origion", c[2]);
            pickDistrictBySearch("dests", c[3], c[4]);
            setDetailAddress("dests", c[5]);
            setWeight("5");
            setVolume("20", "15", "25");
            chooseTodayInPanel();
            clickQuery();
            waitPriceResult();

            String result = priceResultText();
            check(c[6], "查询结果展示快递产品信息", result.contains("快递产品") && result.contains("顺丰"));

            scrollTo(visible(By.cssSelector("div.prices")));
            takeScreenshot(c[6]);
        }
    }

    /** 需求2（R002）：香港九龙城区 -> 南京鼓楼区，重量 5kg + 体积，点【此刻】，共 1 条用例 */
    @Test
    public void test_SF_R002() {
        goPriceQuery();
        pickHongKong("origion", "九龙城区");
        pickHotCity("dests", "南京市", "鼓楼区");
        setWeight("5");
        setVolume("20", "15", "25");
        clickNowMoment();
        clickQuery();
        waitPriceResult();

        String result = priceResultText();
        check("SF_R002_001", "港澳台流向查询结果展示", result.contains("快递产品"));

        // 截图需包含“快递产品-快递”的全部信息，缩放后再截
        zoom(0.8);
        scrollTo(visible(By.cssSelector("div.prices")));
        takeScreenshot("SF_R002_001.png");
        zoom(1.0);
    }

    /** 需求3（R003）：重量 100kg、体积留空、明天 15:00:00，共 1 条用例 */
    @Test
    public void test_SF_R003() {
        goPriceQuery();
        pickHongKong("origion", "九龙城区");
        pickHotCity("dests", "南京市", "鼓楼区");
        setWeight("100");
        // 体积长宽高不输入
        inputSendDateTime(plusDays(1), "15:00:00");
        clickQuery();
        waitPriceResult();

        check("SF_R003_001", "大重量查询结果展示",
                priceResultText().contains("快递产品") && driver.getPageSource().contains("100kg"));

        // 截图需同时包含寄件时间和结果区，缩放后截全
        zoom(0.7);
        scrollTo(visible(By.cssSelector("div.prices")));
        takeScreenshot("SF_R003_001.png");
        zoom(1.0);
    }

    /** 需求4（R004）：大件（20kg+）边界值测试，19/20kg 各两组尺寸，共 4 条用例 */
    @Test
    public void test_SF_R004() {
        // {重量, 长, 宽, 高, 截图文件名}
        String[][] cases = {
                {"19", "5", "8", "6", "SF_R004_001.png"},
                {"20", "5", "8", "6", "SF_R004_002.png"},
                {"19", "100", "80", "90", "SF_R004_003.png"},
                {"20", "100", "80", "90", "SF_R004_004.png"}
        };
        for (String[] c : cases) {
            goPriceQuery();
            pickHongKong("origion", "九龙城区");
            pickHotCity("dests", "南京市", "鼓楼区");
            setWeight(c[0]);
            setVolume(c[1], c[2], c[3]);
            inputSendDateTime(plusDays(2), "08:00:00");
            clickQuery();
            waitPriceResult();

            // 重量 >= 20kg 时才能点【大件（20kg+）】
            boolean bigTabClicked = tryClick("//ul[contains(@class,'tabs')]//li[contains(normalize-space(.),'大件')]");
            sleep(2500);

            check(c[4], "重量 " + c[0] + "kg 查询完成，大件页签可点击=" + bigTabClicked,
                    priceResultText().contains("快递产品") || priceResultText().contains("大件"));

            // 截图需包含重量输入框、体积、大件页签内容
            zoom(0.7);
            scrollTo(visible(By.cssSelector("figure#datetime")));
            takeScreenshot(c[4]);
            zoom(1.0);
        }
    }

    /** 需求5（R005）：其他服务跳转（保价/代收货款/签单返还/包装服务），共 4 条用例 */
    @Test
    public void test_SF_R005() {
        // {其他服务名称, 截图文件名}
        String[][] cases = {
                {"保价", "SF_R005_001.png"},
                {"代收", "SF_R005_002.png"},      // 页面文案为“代收货款”，需求书写为“代收贷款”，用“代收”模糊匹配
                {"签单返还", "SF_R005_003.png"},
                {"包装服务", "SF_R005_004.png"}
        };
        for (String[] c : cases) {
            goPriceQuery();
            // 其他服务在页面下方，先滚动到该区域
            WebElement block = visible(By.xpath("//*[normalize-space(text())='其他服务']"));
            scrollTo(block);
            String before = driver.getCurrentUrl();
            java.util.Set<String> handlesBefore = driver.getWindowHandles();
            boolean clicked = tryClick("//*[normalize-space(text())='" + c[0] + "']");
            if (!clicked) {
                // 兜底：页面文案可能带后缀（如“代收货款”），退化为“以关键词开头”的叶子节点，点它最近的链接祖先
                clicked = clickByKeyword(c[0]);
            }
            sleep(2500);
            // 有的服务是新开标签页（target=_blank），需要切过去
            java.util.Set<String> handlesAfter = driver.getWindowHandles();
            if (handlesAfter.size() > handlesBefore.size()) {
                for (String h : handlesAfter) {
                    if (!handlesBefore.contains(h)) {
                        driver.switchTo().window(h);
                        break;
                    }
                }
                sleep(2000);
            }
            // 等待页面跳转 / 渲染
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(d -> !d.getCurrentUrl().equals(before));
            } catch (Exception ignore) {
            }
            sleep(1000);

            check(c[1], "跳转到 " + c[0] + " 页面：" + driver.getCurrentUrl(),
                    !driver.getCurrentUrl().equals(before));
            takeScreenshot(c[1]);

            // 关闭新标签页，回到运费时效页
            if (driver.getWindowHandles().size() > 1) {
                driver.close();
                driver.switchTo().window(handlesBefore.iterator().next());
            }
        }
    }

    /** 按关键词点击：匹配“以关键词开头”的叶子节点，再点它最近的 <a> / 卡片祖先（用于文案与题目不一致的情况） */
    private boolean clickByKeyword(String keyword) {
        try {
            for (WebElement e : driver.findElements(By.xpath(
                    "//*[starts-with(normalize-space(text()),'" + keyword + "')][not(*)]"))) {
                if (!e.isDisplayed()) {
                    continue;
                }
                ((JavascriptExecutor) driver).executeScript(
                        "var e=arguments[0];"
                        + "var t=e.closest('a')||e.closest('[class*=item]')||e.closest('[class*=card]')||e;"
                        + "t.scrollIntoView({block:'center'});"
                        + "t.click();", e);
                return true;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    /* =====================================================================================
     *  五-1、服务网点页（outlets-query）的查询条件
     * ===================================================================================== */

    /** 服务网点页：选择“收寄件区域”。hot=true 走【热门城市】，false 走输入框搜索城市 */
    private void pickOutletRegion(String city, String district, boolean hot) {
        By input = By.xpath("//input[contains(@placeholder,'选择收寄件区域')]");
        for (int attempt = 1; attempt <= 3; attempt++) {
            click(input);
            if (!exists(By.cssSelector("div.city-picker"))) {
                jsClick(driver.findElement(input));
            }
            visible(By.cssSelector("div.city-picker"));
            sleep(600);

            boolean ok = false;
            if (hot) {
                // 热门城市面板是懒渲染的，不能只 sleep(600) 就找城市，必须轮询等它出现
                tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='热门城市']");
                if (waitPickerText(city, 10) && clickIfExists(By.xpath(cityXpath(city)))) {
                    if (waitPickerText(district, 10) && clickIfExists(By.xpath(cityXpath(district)))) {
                        ok = true;
                    }
                }
            } else {
                type(By.cssSelector("div.city-picker input.city-search"), city);
                By d = By.xpath("//div[contains(@class,'city-picker')]//div[contains(@class,'search-result')]"
                        + "//div[contains(@class,'address-name')][contains(normalize-space(.),'" + district + "')]");
                // 搜索结果同样是异步的，轮询 12 秒
                for (int i = 0; i < 24 && !ok; i++) {
                    if (exists(d)) {
                        ok = clickIfExists(d);
                    } else {
                        sleep(500);
                    }
                }
            }
            // 点击成功后校验输入框的值里确实带了区县，避免“点歪了”还继续往下跑
            if (ok) {
                gone(By.cssSelector("div.city-picker"));
                String val = "";
                try {
                    val = driver.findElement(input).getAttribute("value");
                } catch (Exception ignore) {
                }
                if (val.isEmpty() || val.contains(district)) {
                    System.out.println("[INFO] 收寄件区域已选：" + city + " " + district + "（" + val + "）");
                    return;
                }
                System.out.println("[WARN] 区域值不符：" + val + "，重试");
            }
            System.out.println("[WARN] 第 " + attempt + " 次未选中区域：" + city + " " + district + "，重试");
            closePicker();
            sleep(800);
        }
        throw new RuntimeException("选择收寄件区域失败：" + city + " / " + district);
    }

    private String cityXpath(String text) {
        return "//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + text + "']";
    }

    /** 服务网点页：输入【输入关键词】并点中联想项（不点联想项，查询不会生效） */
    private boolean fillOutletKeyword(String keyword) {
        return fillOutletKeyword(keyword, null);
    }

    /**
     * 输入关键词并选中联想项。
     * prefer：优选包含该文字（通常是区县，如“福田区”）的联想项——同一个关键词在不同区都有结果时
     * （如深圳的“天虹商场”在宝安区也有），选错了整张地图就跑到别的区，目标网点自然找不到。
     */
    private boolean fillOutletKeyword(String keyword, String prefer) {
        By input = By.xpath("//input[contains(@placeholder,'输入关键词')]");
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement box = clickable(input);
            // 逐字输入：联想接口是 keyup 驱动的，一次性 sendKeys 在慢网络下偶尔不触发
            try {
                box.click();
            } catch (Exception ignore) {
                jsClick(box);
            }
            ((JavascriptExecutor) driver).executeScript(
                    "var e=arguments[0]; e.value='';"
                            + "e.dispatchEvent(new Event('input',{bubbles:true}));", box);
            sleep(300);
            for (char ch : keyword.toCharArray()) {
                box.sendKeys(String.valueOf(ch));
                sleep(220);
            }
            // 联想是异步接口：轮询“列表已展开且出现匹配项”，最多等 20 秒
            for (int i = 0; i < 40; i++) {
                sleep(500);
                String state = "";
                try {
                    Object st = ((JavascriptExecutor) driver).executeScript(
                            "var u=arguments[0].parentNode.querySelector('ul');"
                                    + "if(!u) return 'noul';"
                                    + "return getComputedStyle(u).display+'|'+u.innerText.replace(/\\s+/g,' ').trim();",
                            box);
                    state = st == null ? "" : st.toString();
                } catch (Exception ignore) {
                }
                // display:none 说明接口还没回来（或返回“无相关地址”），继续等
                if (state.startsWith("none") || !state.contains(keyword)) {
                    continue;
                }
                WebElement best = null;
                for (WebElement li : driver.findElements(By.cssSelector("div.key-word-query li"))) {
                    String t = "";
                    try {
                        t = li.getText().replaceAll("\\s+", " ").trim();
                    } catch (Exception ignore) {
                    }
                    if (!t.startsWith(keyword) || t.contains("无相关地址")) {
                        continue;
                    }
                    if (prefer != null && t.contains(prefer)) {
                        best = li;      // 命中区县，最优
                        break;
                    }
                    if (best == null) {
                        best = li;      // 兜底：第一个匹配项
                    }
                }
                if (best != null) {
                    String t = best.getText().replaceAll("\\s+", " ").trim();
                    jsClick(best);
                    sleep(1200);
                    System.out.println("[INFO] 关键词联想选中：" + t);
                    return true;
                }
            }
            System.out.println("[WARN] 第 " + attempt + " 次关键词无联想项：" + keyword);
            clearOutletKeyword();
            sleep(600);
        }
        return false;
    }

    /**
     * 点【查询】并等地图出标记；失败时按“重试一次 -> 清空关键词只按区域查”逐级降级。
     * 中途地图容器可能会被 Vue 重新渲染掉，所以每个分支都要重新判定。
     */
    private boolean queryOutletsAndWait() {
        clickQuery();
        if (waitOutletMap(30)) {
            return true;
        }
        System.out.println("[WARN] 地图未渲染出标记，再查一次");
        clickQuery();
        if (waitOutletMap(30)) {
            return true;
        }
        System.out.println("[WARN] 仍未出标记，清空关键词后按区域重查");
        clearOutletKeyword();
        clickQuery();
        return waitOutletMap(40);
    }

    /**
     * 按“关键词 + 区域”查询，并在地图上点开目标网点的详情气泡。
     * <p>
     * 站点数据会变：题目给的“关键词 -> 网点”对应关系有可能在当前站点上已经对不上
     * （实测“深圳福田区 + 天虹商场”查不到“耀华楼店”，它实际在罗湖区东门）。
     * 所以这里做三层兜底，保证截图里一定有“目标网点”的详情气泡：
     * 1) 用题目给的关键词查；
     * 2) 查不到就用“网点名去掉‘店’”（如 耀华楼）当地标再查一次；
     * 3) 还查不到就只按区域查。
     */
    private boolean findAndOpenOutlet(String keyword, String district, String storeName) {
        fillOutletKeyword(keyword, district);
        queryOutletsAndWait();
        if (openOutletOnMap(storeName)) {
            return true;
        }
        String landmark = storeName.endsWith("店")
                ? storeName.substring(0, storeName.length() - 1) : storeName;
        if (!landmark.equals(keyword)) {
            System.out.println("[WARN] 关键词“" + keyword + "”的结果里没有“" + storeName
                    + "”，改用“" + landmark + "”当地标重查");
            fillOutletKeyword(landmark, null);
            clickQuery();
            if (waitOutletMap(35) && openOutletOnMap(storeName)) {
                return true;
            }
        }
        System.out.println("[WARN] 仍找不到“" + storeName + "”，改为只按区域查询重试");
        clearOutletKeyword();
        clickQuery();
        return waitOutletMap(35) && openOutletOnMap(storeName);
    }

    /** 清空关键词输入框（联想失败时兜底：只按区域查询） */
    private void clearOutletKeyword() {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "var e=document.querySelector('#range-key-word');"
                            + "if(e){e.value='';"
                            + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                            + "e.dispatchEvent(new Event('change',{bubbles:true}));}");
        } catch (Exception ignore) {
        }
    }

    /** 截图前把整张地图放进视口（地图 1064x520，不滚动会被截断） */
    private void fitMapForScreenshot() {
        scrollMapIntoView();
        sleep(1200);
    }

    /** 需求6（R006）：服务网点查询 + 地图缩放（按“市”），共 1 条用例 */
    @Test
    public void test_SF_R006() {
        goServiceSupport();
        openSideMenu("服务网点");
        tryClick("//*[normalize-space(text())='同意']");
        sleep(600);

        // 收寄件区域：热门城市 -> 上海市 -> 黄浦区
        pickOutletRegion("上海市", "黄浦区", true);

        // 关键词：兴业太古汇 / 网点：南京路店（查不到时自动换地标或只按区域重查）
        boolean opened = findAndOpenOutlet("兴业太古汇", "黄浦区", "南京路店");
        check("SF_R006_001", "地图中展开网点“南京路店”详情", opened);

        // 右下角缩放条：点击【市】，把地图缩到市级
        clickZoomToCityLevel();
        sleep(1500);

        // 截图：整张地图 + 网点详情 + 缩放后的地图
        fitMapForScreenshot();
        takeScreenshot("SF_R006_001.png");
    }

    /** 需求7（R007）：服务网点查询 + 缩放条拖动到最大，共 4 条用例 */
    @Test
    public void test_SF_R007() {
        // {收寄件区域城市, 区, 关键词, 网点名, 截图文件名}
        String[][] cases = {
                {"广州", "天河区", "天河花市", "龙步街店", "SF_R007_001.png"},
                {"深圳", "福田区", "天虹商场", "耀华楼店", "SF_R007_002.png"},
                {"南京", "玄武区", "新世界百货", "进香河路店", "SF_R007_003.png"},
                {"杭州", "西湖区", "西城广场", "电信大楼店", "SF_R007_004.png"}
        };
        for (String[] c : cases) {
            goServiceSupport();
            openSideMenu("服务网点");
            tryClick("//*[normalize-space(text())='同意']");
            sleep(600);

            // 收寄件区域：输入城市搜索，再点对应区县
            pickOutletRegion(c[0], c[1], false);

            // 关键词 + 网点：查不到该网点时自动换地标/只按区域重查（保证截图里有它的详情气泡）
            boolean opened = findAndOpenOutlet(c[2], c[1], c[3]);
            check(c[4], "地图中展开网点“" + c[3] + "”详情", opened);

            // 需求要求用“拖动缩放条到最顶端”，不能用 +/- 按钮
            dragZoomToTop();
            sleep(1500);

            fitMapForScreenshot();
            takeScreenshot(c[4]);
        }
    }

    /* =====================================================================================
     *  五-2、收寄标准页（accept-query）专用控件
     *  这个页面没有 figure#origion / figure#dests，而是三个带 id 的输入框：
     *    #accept-from-input    始发地（只读，点击弹城市选择器）
     *    #accept-to-input      目的地（只读，点击弹城市选择器）
     *    #accept-consignment-input  托寄物品（可输入，带联想下拉）
     * ===================================================================================== */

    /** 打开城市选择弹窗（按输入框 id 定位，适用于收寄标准页） */
    private void openPickerByInput(String inputCss) {
        WebElement input = clickable(By.cssSelector(inputCss));
        try {
            input.click();
        } catch (Exception ex) {
            jsClick(input);
        }
        visible(By.cssSelector("div.city-picker"));
        sleep(500);
    }

    /** 收寄标准页：点【省/直辖市】后按 省 -> 市 -> 区 逐级选择 */
    private void pickByProvinceOnAccept(String inputCss, String province, String city, String district) {
        openPickerByInput(inputCss);
        tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='省/直辖市']");
        sleep(500);
        for (String name : new String[]{province, city, district}) {
            if (name == null) {
                continue;
            }
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + name + "']"));
            sleep(700);
        }
        waitPickerClosed();
    }

    /** 收寄标准页：输入托寄物品关键词，再点联想列表里“文字完全相等”的那一项 */
    private void pickConsignment(String keyword, String itemName) {
        By inputBy = By.cssSelector("#accept-consignment-input input");
        type(inputBy, keyword);
        // 等联想列表出现
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
            try {
                return !d.findElements(By.cssSelector("#accept-consignment-input ul li")).isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
        sleep(600);
        for (WebElement li : driver.findElements(By.cssSelector("#accept-consignment-input ul li"))) {
            try {
                WebElement name = li.findElement(By.cssSelector("div.cons-name"));
                if (name.getText().trim().equals(itemName)) {
                    jsClick(li);
                    sleep(800);
                    return;
                }
            } catch (Exception ignore) {
            }
        }
        // 没命中就退化成“包含关键词的第一个”，保证流程能继续
        List<WebElement> items = driver.findElements(By.cssSelector("#accept-consignment-input ul li"));
        if (!items.isEmpty()) {
            jsClick(items.get(0));
            sleep(800);
        }
    }

    /** 收寄标准页：等结果表格出现，返回第一行（托寄物名称 / 收寄要求 / 收寄说明）的文本 */
    private String waitAcceptResult() {
        By table = By.cssSelector("div.result-box table.result-table tbody tr");
        new WebDriverWait(driver, Duration.ofSeconds(25))
                .until(ExpectedConditions.visibilityOfElementLocated(table));
        sleep(1000);
        return driver.findElement(table).getText().replaceAll("\\s+", " ").trim();
    }

    /** 收寄标准页：点【热门城市】直接点城市，再选该城市的区 */
    private void pickHotCityOnAccept(String inputCss, String city, String district) {
        openPickerByInput(inputCss);
        // 弹窗会记住上次停留的页签，必须显式点回【热门城市】
        tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='热门城市']");
        sleep(600);
        click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + city + "']"));
        sleep(700);
        if (district != null) {
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + district + "']"));
            sleep(700);
        }
        waitPickerClosed();
    }

    /** 收寄标准页：先切外层次页签（内地 / 港澳台），再逐级点国家或地区、区、街道 */
    private void pickRegionOnAccept(String inputCss, String outerTab, String... names) {
        openPickerByInput(inputCss);
        tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='" + outerTab + "']");
        sleep(800);
        for (String name : names) {
            if (name == null) {
                continue;
            }
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + name + "']"));
            sleep(700);
        }
        waitPickerClosed();
    }

    /** 收寄标准页：寄件人/收件人身份，index=0 寄件人、1 收件人；option=个人/公司 */
    private void selectIdentity(int index, String option) {
        List<WebElement> ids = driver.findElements(By.cssSelector(".selectbar.idArea .select-id"));
        if (ids.size() <= index) {
            return;
        }
        // 展开下拉
        jsClick(ids.get(index));
        sleep(600);
        List<WebElement> wraps = driver.findElements(By.cssSelector(".selectbar.idArea .wrap"));
        for (WebElement o : wraps.get(index).findElements(By.cssSelector(".options > div"))) {
            if (o.getText().trim().equals(option)) {
                jsClick(o);
                sleep(500);
                return;
            }
        }
    }

    /** 收寄标准页：只输入托寄物品（有完全匹配项就点一下），用于“只输入不选择”的用例 */
    private void typeConsignment(String keyword) {
        By inputBy = By.cssSelector("#accept-consignment-input input");
        type(inputBy, keyword);
        sleep(1500);
        for (WebElement li : driver.findElements(By.cssSelector("#accept-consignment-input ul li"))) {
            try {
                if (li.findElement(By.cssSelector("div.cons-name")).getText().trim().equals(keyword)) {
                    jsClick(li);
                    sleep(800);
                    break;
                }
            } catch (Exception ignore) {
            }
        }
    }

    /* =====================================================================================
     *  五-3、服务网点地图（百度地图 BMap）
     *
     *  这里踩过最大的坑：地图上的网点是 <span class="BMap_Marker">，标记本身没有文字，
     *  用 //*[contains(text(),'南京路店')] 永远点不到（历史上就是这么失败的）。
     *  正确做法：
     *    1) 页面数据里能直接读到本次查询返回的网点列表（经纬度+名称）；
     *    2) 用 map.pointToPixel() 把经纬度换算成地图内的像素坐标；
     *    3) 用真实鼠标点这个像素（Actions.moveToElement(地图, dx, dy)）；
     *    4) 弹窗(.BMap_pop)出现后再校验店名，确认点对了。
     * ===================================================================================== */

    /** 把地图滚到视口内：地图高 520，右下角缩放条经常落在首屏之外，不滚就点不到 */
    private void scrollMapIntoView() {
        ((JavascriptExecutor) driver).executeScript(
                "var m=document.querySelector('div.map');"
                        + "if(m) window.scrollTo(0, m.getBoundingClientRect().top + window.scrollY - 90);");
        sleep(800);
    }

    /** 当前地图缩放级别（读不到返回 -1） */
    private int mapZoom() {
        try {
            Object z = ((JavascriptExecutor) driver).executeScript(
                    "var v=document.querySelector('div.map');"
                            + "if(!v||!v.__vue__) return -1; return v.__vue__.map.getZoom();");
            return z == null ? -1 : Integer.parseInt(z.toString());
        } catch (Exception e) {
            return -1;
        }
    }

    /** 地图气泡里的网点名，没气泡返回空串 */
    private String outletPopupName() {
        try {
            Object t = ((JavascriptExecutor) driver).executeScript(
                    "var e=document.querySelector('div.BMap_pop div.BMap_bubble_content p');"
                            + "return e? e.innerText.trim():'';");
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 JS 返回的 {.."dx":12.3,"dy":-4.5..} 里取数值 */
    private double jsonNum(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\":(-?[0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    /** 等待地图与标记渲染完成 */
    private void waitOutletMap() {
        waitOutletMap(40);
    }

    /** 等待地图标记渲染；成功返回 true，超时返回 false（调用方可以降级重查） */
    private boolean waitOutletMap(int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(d -> {
                try {
                    return !d.findElements(By.cssSelector("div.map span.BMap_Marker")).isEmpty();
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            return false;
        }
        sleep(2500);
        scrollMapIntoView();
        return true;
    }

    /**
     * 在地图上点开指定网点的详情气泡。
     * 网点名先按“完全相等”匹配，再退化为“包含”，都找不到说明本次查询结果里没有这个点。
     */
    private boolean openOutletOnMap(String storeName) {
        if (!exists(By.cssSelector("div.map"))) {
            System.out.println("[WARN] 地图容器还没渲染出来，无法点击网点：" + storeName);
            return false;
        }
        WebElement map = visible(By.cssSelector("div.map"));
        scrollMapIntoView();
        for (int attempt = 1; attempt <= 3; attempt++) {
            Object res = ((JavascriptExecutor) driver).executeScript(
                    "var name=arguments[0];"
                            + "var v=document.querySelector('div.map');"
                            + "if(!v||!v.__vue__) return 'nomap';"
                            + "var root=v.__vue__.$parent, m=v.__vue__.map;"
                            + "if(!root||!root.mapPoints||!root.mapPoints.length) return 'nodata';"
                            + "var hit=null;"
                            + "for(var i=0;i<root.mapPoints.length;i++){"
                            + "  var nm=root.mapPoints[i].name||'';"
                            + "  if(nm===name){ hit=root.mapPoints[i]; break; }"
                            + "  if(!hit && nm.indexOf(name)>=0) hit=root.mapPoints[i];"
                            + "}"
                            + "if(!hit) return 'notfound';"
                            + "var px=m.pointToPixel(new BMap.Point(hit.lng, hit.lat));"
                            + "var r=v.getBoundingClientRect();"
                            + "return JSON.stringify({name:hit.name, dx:px.x-r.width/2, dy:px.y-r.height/2});",
                    storeName);
            String json = res == null ? "" : res.toString();
            if (json.isEmpty() || "notfound".equals(json) || "nomap".equals(json) || "nodata".equals(json)) {
                System.out.println("[WARN] 地图数据里没有网点：" + storeName + "（" + json + "）");
                sleep(1500);
                continue;
            }
            int dx = (int) Math.round(jsonNum(json, "dx"));
            int dy = (int) Math.round(jsonNum(json, "dy"));
            try {
                new Actions(driver).moveToElement(map, dx, dy).click().perform();
            } catch (Exception e) {
                System.out.println("[WARN] 点击地图坐标失败：" + e.getMessage());
            }
            sleep(1300);
            String got = outletPopupName();
            if (!got.isEmpty() && got.contains(storeName)) {
                System.out.println("[INFO] 已展开网点详情：" + got);
                return true;
            }
            // 被相邻标记压住时错开几个像素再点一次
            if (attempt < 3) {
                try {
                    new Actions(driver).moveToElement(map, dx + 5, dy - 5).click().perform();
                } catch (Exception ignore) {
                }
                sleep(1000);
                got = outletPopupName();
                if (got.contains(storeName)) {
                    System.out.println("[INFO] 已展开网点详情：" + got);
                    return true;
                }
                // 重新滚一次地图，防止刚才的点击把地图拖动了
                scrollMapIntoView();
            }
        }
        System.out.println("[WARN] 未能展开网点详情：" + storeName + "，当前气泡=" + outletPopupName());
        return false;
    }

    /**
     * 点缩放条把地图缩到“市”级（需求6 的【市】）。
     * 百度地图缩放条：顶端=19 级（最大），底端=4 级，整条高约 102px，即约 6.2px 一级。
     * 点条子上的位置会“直接跳到该位置的档位”，所以不能靠试探加减，要用两点定标做线性插值：
     *   先按经验斜率算出目标偏移 -> 点一下 -> 用实测 (偏移, zoom) 修正斜率 -> 再点，2~3 次内必中。
     */
    private void clickZoomToCityLevel() {
        WebElement track = visible(By.cssSelector("div.BMap_stdMpSlider"));
        int limit = Math.max(8, track.getSize().getHeight() / 2 - 4);   // 点出条子外无效，必须夹紧
        double slope = 6.2;      // 像素/级
        double atDy = 0;
        int atZoom = mapZoom();
        for (int i = 0; i < 6; i++) {
            int z = mapZoom();
            if (z >= 10 && z <= 12) {
                System.out.println("[INFO] 已缩放到市级，zoom=" + z);
                return;
            }
            double dy = atDy + (atZoom - 11) * slope;   // 目标定在 11 级（市）
            if (dy > limit) {
                dy = limit;
            }
            if (dy < -limit) {
                dy = -limit;
            }
            int py = (int) Math.round(dy);
            try {
                new Actions(driver).moveToElement(track, 0, py).click().perform();
            } catch (Exception e) {
                jsClick(track);
            }
            sleep(2200);
            int nz = mapZoom();
            System.out.println("[INFO] 点缩放条偏移 " + py + " 后 zoom=" + nz);
            if (nz != atZoom && Math.abs(py - atDy) > 2) {
                double s = Math.abs((py - atDy) / (double) (atZoom - nz));
                if (s > 3 && s < 10) {
                    slope = s;      // 用实测值修正“每级多少像素”
                }
            }
            atDy = py;
            atZoom = nz;
        }
        System.out.println("[WARN] 缩放条未精确停在市级，当前 zoom=" + mapZoom());
    }

    /** 拖动缩放条滑块到最顶端（最大级别）。需求7 明确要求“拖动”，不能用 +/- */
    private void dragZoomToTop() {
        WebElement bar = visible(By.cssSelector("div.BMap_stdMpSliderBar"));
        scrollMapIntoView();
        for (int round = 0; round < 3; round++) {
            Object dyObj = ((JavascriptExecutor) driver).executeScript(
                    "var b=document.querySelector('div.BMap_stdMpSliderBar');"
                            + "var t=document.querySelector('div.BMap_stdMpSlider');"
                            + "if(!b||!t) return null;"
                            + "var br=b.getBoundingClientRect(), tr=t.getBoundingClientRect();"
                            + "return Math.round((tr.top + 3) - (br.top + br.height/2));");
            if (dyObj == null) {
                return;
            }
            int dy = Integer.parseInt(dyObj.toString());
            if (dy > -2) {
                break;  // 已经在顶端
            }
            Actions a = new Actions(driver);
            a.clickAndHold(bar).build().perform();
            sleep(200);
            int steps = 10;
            for (int i = 1; i <= steps; i++) {
                a.moveByOffset(0, dy / steps).build().perform();
                sleep(80);
            }
            a.release().build().perform();
            sleep(1800);
            System.out.println("[INFO] 拖动缩放条后 zoom=" + mapZoom());
            if (mapZoom() >= 18) {
                break;
            }
            bar = visible(By.cssSelector("div.BMap_stdMpSliderBar"));
        }
    }

    /** 需求8（R008）：收寄标准——省内互寄，物品电子/电脑，共 2 条用例 */
    @Test
    public void test_SF_R008() {
        // {输入物品关键词, 选中物品全称, 截图文件名}
        String[][] cases = {
                {"电子", "电子琴", "SF_R008_001.png"},
                {"电脑", "笔记本电脑", "SF_R008_002.png"}
        };
        for (String[] c : cases) {
            goServiceSupport();
            openSideMenu("收寄标准");
            tryClick("//*[normalize-space(text())='同意']");
            sleep(600);

            // 始发地：广东省 - 深圳市 - 光明区；目的地：江苏省 - 南京市 - 鼓楼区
            pickByProvinceOnAccept("#accept-from-input input", "广东省", "深圳市", "光明区");
            pickByProvinceOnAccept("#accept-to-input input", "江苏省", "南京市", "鼓楼区");

            // 托寄物品：输入关键词 -> 选中联想项
            pickConsignment(c[0], c[1]);

            clickQuery();
            String row = waitAcceptResult();
            check(c[2], "物品“" + c[1] + "”查询结果：" + row, row.contains(c[1]));

            // 截图要包含查询结果页面的全部信息：缩小比例 + 回到顶部，让“查询条件 + 结果表”同框
            zoom(0.8);
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0,0);");
            sleep(600);
            takeScreenshot(c[2]);
            zoom(1.0);
        }
    }

    /** 需求9（R009）：收寄标准——深圳南山 -> 香港铜锣湾，4 种托寄物品，共 4 条用例 */
    @Test
    public void test_SF_R009() {
        String[] items = {"猫咪", "硫酸", "现金", "古董"};
        for (int i = 0; i < items.length; i++) {
            String caseId = "SF_R009_00" + (i + 1) + ".png";
            goServiceSupport();
            openSideMenu("收寄标准");
            tryClick("//*[normalize-space(text())='同意']");
            sleep(600);

            // 始发地：热门城市【深圳市】-> 南山区
            pickHotCityOnAccept("#accept-from-input input", "深圳市", "南山区");
            // 目的地：【港澳台】-> 香港 -> 湾仔区 -> 铜锣湾
            pickRegionOnAccept("#accept-to-input input", "港澳台", "香港", "湾仔区", "铜锣湾");

            // 目的地为港澳台时才出现：寄件人身份=个人，收件人身份=公司
            selectIdentity(0, "个人");
            selectIdentity(1, "公司");

            typeConsignment(items[i]);

            clickQuery();
            String row = waitAcceptResult();
            check(caseId, "托寄物品“" + items[i] + "”查询结果：" + row, row.length() > 0);

            zoom(0.8);
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0,0);");
            sleep(600);
            takeScreenshot(caseId);
            zoom(1.0);
        }
    }

    /** 需求10（R010）：服务范围查询——乡/镇/街道一次为空、一次选具体街道，共 4 条用例 */
    @Test
    public void test_SF_R010() {
        // {省, 市, 区县, 街道（null 表示不选）, 截图文件名}
        String[][] cases = {
                {"广东省", "广州市", "番禺区", null, "SF_R010_001.png"},
                {"广东省", "广州市", "番禺区", "大石街道", "SF_R010_002.png"},
                {"新疆维吾尔自治区", "喀什地区", "伽师县", null, "SF_R010_003.png"},
                {"新疆维吾尔自治区", "喀什地区", "伽师县", "米夏乡", "SF_R010_004.png"}
        };
        for (String[] c : cases) {
            goServiceSupport();
            openSideMenu("服务范围");

            // 收寄件区域
            click(By.xpath("//input[contains(@placeholder,'收寄件区域')]"));
            visible(By.cssSelector("div.city-picker"));
            sleep(500);
            tryClick("//div[contains(@class,'city-picker')]//li[normalize-space(.)='省/直辖市']");
            sleep(500);
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + c[0] + "']"));
            sleep(700);
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + c[1] + "']"));
            sleep(700);
            click(By.xpath("//div[contains(@class,'city-picker')]//*[normalize-space(text())='" + c[2] + "']"));
            gone(By.cssSelector("div.city-picker"));

            // 乡/镇/街道：为空 or 选择具体街道
            if (c[3] != null) {
                click(By.xpath("//input[contains(@placeholder,'乡/镇/街道')]"));
                sleep(1000);
                tryClick("//li[contains(normalize-space(.),'" + c[3] + "')]");
                sleep(800);
            }

            clickQuery();
            sleep(3000);

            check(c[4], "服务范围查询：" + c[2] + " / 街道=" + (c[3] == null ? "为空" : c[3]),
                    driver.getPageSource().contains(c[2]));

            scrollTo(visible(By.cssSelector("div.prices, div.result, div[class*='result']")));
            takeScreenshot(c[4]);
        }
    }

    /* =====================================================================================
     *  六、地图缩放条拖动（需求7 专用）
     *  要点：必须用 Actions 的 clickAndHold + moveByOffset + release 模拟“按住拖动”，
     *        不能点【+】【-】按钮。
     * ===================================================================================== */

    private void dragZoomToMax() {
        WebElement slider = null;
        List<By> candidates = Arrays.asList(
                By.cssSelector("div[class*='zoom'] div[class*='slider']"),
                By.cssSelector("div[class*='zoom'] div[class*='drag']"),
                By.cssSelector("div[class*='control'] div[class*='slider']"),
                By.xpath("//div[contains(@class,'map')]//div[@draggable='true']"));
        for (By by : candidates) {
            try {
                List<WebElement> list = driver.findElements(by);
                for (WebElement e : list) {
                    if (e.isDisplayed()) {
                        slider = e;
                        break;
                    }
                }
            } catch (Exception ignore) {
            }
            if (slider != null) {
                break;
            }
        }
        if (slider == null) {
            System.out.println("[WARN] 未找到地图缩放条，请用 F12 检查该元素后替换 dragZoomToMax() 中的定位器");
            return;
        }

        Actions actions = new Actions(driver);
        actions.clickAndHold(slider).build().perform();
        sleep(300);
        // 向上分多次移动，直到拖到最顶端
        for (int i = 0; i < 12; i++) {
            actions.moveByOffset(0, -25).build().perform();
            sleep(150);
        }
        actions.release().build().perform();
        sleep(1000);
    }

    // test-code-end
   
    @AfterEach
    public void teardown() {
        this.driver.quit();
    }

    private void takeScreenshot(String fileName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HHmmssddSSS");
        String timestamp = dateFormat.format(new Date());
        String timestampedFileName = timestamp + "_" + fileName;
        File screenshotsDir = new File("screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }
        String screenshotFilePath = screenshotsDir.getAbsolutePath() + File.separator + timestampedFileName;
        File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        try {
            FileUtils.copyFile(screenshotFile, new File(screenshotFilePath));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
